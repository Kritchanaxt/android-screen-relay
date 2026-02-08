package com.example.android_screen_relay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.android_screen_relay.ui.theme.AndroidscreenrelayTheme
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            checkPermissionsAndStart()
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startRelayService(result.resultCode, result.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidscreenrelayTheme {
                AppNavigation(
                    onStartService = { checkPermissionsAndStart() }
                )
            }
        }
    }

    private fun checkPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
            return
        }

        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun startRelayService(resultCode: Int, data: Intent) {
        val intent = Intent(this, RelayService::class.java).apply {
            putExtra("RESULT_CODE", resultCode)
            putExtra("DATA_INTENT", data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

@Composable
fun AppNavigation(onStartService: () -> Unit) {
    var currentTab by remember { mutableStateOf(0) } // 0: Home, 1: Connect (Client), 2: Me
    var isViewerMode by remember { mutableStateOf(false) }
    var targetIp by remember { mutableStateOf("") }

    if (isViewerMode) {
        ViewerScreen(hostIp = targetIp)
        // Add a floating back button to exit viewer
        Box(modifier = Modifier.fillMaxSize()) {
            FloatingActionButton(
                onClick = { isViewerMode = false },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(32.dp)
                    .statusBarsPadding(),
                containerColor = Color.Red,
                contentColor = Color.White
            ) {
                Icon(Icons.Filled.Close, "Exit")
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFFE3F2FD))
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.CastConnected, contentDescription = "Connect") },
                        label = { Text("Connect") },
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFFE3F2FD))
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Filled.Person, contentDescription = "Me") },
                        label = { Text("Me") },
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color(0xFFE3F2FD))
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (currentTab) {
                    0 -> HomeScreen(onStartService)
                    1 -> ConnectScreen(
                        onConnect = { ip ->
                            targetIp = ip
                            isViewerMode = true
                        }
                    )
                    2 -> MeScreen()
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onStartService: () -> Unit) {
    val localIp = remember { getLocalIpAddress() } ?: "Unknown"
    val deviceName = remember { "${Build.MANUFACTURER} ${Build.MODEL}" }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Android Screen Relay",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
            )
            Icon(
                imageVector = Icons.Filled.QrCodeScanner,
                contentDescription = "Scan",
                tint = Color(0xFF007AFF)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Device Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Smartphone, null, tint = Color.Gray)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Device", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text(deviceName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Edit, null, tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp))
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("My IP Address", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = localIp,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF007AFF)
                        )
                    )
                    Icon(
                        Icons.Filled.Lock, 
                        null, 
                        tint = Color(0xFF007AFF).copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Connection Status / My Devices
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "My Services", 
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Icon(Icons.Filled.Refresh, null, tint = Color(0xFF007AFF))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Start Broadcasting Action
        Card(
            onClick = onStartService,
            modifier = Modifier.fillMaxWidth().height(80.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF007AFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Cast, null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        "Start Broadcasting", 
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0D47A1)
                    )
                    Text(
                        "Share this screen to others", 
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1976D2)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Filled.PlayArrow, null, tint = Color(0xFF0D47A1))
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            "No target device? Check user guide",
            modifier = Modifier.padding(bottom = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
    }
}

@Composable
fun ConnectScreen(onConnect: (String) -> Unit) {
    var ipInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA)),
    ) {
        // Banner Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF4A90E2), Color(0xFF0044AA))
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Text(
                    "Connection failed?", 
                    style = MaterialTheme.typography.headlineSmall, 
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Try ensuring both devices\nare on the same Wi-Fi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            Icon(
                Icons.Filled.WifiOff, 
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(80.dp)
                    .padding(end = 16.dp),
                tint = Color.White.copy(alpha = 0.5f)
            )
        }

        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "Connect to Remote Device",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = ipInput,
                onValueChange = { ipInput = it },
                label = { Text("Enter Host IP Address") },
                placeholder = { Text("e.g. 192.168.1.45") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Computer, null) }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { if(ipInput.isNotEmpty()) onConnect(ipInput) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                Text("Connect", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Text("Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ToolCard("Whiteboard", Icons.Filled.Draw, Color(0xFFFF9500))
                ToolCard("File Transfer", Icons.Filled.Folder, Color(0xFF34C759))
            }
        }
    }
}

@Composable
fun RowScope.ToolCard(name: String, icon: ImageVector, color: Color) {
    Card(
        modifier = Modifier.weight(1f).height(100.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(name, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun MeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(60.dp),
                    shape = RoundedCornerShape(30.dp),
                    color = Color(0xFFE3F2FD)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, null, tint = Color(0xFF007AFF), modifier = Modifier.size(30.dp))
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Guest User", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Not logged in", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                ) {
                    Text("Log In")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Menu Items
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 8.dp)
        ) {
            MenuItem(Icons.Filled.Settings, "Settings")
            Divider(color = Color(0xFFF0F0F0))
            MenuItem(Icons.Filled.Help, "Help Center")
            Divider(color = Color(0xFFF0F0F0))
            MenuItem(Icons.Filled.Feedback, "Feedback")
            Divider(color = Color(0xFFF0F0F0))
            MenuItem(Icons.Filled.Info, "About Us")
        }
    }
}

@Composable
fun MenuItem(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.Gray)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = Color.LightGray)
    }
}

fun getLocalIpAddress(): String? {
    try {
        val en = NetworkInterface.getNetworkInterfaces()
        while (en.hasMoreElements()) {
            val intf = en.nextElement()
            val enumIpAddr = intf.inetAddresses
            while (enumIpAddr.hasMoreElements()) {
                val inetAddress = enumIpAddr.nextElement()
                if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                    return inetAddress.hostAddress
                }
            }
        }
    } catch (ex: Exception) {
        ex.printStackTrace()
    }
    return null
}
