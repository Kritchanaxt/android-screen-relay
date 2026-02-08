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
        Log.d("RelayServer", "Message from client: $message")
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
