package com.example.android_screen_relay

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress
import android.util.Log
import kotlinx.coroutines.launch

class RelayServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {

    private var currentPasskey: String? = null
    // Store authenticated sessions (could use a list or set)
    private val authenticatedSessions = java.util.Collections.synchronizedSet(HashSet<WebSocket>())
    // Allow single controller for better security? For now allow multiple if they know the code.

    fun updatePasskey(passkey: String?) {
        this.currentPasskey = passkey
        if (passkey == null) {
            // If passkey is cleared/reset, disconnect everyone or just invalidate?
            // Disconnecting is safer
            authenticatedSessions.clear()
            connections.forEach { it.close(1000, "Server passkey reset") }
        }
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        Log.d("RelayServer", "New connection from " + conn.remoteSocketAddress)
        // Enforce timeout for auth?
        // Basic timer to kick if not authed in 5 seconds
        kotlinx.coroutines.GlobalScope.launch {
            kotlinx.coroutines.delay(5000)
            if (conn.isOpen && !authenticatedSessions.contains(conn)) {
                conn.close(1008, "Authentication timeout")
            }
        }
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        Log.d("RelayServer", "Closed connection to " + conn.remoteSocketAddress)
        authenticatedSessions.remove(conn)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        // Log.d("RelayServer", "Message from client: $message")
        try {
            val json = org.json.JSONObject(message)
            val type = json.optString("type")
            
            // SECURITY CHECK: Authentication
            if (type == "auth") {
                val attemptKey = json.optString("key")
                if (currentPasskey != null && attemptKey == currentPasskey) {
                    authenticatedSessions.add(conn)
                    val response = org.json.JSONObject()
                    response.put("type", "auth_response")
                    response.put("status", "ok")
                    conn.send(response.toString())
                    Log.d("RelayServer", "Client authenticated: " + conn.remoteSocketAddress)
                } else {
                    val response = org.json.JSONObject()
                    response.put("type", "auth_response")
                    response.put("status", "failed")
                    conn.send(response.toString())
                    conn.close(1008, "Invalid Passkey")
                    Log.w("RelayServer", "Auth failed for: " + conn.remoteSocketAddress)
                }
                return
            }

            // Reject all other messages if not authenticated
            if (!authenticatedSessions.contains(conn)) {
                // Ignore or close?
                // conn.close(1008, "Not Authenticated") // Aggressive
                return 
            }
            
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
        // Only broadcast to authenticated clients
        val frameMsg = "{\"type\": \"frame\", \"data\": \"$base64Image\"}"
        broadcastToAuthenticated(frameMsg)
    }

    fun broadcastToAuthenticated(message: String) {
        synchronized(authenticatedSessions) {
            for (client in authenticatedSessions) {
                if (client.isOpen) {
                    client.send(message)
                }
            }
        }
    }
}
