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
        LogRepository.addLog("Passkey updated: $passkey", LogRepository.LogType.INFO)
        if (passkey == null) {
            // If passkey is cleared/reset, disconnect everyone or just invalidate?
            // Disconnecting is safer
            authenticatedSessions.clear()
            connections.forEach { it.close(1000, "Server passkey reset") }
        }
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        val remoteAddr = conn.remoteSocketAddress?.address?.hostAddress ?: "Unknown"
        val descriptor = handshake.resourceDescriptor
        
        val msg = "NEW CONNECTION: IP=$remoteAddr | Resource=$descriptor"
        Log.d("RelayServer", msg)
        LogRepository.addLog(msg, LogRepository.LogType.INFO)
        
        // Enforce timeout for auth
        kotlinx.coroutines.GlobalScope.launch {
            kotlinx.coroutines.delay(5000)
            if (conn.isOpen && !authenticatedSessions.contains(conn)) {
                val timeoutMsg = "Auth Timeout: Disconnecting $remoteAddr"
                LogRepository.addLog(timeoutMsg, LogRepository.LogType.ERROR)
                conn.close(1008, "Authentication timeout")
            }
        }
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        val remoteAddr = conn.remoteSocketAddress?.address?.hostAddress ?: "Unknown"
        val who = if (remote) "Remote" else "Local"
        val msg = "DISCONNECTED: $remoteAddr | Code=$code | Reason=$reason | By=$who"
        
        Log.d("RelayServer", msg)
        LogRepository.addLog(msg, LogRepository.LogType.INFO)
        authenticatedSessions.remove(conn)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        try {
            val json = org.json.JSONObject(message)
            val type = json.optString("type")
            
            // SECURITY CHECK: Authentication
            if (type == "auth") {
                val attemptKey = json.optString("key")
                val remoteAddr = conn.remoteSocketAddress?.address?.hostAddress ?: "?"
                
                LogRepository.addLog("AUTH REQUEST: From $remoteAddr with key '******'", LogRepository.LogType.INCOMING)
                
                if (currentPasskey != null && attemptKey == currentPasskey) {
                    authenticatedSessions.add(conn)
                    val response = org.json.JSONObject()
                    response.put("type", "auth_response")
                    response.put("status", "ok")
                    conn.send(response.toString())
                    
                    val msg = "AUTH SUCCESS: Client $remoteAddr is now authenticated."
                    Log.d("RelayServer", msg)
                    LogRepository.addLog(msg, LogRepository.LogType.INFO)
                } else {
                    val response = org.json.JSONObject()
                    response.put("type", "auth_response")
                    response.put("status", "failed")
                    conn.send(response.toString())
                    conn.close(1008, "Invalid Passkey") // Specific close code
                    
                    val failMsg = "AUTH FAILED: Invalid passkey from $remoteAddr"
                    Log.w("RelayServer", failMsg)
                    LogRepository.addLog(failMsg, LogRepository.LogType.ERROR)
                }
                return
            }

            // Reject all other messages if not authenticated
            if (!authenticatedSessions.contains(conn)) {
                return 
            }
            
            // Log command details
            val detail = when (type) {
                "click" -> {
                    if (json.has("x_percent")) "Click(${json.optDouble("x_percent")}, ${json.optDouble("y_percent")})"
                    else "Click(${json.optDouble("x")}, ${json.optDouble("y")})"
                }
                "swipe" -> "Swipe(${json.optInt("duration")}ms)"
                "back" -> "Action: BACK"
                "home" -> "Action: HOME"
                "recent" -> "Action: RECENT"
                else -> "Data: $message"
            }
            LogRepository.addLog("CMD RX: $detail", LogRepository.LogType.INCOMING)
            
            // ... (Rest of logic remains same, just logging above)
            
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
        LogRepository.addLog("Error: ${ex.message}", LogRepository.LogType.ERROR)
        ex.printStackTrace()
    }

    override fun onStart() {
        val msg = "Server started on port $port"
        Log.d("RelayServer", msg)
        LogRepository.addLog(msg, LogRepository.LogType.INFO)
    }
    
    fun broadcastImage(imageBytes: ByteArray) {
        // Broadcast raw bytes to authenticated clients
        broadcastToAuthenticated(imageBytes)
        // LogRepository.addLog("TX: Image Frame (${imageBytes.size} bytes)", LogRepository.LogType.OUTGOING) // Too spammy? Maybe log every 30 frames?
    }
    
    // Kept for legacy textual interface if needed (but we are moving to binary)
    fun broadcastToAuthenticated(message: String) {
        synchronized(authenticatedSessions) {
            for (client in authenticatedSessions) {
                if (client.isOpen) {
                    client.send(message)
                }
            }
        }
        // Don't log heartbeat content fully if it is heartbeat
        if (message.contains("heartbeat")) {
             // LogRepository.addLog("TX: Heartbeat", LogRepository.LogType.OUTGOING)
        } else {
             LogRepository.addLog("TX: $message", LogRepository.LogType.OUTGOING)
        }
    }

    fun broadcastToAuthenticated(data: ByteArray) {
        synchronized(authenticatedSessions) {
            for (client in authenticatedSessions) {
                if (client.isOpen) {
                    client.send(data)
                }
            }
        }
    }
}
