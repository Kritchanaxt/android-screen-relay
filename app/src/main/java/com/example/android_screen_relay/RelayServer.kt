package com.example.android_screen_relay

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import android.util.Log

class RelayServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        Log.d("RelayServer", "New connection from " + conn.remoteSocketAddress)
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        Log.d("RelayServer", "Closed connection to " + conn.remoteSocketAddress)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        // Log.d("RelayServer", "Message from client: $message")
        try {
            val json = org.json.JSONObject(message)
            val type = json.optString("type")
            
            when (type) {
                "click" -> {
                    // Check for normalized coordinates first
                    if (json.has("x_percent") && json.has("y_percent")) {
                        val displayMetrics = android.content.res.Resources.getSystem().displayMetrics
                        val screenWidth = displayMetrics.widthPixels
                        val screenHeight = displayMetrics.heightPixels
                        
                        val xPct = json.optDouble("x_percent").toFloat()
                        val yPct = json.optDouble("y_percent").toFloat()
                        
                        val realX = xPct * screenWidth
                        val realY = yPct * screenHeight
                         RelayAccessibilityService.instance?.click(realX, realY)
                         
                    } else {
                        // Old absolute way
                        val x = json.optDouble("x").toFloat()
                        val y = json.optDouble("y").toFloat()
                        RelayAccessibilityService.instance?.click(x, y)
                    }
                }
                "swipe" -> {
                    val x1 = json.optDouble("x1").toFloat()
                    val y1 = json.optDouble("y1").toFloat()
                    val x2 = json.optDouble("x2").toFloat()
                    val y2 = json.optDouble("y2").toFloat()
                    val duration = json.optLong("duration", 300)
                    RelayAccessibilityService.instance?.swipe(x1, y1, x2, y2, duration)
                }
                "back" -> RelayAccessibilityService.instance?.performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                "home" -> RelayAccessibilityService.instance?.performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                "recent" -> RelayAccessibilityService.instance?.performGlobal(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
                "stop_server" -> {
                    // Logic to stop server from client? might be dangerous, maybe just stop stream
                }
            }
        } catch (e: Exception) {
            // Not a JSON or error parsing
            // e.printStackTrace()
        }
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e("RelayServer", "Error: ${ex.message}")
        ex.printStackTrace()
    }

    override fun onStart() {
        Log.d("RelayServer", "Server started on port $port")
    }
    
    fun broadcastImage(base64Image: String) {
        broadcast(base64Image)
    }
}
