package com.longipinnatus.screentrans

import android.graphics.Bitmap
import android.media.ImageReader
import android.util.Log
import java.nio.ByteBuffer
import androidx.core.graphics.createBitmap

object ScreenCaptureHelper {
    private const val TAG = "ScreenCaptureHelper"

    fun capture(imageReader: ImageReader?): Bitmap? {
        if (imageReader == null) {
            Log.e(TAG, "capture: ImageReader is null")
            return null
        }

        val image = try {
            imageReader.acquireLatestImage()
        } catch (e: Exception) {
            Log.e(TAG, "capture: Failed to acquire latest image", e)
            null
        } ?: return null
        
        try {
            val width = image.width
            val height = image.height
            val planes = image.planes
            
            if (planes.isNullOrEmpty()) {
                Log.e(TAG, "capture: Image planes are null or empty")
                return null
            }

            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            Log.d(TAG, "capture: Processing image [${width}x${height}] pixelStride=$pixelStride, rowStride=$rowStride, rowPadding=$rowPadding")

            val bitmap = createBitmap(width + rowPadding / pixelStride, height)
            
            try {
                bitmap.copyPixelsFromBuffer(buffer)
            } catch (e: Exception) {
                Log.e(TAG, "capture: copyPixelsFromBuffer failed", e)
                bitmap.recycle()
                return null
            }
            
            // Crop to actual size if there was padding
            val result = if (rowPadding > 0) {
                val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                if (croppedBitmap != bitmap) {
                    bitmap.recycle()
                }
                croppedBitmap
            } else {
                bitmap
            }
            
            Log.d(TAG, "capture: Success, final bitmap size: ${result.width}x${result.height}")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "capture: Unexpected error during bitmap processing", e)
            return null
        } finally {
            image.close()
        }
    }
}
