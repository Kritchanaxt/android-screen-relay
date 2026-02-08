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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
                    onStartService = { checkPermissionsAndStart() },
                    onStopService = { stopRelayService() }
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

    private fun stopRelayService() {
        val intent = Intent(this, RelayService::class.java).apply {
            action = RelayService.ACTION_STOP
        }
        startService(intent)
    }
}

@Composable
fun AppNavigation(
    onStartService: () -> Unit,
    onStopService: () -> Unit = {} // Add check later
) {
    var currentTab by remember { mutableStateOf(0) } // 0: Home, 1: Connect (Client), 2: Me
    var isViewerMode by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var targetIp by remember { mutableStateOf("") }
    var connectionPasskey by remember { mutableStateOf("") } // Store passkey for auth
    
    // Simple state to track only for UI toggling (Real app should observe service state)
    var isBroadcasting by remember { mutableStateOf(false) }

    if (isScanning) {
        val context = LocalContext.current
        var hasCameraPermission by remember {
            mutableStateOf(
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, 
                    android.Manifest.permission.CAMERA
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            )
        }
        
        QRScannerScreen(
            onQrCodeScanned = { result ->
                // Basic extraction if it's a full URL e.g. ws://192.168.1.5:8887
                // Or just IP
                val ip = result.removePrefix("ws://").substringBefore(":")
                targetIp = ip
                isScanning = false
                isViewerMode = true
                // Note: QrCode scan is legacy now? Or we should embed passkey in QR?
                // If we use passkey mode, QR might not be useful for Auth unless it contains the key.
            },
            onClose = { isScanning = false }
        )
    } else if (isViewerMode) {
        ViewerScreen(hostIp = targetIp, passkey = connectionPasskey)
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
                    0 -> HomeScreen(
                        isBroadcasting = isBroadcasting,
                        onStartService = {
                            onStartService()
                            isBroadcasting = true
                        },
                        onStopService = {
                             onStopService()
                             isBroadcasting = false
                        },
                        onScanClick = { isScanning = true }
                    )
                    1 -> ConnectScreen(
                        onConnect = { ip, key ->
                            targetIp = ip
                            connectionPasskey = key
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
fun HomeScreen(
    isBroadcasting: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onScanClick: () -> Unit
) {
    var passkey by remember { mutableStateOf<String?>(null) }
    
    // Poll for passkey update
    LaunchedEffect(isBroadcasting) {
        if (isBroadcasting) {
            while(true) {
                passkey = RelayService.currentPasskey
                if (passkey != null) break
                kotlinx.coroutines.delay(500)
            }
        } else {
            passkey = null
        }
    }

    val deviceName = remember { "${Build.MANUFACTURER} ${Build.MODEL}" }
    // var showQrDialog by remember { mutableStateOf(false) } // Disabled for passkey mode
    
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
            IconButton(onClick = onScanClick) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = "Scan",
                    tint = Color(0xFF007AFF)
                )
            }
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
                        Text(
                            deviceName, 
                            style = MaterialTheme.typography.titleMedium, // Reduced from bodyLarge
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Edit, null, tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp))
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("Connection Passkey", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = passkey ?: (if (isBroadcasting) "Generating..." else "Not Active"),
                        style = MaterialTheme.typography.headlineSmall.copy( // Reduced from headlineMedium
                            fontWeight = FontWeight.Black,
                            color = if (passkey != null) Color(0xFF007AFF) else Color.Gray,
                            letterSpacing = 4.sp
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    if (passkey != null) {
                        Icon(
                            Icons.Filled.Key, 
                            null, 
                            tint = Color(0xFF007AFF),
                            modifier = Modifier.size(32.dp)
                        )
                    }
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

        // Start/Stop Broadcasting Action
        if (isBroadcasting) {
            Card(
                onClick = { /* Handle disconnect confirmation dialog if needed */ },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                     Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFE8F5E9)), // Light Green bg
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.CastConnected, null, tint = Color(0xFF4CAF50))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Relay is Active",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32) // Darker Green text
                            )
                            Text(
                                "Screen is being shared locally",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                     }
                     
                     Spacer(modifier = Modifier.height(24.dp))
                     
                     Button(
                        onClick = onStopService,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)), // Red color
                        shape = RoundedCornerShape(12.dp)
                     ) {
                        Icon(Icons.Filled.Stop, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop Sharing", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                     }
                }
            }
        } else {
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
                            color = Color(0xFF0D47A1),
                            maxLines = 1, // Prevent overflow
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            "Share this screen to others", 
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1976D2),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.PlayArrow, null, tint = Color(0xFF0D47A1))
                }
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
fun ConnectScreen(onConnect: (String, String) -> Unit) {
    var passkeyInput by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA)),
    ) {
        // Banner Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp) // Reduced height
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF4A90E2), Color(0xFF0044AA))
                    )
                )
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text(
                    "Connection failed?", 
                    style = MaterialTheme.typography.titleLarge, // Slightly smaller
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Ensure both devices are\non the same Wi-Fi.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            Icon(
                Icons.Filled.WifiOff, 
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(60.dp), // Slightly smaller
                tint = Color.White.copy(alpha = 0.8f) // Increased brightness
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
                value = passkeyInput,
                onValueChange = { 
                    if (it.length <= 6) passkeyInput = it 
                    errorMsg = null
                },
                label = { Text("Enter 6-digit Host Passkey") },
                placeholder = { Text("e.g. 123456") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Filled.Key, null) }
            )
            
            if (errorMsg != null) {
                Text(
                    text = errorMsg!!,
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { 
                    if(passkeyInput.isNotEmpty()) {
                        isConnecting = true
                        errorMsg = null
                        scope.launch {
                            val ip = NetworkDiscovery.discoverHost(context, passkeyInput)
                            isConnecting = false
                            if (ip != null) {
                                onConnect(ip, passkeyInput)
                            } else {
                                errorMsg = "Host not found with this passkey."
                            }
                        }
                    }
                },
                enabled = !isConnecting,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Searching...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("Connect", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
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
    val context = LocalContext.current
    var isAccessibilityEnabled by remember { mutableStateOf(checkAccessibilityPermission(context)) }
    var isBatteryOptimized by remember { mutableStateOf(checkBatteryOptimization(context)) }
    
    // Resume check
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = checkAccessibilityPermission(context)
                isBatteryOptimized = checkBatteryOptimization(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        // System Permissions
        Text(
            "System Permissions",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = Color.Gray
        )
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 8.dp)
        ) {
             PermissionItem(
                title = "Remote Control (Touch)",
                subtitle = "Required for remote clicks",
                isEnabled = isAccessibilityEnabled,
                onClick = { 
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                }
            )
            Divider(color = Color(0xFFF0F0F0))
             PermissionItem(
                title = "Run in Background",
                subtitle = "Prevent app from being killed",
                isEnabled = isBatteryOptimized,
                onClick = {
                     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isBatteryOptimized) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:${context.packageName}")
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                }
            )
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
            MenuItem(Icons.Filled.Info, "About Us")
        }
    }
}

@Composable
fun PermissionItem(title: String, subtitle: String, isEnabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Switch(checked = isEnabled, onCheckedChange = { onClick() })
    }
}

fun checkAccessibilityPermission(context: Context): Boolean {
    val expectedId = context.packageName + "/.RelayAccessibilityService"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: ""
    return enabledServices.contains(expectedId)
}

fun checkBatteryOptimization(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
    return true
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

@Composable
fun ShowQRCodeDialog(ip: String, onDismiss: () -> Unit) {
    val qrBitmap = remember(ip) {
        QRCodeUtils.generateQRCode("ws://$ip:8887", 512, 512)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Text("Scan to Connect")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (qrBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier
                            .size(250.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Text("Error generating QR Code")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = ip,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF007AFF)
                )
                Text(
                    text = "Ensure devices are on the same Wi-Fi",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}
