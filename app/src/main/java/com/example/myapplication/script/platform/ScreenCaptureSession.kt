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

    suspend fun captureBitmap(): Bitmap? = mutex.withLock {
        Log.d(TAG, "event=screen_capture_request valid=$valid closed=$closed width=$width height=$height")
        if (!valid || closed) return@withLock null
        withContext(Dispatchers.IO) {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                val localReader = reader ?: run { continuation.resumeSafely(null); return@suspendCancellableCoroutine }
                var finished = false
                fun finish(bitmap: Bitmap?) {
                    if (finished) return
                    finished = true
                    Log.d(TAG, "event=screen_capture_result success=${bitmap != null} width=${bitmap?.width ?: 0} height=${bitmap?.height ?: 0}")
                    localReader.setOnImageAvailableListener(null, null)
                    if (continuation.isActive) continuation.resumeSafely(bitmap)
                }
                fun captureAvailableImage(imageReader: ImageReader): Boolean {
                    val image = runCatching { imageReader.acquireLatestImage() }
                        .onFailure { error -> Log.e(TAG, "Failed to acquire screen capture image", error) }
                        .getOrNull()
                        ?: return false
                    try {
                        val plane = image.planes.firstOrNull()
                        val buffer = plane?.buffer
                        if (!valid || plane == null || buffer == null) {
                            Log.w(TAG, "Screen capture image has no readable pixel buffer")
                            finish(null)
                        } else {
                            finish(imageToBitmap(buffer, plane.pixelStride, plane.rowStride, image.width, image.height))
                        }
                    } catch (error: RuntimeException) {
                        Log.e(TAG, "Failed to convert screen capture image to bitmap", error)
                        finish(null)
                    } finally {
                        image.close()
                    }
                    return true
                }
                handler.post {
                    if (finished || captureAvailableImage(localReader)) return@post
                    localReader.setOnImageAvailableListener({ imageReader ->
                        captureAvailableImage(imageReader)
                    }, handler)
                }
                handler.postDelayed({
                    if (!finished) {
                        Log.w(TAG, "Screen capture timed out after $CAPTURE_TIMEOUT_MILLIS ms")
                        finish(null)
                    }
                }, CAPTURE_TIMEOUT_MILLIS)
                continuation.invokeOnCancellation { handler.post { localReader.setOnImageAvailableListener(null, null) } }
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        valid = false
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
    if (isActive) resume(value) {}
}
