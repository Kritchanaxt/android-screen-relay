package com.example.android_screen_relay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class OverlayManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    fun showOverlay() {
        if (overlayView != null) return

        // User requested NOT to show the overlay on screen ("displaying on the screen")
        // but rely on notification ("show that it is doing on air").
        // Since the Service has a notification, we can disable the floating window here.
        // If we strictly follow "show that it is doing on air" but NOT on screen, 
        // effectively we don't add the view.
        
        // However, if the intent was just to not block the UI, maybe we could make it tiny.
        // But "I don't want it to show on the screen" is strong.
        // We will return early. The Notification handles the "On Air" status.
        return

        /* Legacy Overlay
        // Creating a pill-shaped indicator
        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 50f
            setColor(Color.parseColor("#CCFF3B30")) // Semi-transparent Red
            setStroke(2, Color.WHITE)
        }
        ...
        */
    }

    fun removeOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }
}
