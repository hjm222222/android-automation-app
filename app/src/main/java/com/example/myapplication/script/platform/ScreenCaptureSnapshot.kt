package com.example.myapplication.script.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import kotlin.coroutines.resume

/** 使用已授权的 MediaProjection 读取一次屏幕快照。 */
class ScreenCaptureSnapshot(
    context: Context,
    private val resultCode: Int,
    private val resultData: Intent
) {
    private val applicationContext = context.applicationContext
    private val projectionManager = applicationContext.getSystemService(
        Context.MEDIA_PROJECTION_SERVICE
    ) as MediaProjectionManager

    suspend fun capture(): Bitmap? = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ||
            resultCode != android.app.Activity.RESULT_OK
        ) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val metrics = applicationContext.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val thread = HandlerThread("screen-capture").also { it.start() }
        val handler = Handler(thread.looper)
        var projection: MediaProjection? = null
        var display: android.hardware.display.VirtualDisplay? = null
        var released = false

        fun release() {
            if (released) return
            released = true
            reader.setOnImageAvailableListener(null, null)
            display?.release()
            projection?.stop()
            reader.close()
            thread.quitSafely()
        }

        fun finish(bitmap: Bitmap?) {
            release()
            if (continuation.isActive) continuation.resume(bitmap)
        }

        try {
            projection = projectionManager.getMediaProjection(resultCode, resultData)
            if (projection == null) {
                finish(null)
                return@suspendCancellableCoroutine
            }
            reader.setOnImageAvailableListener({ imageReader ->
                val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val plane = image.planes.firstOrNull()
                    val buffer = plane?.buffer
                    if (plane == null || buffer == null) {
                        finish(null)
                    } else {
                        finish(imageToBitmap(buffer, plane.pixelStride, plane.rowStride, image.width, image.height))
                    }
                } catch (_: RuntimeException) {
                    finish(null)
                } finally {
                    image.close()
                }
            }, handler)
            display = projection?.createVirtualDisplay(
                "single-frame-capture",
                width,
                height,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )
            if (display == null) finish(null)
        } catch (_: RuntimeException) {
            finish(null)
        }

        continuation.invokeOnCancellation { handler.post { release() } }
        handler.postDelayed({ if (continuation.isActive) finish(null) }, CAPTURE_TIMEOUT_MILLIS)
    }

    private fun imageToBitmap(
        buffer: ByteBuffer,
        pixelStride: Int,
        rowStride: Int,
        width: Int,
        height: Int
    ): Bitmap {
        val rowPadding = (rowStride - pixelStride * width).coerceAtLeast(0)
        val paddedWidth = width + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        padded.copyPixelsFromBuffer(buffer)
        return if (paddedWidth == width) padded else Bitmap.createBitmap(padded, 0, 0, width, height)
            .also { padded.recycle() }
    }

    private companion object {
        const val CAPTURE_TIMEOUT_MILLIS = 2000L
    }
}
