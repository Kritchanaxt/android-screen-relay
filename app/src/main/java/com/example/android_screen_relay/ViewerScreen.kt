package com.example.android_screen_relay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import org.json.JSONObject

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.geometry.Offset

@Composable
fun ViewerScreen(hostIp: String, passkey: String) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("Connecting to $hostIp...") }
    
    // WebSocket Manager Reference to send commands
    var wsManagerRef by remember { mutableStateOf<WebSocketManager?>(null) }
    
    // Layout info for touch scaling
    var imageSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    DisposableEffect(hostIp) {
        val wsManager = WebSocketManager(
            url = "ws://$hostIp:8887",
            onMessageReceived = { message ->
                try {
                    // Parse JSON: {"type": "frame", "data": "BASE64..."}
                    val json = JSONObject(message)
                    val type = json.optString("type")
                    
                    if (type == "auth_response") {
                         val authStatus = json.optString("status")
                         if (authStatus == "ok") {
                             status = "Authenticated! Waiting for stream..."
                         } else {
                             status = "Authentication Failed!"
                         }
                    } else if (type == "frame") {
                        val base64String = json.getString("data")
                        val decodedBytes = Base64.decode(base64String, Base64.NO_WRAP)
                        val decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        
                        // Update state
                        bitmap = decodedBitmap
                        status = "Connected"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            onConnectionOpened = {
                status = "Connected! Authenticating..."
                // Send Auth Token immediately
                val authJson = JSONObject()
                authJson.put("type", "auth")
                authJson.put("key", passkey)
                // We need to send this. The WebSocketManager needs a reference to 'this' or we use a captured variable?
                // The onConnectionOpened is a callback. 'wsManager' variable is initialized *after* this lambda is created? 
                // No, variable initialization in Kotlin logic...
                // Actually 'wsManager' is not yet assigned to wsManagerRef.
                // But inside 'DisposableEffect', we can rely on 'wsManager' variable being available if we reference it carefully?
                // No, circular dependency in definition.
                // We can use a trick or valid scope.
                // Let's call send() on the instance *after* initialization block, but connect() is async?
                // Wait, WebSocketManager.connect() starts thread.
                // The 'onConnectionOpened' is called from that thread.
                // We can't access 'wsManager' inside its own constructor params easily.
                
                // Solution: We need a way to send. 
                // Let's modify WebSocketManager slightly or use a hack.
                // Actually, wsManagerRef is updated *after* wsManager is created.
                // If onConnectionOpened happens *very fast*, wsManagerRef might still be null?
                // Unlikely in UI thread composition but possible in threading.
                
                // Better: Just use a one-shot send inside the callback if we can get the instance.
                // Or just:
            },
            onConnectionFailed = { error ->
                status = "Socket Error: $error"
            }
        )
        // Assign ref first? No.
        wsManagerRef = wsManager
        
        // We need to inject the send action into onOpen.
        // But WebSocketManager is our wrapper.
        // Let's modify WebSocketManager to accept an onOpenAction separate from constructor?
        // Or we can just call sendAuth immediately after connect? No, must wait for open.
        
        // The simplest way without changing WebSocketManager too much:
        // WebSocketManager stores the socket.
        // When onConnectionOpened fires, we can assume `wsManager` is valid reference if we use `wsManagerRef`?
        // No, `wsManagerRef` is state, updated asynchronously in Compose terms (maybe).
        
        // Let's just create a thread-safe holder.
        
        wsManager.connect()
        
        // HACK: Send auth in a delayed loop or modifying wrapper to auto-send.
        // But the CLEAN way is to modify WebSocketManager to allow sending on Open.
        // Wait, onConnectionOpened IS the hook.
        // But inside it, we don't have `wsManager` reference yet because it's being constructed.
        
        wsManager.connect()
        wsManagerRef = wsManager
        
        onDispose {
            wsManager.disconnect()
        }
    }
    
    // Side effect to send auth when connected
    LaunchedEffect(wsManagerRef, status) {
        if (status == "Connected! Authenticating..." && wsManagerRef != null) {
            val authJson = JSONObject()
            authJson.put("type", "auth")
            authJson.put("key", passkey)
            wsManagerRef?.send(authJson.toString())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (bitmap != null) {
            val bmp = bitmap!!
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Screen Stream",
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        imageSize = coordinates.size.toSize()
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            // Calculate scaling relative to the actual image size vs displayed size
                            // This depends on ContentScale.Fit logic. For simplicity, we assume Fill or basic math.
                            
                            // Simple relative Mapping (Assuming full screen coverage or adjusting for aspect ratio later)
                            // Ideally, we need the source resolution from the host safely.
                            // For now, we send relative coordinates (0..1) or raw display coordinates if Host can handle it.
                            // Best practice: Host expects raw pixels. Client sends normalized (0.0 - 1.0) and Host scales it up.
                            
                            val normX = offset.x / imageSize.width
                            val normY = offset.y / imageSize.height
                            
                            // Send to Host. Host must interpret relative vs absolute. 
                            // Our Host expects Absolute pixels currently.
                            // To fix this properly, let's assume a standard 1080x1920 host for a moment OR
                            // update Host to accept normalized coordinates (Better).
                            
                            // Temporary: Send generic normalized, Host side needs to scale.
                            // But wait, our `RelayServer` expects just X, Y float.
                            // Let's modify logic to send "Percent" and let Host multiply by screen width/height.
                            
                            val cmd = JSONObject()
                            cmd.put("type", "click")
                            cmd.put("x_percent", normX)
                            cmd.put("y_percent", normY)
                            
                            wsManagerRef?.send(cmd.toString())
                        }
                    },
                contentScale = ContentScale.FillBounds // Changed to FillBounds to make coordinate mapping easier for this MVP
            )
        } else {
            Text(text = status, modifier = Modifier.align(Alignment.Center))
        }
    }
}
