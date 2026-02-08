package com.example.android_screen_relay

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.util.Size
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.concurrent.Executors

@Composable
fun QRScannerScreen(
    onQrCodeScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        this.scaleType = PreviewView.ScaleType.FILL_CENTER
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), QrCodeAnalyzer { result ->
                            onQrCodeScanned(result)
                        })

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (exc: Exception) {
                            exc.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Overlay with hole
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scanSize = size.width * 0.7f
                val left = (size.width - scanSize) / 2
                val top = (size.height - scanSize) / 2
                
                // Dimmed background
                drawRect(
                    color = Color.Black.copy(alpha = 0.5f),
                    size = size
                )

                // Cut out the hole by clearing the destination
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(scanSize, scanSize),
                    cornerRadius = CornerRadius(30f, 30f),
                    blendMode = BlendMode.Clear 
                )
                
                // White Border
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(scanSize, scanSize),
                    cornerRadius = CornerRadius(30f, 30f),
                    style = Stroke(width = 8f)
                )

                // Corner Lines (Optional: adds "scanner" feel)
                val lineLen = 60f
                val strokeW = 12f
                
                // Top Left
                drawLine(Color.White, Offset(left, top), Offset(left + lineLen, top), strokeW)
                drawLine(Color.White, Offset(left, top), Offset(left, top + lineLen), strokeW)
            }

            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    modifier = Modifier.height(40.dp)
                ) {
                    TabRow(
                        selectedTabIndex = 1,
                        modifier = Modifier.width(200.dp),
                        containerColor = Color.Transparent,
                        indicator = {},
                        divider = {}
                    ) {
                         Tab(selected = false, onClick = {}, text = { Text("Passkey", color = Color.Black) })
                         Tab(selected = true, onClick = {}, text = { Text("QR", color = Color.Black) })
                    }
                }
            }

            // Close Button
            IconButton(
                onClick = onClose,
                modifier = Modifier.padding(top = 48.dp, start = 16.dp)
            ) {
                Icon(Icons.Filled.Close, "Close", tint = Color.White)
            }
            
            // Bottom sheet area hint
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                     Text("Scan QR Code", style = MaterialTheme.typography.titleMedium)
                     Text("Align QR code within the frame to connect", color = Color.Gray)
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
             Text("Camera permission is required to scan QR codes.")
             Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                 Text("Grant Permission")
             }
        }
    }
}

class QrCodeAnalyzer(private val onQrCodeScanned: (String) -> Unit) : ImageAnalysis.Analyzer {
    
    private val reader = MultiFormatReader().apply {
        val map = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
        )
        setHints(map)
    }

    override fun analyze(image: ImageProxy) {
        if (image.format !in listOf(ImageFormat.YUV_420_888, ImageFormat.YUV_422_888, ImageFormat.YUV_444_888)) {
            image.close()
            return
        }

        val buffer = image.planes[0].buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        
        // ImageProxy provides data in a way that might include row strides.
        // For simplicity with CameraX and standard implementation:
        val width = image.width
        val height = image.height
        
        // Note: This is a simplified planar YUV source. 
        // In production apps, rotation handling and stride handling is often more complex.
        val source = PlanarYUVLuminanceSource(
            data, width, height,
            0, 0, width, height,
            false // reverseHorizontal
        )
        
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        
        try {
            val result = reader.decodeWithState(binaryBitmap)
            onQrCodeScanned(result.text)
        } catch (e: NotFoundException) {
            // No QR code found
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }
}
