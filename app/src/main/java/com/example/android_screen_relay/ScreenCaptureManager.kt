package com.example.android_screen_relay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class ScreenCaptureManager(private val context: Context) {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    // deleted webSocketManager
    private var backgroundThread: android.os.HandlerThread? = null
    private var backgroundHandler: android.os.Handler? = null
    
    // Callback to send data
    private var onFrameCaptured: ((String) -> Unit)? = null

    // Screen metrics
    private val width = Resources.getSystem().displayMetrics.widthPixels / 2 // Downscale for performance
    private val height = Resources.getSystem().displayMetrics.heightPixels / 2
    private val density = Resources.getSystem().displayMetrics.densityDpi

    @SuppressLint("WrongConstant")
    fun startCapture(resultCode: Int, data: Intent, onFrameCaptured: (String) -> Unit) {
        this.onFrameCaptured = onFrameCaptured
        
        // Start background thread
        backgroundThread = android.os.HandlerThread("ScreenCaptureThread")
        backgroundThread?.start()
        backgroundHandler = android.os.Handler(backgroundThread!!.looper)

        val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)

        // Register callback to handle stop
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                super.onStop()
                stopCapture()
            }
        }, backgroundHandler)

        // Setup ImageReader
        // PixelFormat.RGBA_8888 is standard
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, backgroundHandler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width

                // Create bitmap
                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                
                // Crop the bitmap (remove padding) if necessary
                val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                
                sendBitmap(croppedBitmap)
                
                bitmap.recycle()
                // croppedBitmap.recycle() // Recycled inside sendBitmap logic eventually or rely on GC
            } catch (e: Exception) {
                Log.e("ScreenCapture", "Error processing image: ${e.message}")
            } finally {
                image.close()
            }
        }, backgroundHandler) 
    }

    private fun sendBitmap(bitmap: Bitmap) {
        // Compress to JPEG and send
        CoroutineScope(Dispatchers.IO).launch {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
            val imageBytes = outputStream.toByteArray()
            val base64String = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            
            // Simple JSON protocol
            val jsonMessage = "{\"type\": \"frame\", \"data\": \"$base64String\"}"
            onFrameCaptured?.invoke(jsonMessage)
        }
    }

    fun stopCapture() {
        mediaProjection?.stop()
        mediaProjection = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}
