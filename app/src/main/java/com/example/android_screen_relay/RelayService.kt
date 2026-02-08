package com.example.android_screen_relay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo

class RelayService : Service() {

    private var relayServer: RelayServer? = null
    // private lateinit var webSocketManager: WebSocketManager // Removed
    private lateinit var overlayManager: OverlayManager
    private lateinit var screenCaptureManager: ScreenCaptureManager
    private val CHANNEL_ID = "RelayServiceChannel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // webSocketManager = WebSocketManager("wss://echo.websocket.org") // Removed
        
        // Start Local Server
        try {
            relayServer = RelayServer(8887)
            relayServer?.start()
            android.util.Log.d("RelayService", "RelayServer started on port 8887")
        } catch (e: Exception) {
            e.printStackTrace()
        }

        overlayManager = OverlayManager(this)
        screenCaptureManager = ScreenCaptureManager(this)
    }

    companion object {
        const val ACTION_STOP = "com.example.android_screen_relay.STOP"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = createNotification()
        
        // Connect WebSocket - Removed
        // webSocketManager.connect()

        // Show Overlay
        overlayManager.showOverlay()

        // Start Screen Capture if data is present
        // IMPORTANT: Must start capture (obtain MediaProjection) BEFORE calling startForeground with type mediaProjection on Android 14+
        if (intent != null) {
            val resultCode = intent.getIntExtra("RESULT_CODE", 0)
            val dataIntent = intent.getParcelableExtra<Intent>("DATA_INTENT")
            
            if (resultCode != 0 && dataIntent != null) {
                try {
                    screenCaptureManager.startCapture(resultCode, dataIntent) { message ->
                        relayServer?.broadcast(message)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
             startForeground(1, notification)
        }

        // Using START_REDELIVER_INTENT so if the service is killed, it restarts with the same intent (including permission token)
        // Note: The permission token might expire or be invalidated, but it's better than null.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            relayServer?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        overlayManager.removeOverlay()
        screenCaptureManager.stopCapture()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Relay Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, RelayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Relay Active")
            .setContentText("Listening for commands...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }
}
