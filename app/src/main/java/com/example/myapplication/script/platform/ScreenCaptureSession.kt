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
            override fun onStop() { valid = false }
        }, handler)
        if (valid) {
            display = runCatching {
                projection?.createVirtualDisplay(
                    "screen-capture-session", width, height,
                    appContext.resources.displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader?.surface, null, handler
                )
            }.getOrNull()
            if (display == null) valid = false
        }
    }

    suspend fun captureBitmap(): Bitmap? = mutex.withLock {
        if (!valid || closed) return@withLock null
        withContext(Dispatchers.IO) {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                val localReader = reader ?: run { continuation.resumeSafely(null); return@suspendCancellableCoroutine }
                var finished = false
                fun finish(bitmap: Bitmap?) {
                    if (finished) return
                    finished = true
                    localReader.setOnImageAvailableListener(null, null)
                    if (continuation.isActive) continuation.resumeSafely(bitmap)
                }
                localReader.setOnImageAvailableListener({ imageReader ->
                    val image = runCatching { imageReader.acquireLatestImage() }.getOrNull()
                    if (image == null) return@setOnImageAvailableListener
                    try {
                        val plane = image.planes.firstOrNull()
                        val buffer = plane?.buffer
                        if (!valid || plane == null || buffer == null) finish(null)
                        else finish(imageToBitmap(buffer, plane.pixelStride, plane.rowStride, image.width, image.height))
                    } catch (_: RuntimeException) { finish(null) } finally { image.close() }
                }, handler)
                handler.postDelayed({ finish(null) }, 2000L)
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
