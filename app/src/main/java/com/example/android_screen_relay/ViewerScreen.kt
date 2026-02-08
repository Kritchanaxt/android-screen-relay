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

@Composable
fun ViewerScreen(hostIp: String) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("Connecting to $hostIp...") }

    DisposableEffect(hostIp) {
        val wsManager = WebSocketManager("ws://$hostIp:8887") { message ->
            try {
                // Parse JSON: {"type": "frame", "data": "BASE64..."}
                val json = JSONObject(message)
                if (json.optString("type") == "frame") {
                    val base64String = json.getString("data")
                    val decodedBytes = Base64.decode(base64String, Base64.NO_WRAP)
                    val decodedBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    
                    // Update state (safe to do from any thread in Compose usually, but ensures recomposition)
                    bitmap = decodedBitmap
                    status = "Connected"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // status = "Error: ${e.message}" // Avoid rapid UI updates on error loops
            }
        }
        wsManager.connect()

        onDispose {
            wsManager.disconnect()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Screen Stream",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(text = status, modifier = Modifier.align(Alignment.Center))
        }
    }
}
