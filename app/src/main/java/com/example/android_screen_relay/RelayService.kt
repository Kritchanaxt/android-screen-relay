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
import kotlinx.coroutines.*
import kotlin.random.Random

class RelayService : Service() {

    private var relayServer: RelayServer? = null
    // private lateinit var webSocketManager: WebSocketManager // Removed
    private lateinit var overlayManager: OverlayManager
    private lateinit var screenCaptureManager: ScreenCaptureManager
    private val CHANNEL_ID = "RelayServiceChannel"
    
    private var discoveryJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // Generate Passkey
        val passkey = String.format("%06d", Random.nextInt(0, 999999))
        currentPasskey = passkey
        
        // Start UDP Discovery Listener
        discoveryJob = scope.launch {
            NetworkDiscovery.startHostListeners(this@RelayService, passkey, 8887) {
                isActive // check if scope is active
            }
        }
        
        // Start Local Server
        try {
            relayServer = RelayServer(8887)
            relayServer?.updatePasskey(passkey) // Set the passkey
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
        var currentPasskey: String? = null
            private set
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = createNotification()

        // IMPORTANT: Start Foreground Service BEFORE creating the virtual display (Android 14+ requirement)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
        
        // Show Overlay
        overlayManager.showOverlay()

        // Start Screen Capture if data is present
        if (intent != null) {
            val resultCode = intent.getIntExtra("RESULT_CODE", 0)
            val dataIntent = intent.getParcelableExtra<Intent>("DATA_INTENT")
            
            if (resultCode != 0 && dataIntent != null) {
                try {
                    val quality = intent.getIntExtra("QUALITY_MODE", 1) // Default to Medium (1)
                    android.util.Log.d("RelayService", "Starting Screen Capture with Quality: $quality")
                    screenCaptureManager.startCapture(resultCode, dataIntent, quality) { imageBytes ->
                        // Pass raw bytes to authenticated clients
                        relayServer?.broadcastToAuthenticated(imageBytes)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RelayService", "Error starting capture: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        // Using START_STICKY so if the service is killed, it restarts (without the intent data, so no screen capture, but server is alive)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            relayServer?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        discoveryJob?.cancel()
        scope.cancel()
        currentPasskey = null
        
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
