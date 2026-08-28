package com.example.myapplication.script.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/** 一个授权周期内复用的屏幕捕获会话。 */
class ScreenCaptureSession(
    context: Context,
    resultCode: Int,
    resultData: Intent
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val thread = HandlerThread("screen-capture-session").also { it.start() }
    private val handler = Handler(thread.looper)
    private val projection: MediaProjection? = runCatching {
        if (resultCode == Activity.RESULT_OK) {
            (appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                .getMediaProjection(resultCode, Intent(resultData))
        } else null
    }.onFailure { error ->
        Log.e(TAG, "Failed to create MediaProjection", error)
    }.getOrNull()
    private val width = appContext.resources.displayMetrics.widthPixels.coerceAtLeast(1)
    private val height = appContext.resources.displayMetrics.heightPixels.coerceAtLeast(1)
    private val reader: ImageReader? = projection?.let { ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2) }
    private var display: VirtualDisplay? = null
    @Volatile private var valid = projection != null && reader != null
    @Volatile private var closed = false
    private var activeCaptureFinish: (() -> Unit)? = null

    val isValid: Boolean
        get() = valid && !closed

    init {
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                close()
            }
        }, handler)
        if (valid) {
            display = runCatching {
                projection?.createVirtualDisplay(
                    "screen-capture-session", width, height,
                    appContext.resources.displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader?.surface, null, handler
                )
            }.onFailure { error ->
                Log.e(TAG, "Failed to create screen capture virtual display", error)
            }.getOrNull()
            if (display == null) {
                Log.e(TAG, "Screen capture virtual display is unavailable")
                valid = false
            }
        }
    }

    suspend fun captureResult(): VisionResult<Bitmap> = mutex.withLock {
        Log.d(TAG, "event=screen_capture_request valid=$valid closed=$closed width=$width height=$height")
        if (!valid || closed) return@withLock VisionResult.PermissionDenied
        withContext(Dispatchers.IO) {
            kotlinx.coroutines.suspendCancellableCoroutine<VisionResult<Bitmap>> { continuation ->
                val localReader = reader ?: run {
                    val result: VisionResult<Bitmap> = VisionResult.PermissionDenied
                    continuation.resumeSafely(result)
                    return@suspendCancellableCoroutine
                }
                val finished = java.util.concurrent.atomic.AtomicBoolean(false)
                lateinit var finish: (VisionResult<Bitmap>) -> Unit
                lateinit var timeout: Runnable
                finish = fun(result: VisionResult<Bitmap>) {
                    if (!finished.compareAndSet(false, true)) return
                    synchronized(this@ScreenCaptureSession) {
                        if (activeCaptureFinish === finish) activeCaptureFinish = null
                    }
                    val bitmap = (result as? VisionResult.Success)?.value
                    Log.d(TAG, "event=screen_capture_result success=${bitmap != null} width=${bitmap?.width ?: 0} height=${bitmap?.height ?: 0}")
                    runCatching { localReader.setOnImageAvailableListener(null, null) }
                    runCatching { handler.removeCallbacks(timeout) }
                    continuation.resumeSafely(result)
                }
                fun captureAvailableImage(imageReader: ImageReader): Boolean {
                    val image = try {
                        imageReader.acquireLatestImage()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: RuntimeException) {
                        Log.e(TAG, "Failed to acquire screen capture image", error)
                        return false
                    } ?: return false
                    try {
                        val plane = image.planes.firstOrNull()
                        val buffer = plane?.buffer
                        if (!valid || plane == null || buffer == null) {
                            Log.w(TAG, "Screen capture image has no readable pixel buffer")
                            finish(VisionResult.Failed("屏幕截图没有可读取的像素数据"))
                        } else {
                            finish(VisionResult.Success(imageToBitmap(buffer, plane.pixelStride, plane.rowStride, image.width, image.height)))
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: RuntimeException) {
                        Log.e(TAG, "Failed to convert screen capture image to bitmap", error)
                        finish(VisionResult.Failed("屏幕截图转换失败", error))
                    } finally {
                        image.close()
                    }
                    return true
                }
                timeout = Runnable {
                    if (!finished.get()) {
                        Log.w(TAG, "Screen capture timed out after $CAPTURE_TIMEOUT_MILLIS ms")
                        finish(VisionResult.Timeout)
                    }
                }
                synchronized(this@ScreenCaptureSession) {
                    if (closed) {
                        finish(VisionResult.PermissionDenied)
                        return@suspendCancellableCoroutine
                    }
                    activeCaptureFinish = { finish(VisionResult.PermissionDenied) }
                }
                val capturePosted = handler.post {
                    if (finished.get() || captureAvailableImage(localReader)) return@post
                    runCatching {
                        localReader.setOnImageAvailableListener({ imageReader ->
                            captureAvailableImage(imageReader)
                        }, handler)
                    }.onFailure { error ->
                        Log.e(TAG, "Failed to register screen capture listener", error)
                        finish(VisionResult.Failed("屏幕截图监听器注册失败", error))
                    }
                }
                if (!capturePosted) {
                    finish(VisionResult.Failed("屏幕截图任务无法提交"))
                    return@suspendCancellableCoroutine
                }
                if (!handler.postDelayed(timeout, CAPTURE_TIMEOUT_MILLIS)) {
                    finish(VisionResult.Failed("屏幕截图超时任务无法提交"))
                    return@suspendCancellableCoroutine
                }
                continuation.invokeOnCancellation { handler.post { finish(VisionResult.PermissionDenied) } }
            }
        }
    }

    suspend fun captureBitmap(): Bitmap? = when (val result = captureResult()) {
        is VisionResult.Success -> result.value
        else -> null
    }

    override fun close() {
        val finishCapture = synchronized(this) {
            if (closed) return
            closed = true
            valid = false
            activeCaptureFinish.also { activeCaptureFinish = null }
        }
        runCatching { finishCapture?.invoke() }
        runCatching { reader?.setOnImageAvailableListener(null, null) }
        runCatching { display?.release() }
        runCatching { projection?.stop() }
        runCatching { reader?.close() }
        thread.quitSafely()
    }

    private companion object {
        const val TAG = "ScreenCaptureSession"
        const val CAPTURE_TIMEOUT_MILLIS = 2_000L
    }

    private fun imageToBitmap(buffer: ByteBuffer, pixelStride: Int, rowStride: Int, imageWidth: Int, imageHeight: Int): Bitmap {
        val rowPadding = (rowStride - pixelStride * imageWidth).coerceAtLeast(0)
        val paddedWidth = imageWidth + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, imageHeight, Bitmap.Config.ARGB_8888)
        buffer.rewind(); padded.copyPixelsFromBuffer(buffer)
        return if (paddedWidth == imageWidth) padded else Bitmap.createBitmap(padded, 0, 0, imageWidth, imageHeight).also { padded.recycle() }
    }
}

private fun <T> kotlinx.coroutines.CancellableContinuation<T>.resumeSafely(value: T) {
    if (isActive) resume(value) { _, _, _ -> }
}
