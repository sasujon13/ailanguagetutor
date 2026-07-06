package com.cheradip.ailanguagetutor.feature.scanner

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.cheradip.ailanguagetutor.core.image.PointF
import java.util.concurrent.Executors

/**
 * CameraX live scan with real-time document boundary overlay (flat quad or curved polygon).
 * ML Kit capture cannot be customized; this is the in-app live boundary guide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScanCaptureScreen(
    onCaptured: (ByteArray) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    var polygon by remember { mutableStateOf<List<PointF>>(emptyList()) }
    var isCurved by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("Align document inside the frame") }
    var capturing by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(
                        cameraExecutor,
                            LiveScanAnalyzer(onBoundary = { points, curved ->
                            mainExecutor.execute {
                                polygon = points
                                isCurved = curved
                                hint = if (curved) {
                                    "Curved page detected — hold steady"
                                } else {
                                    "Document edges detected"
                                }
                            }
                        }),
                    )
                }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                    imageCapture,
                )
            } catch (_: Exception) {
                hint = "Camera unavailable"
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)
        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
            cameraExecutor.shutdown()
        }
    }

    BackHandler(onBack = onCancel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live scan") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )

            if (polygon.size >= 3) {
                DocumentBoundaryOverlay(
                    polygonNorm = polygon,
                    isCurved = isCurved,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = hint, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (isCurved) "Dashed outline = curved boundary" else "Solid outline = flat document",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                FloatingActionButton(
                    onClick = {
                        if (capturing) return@FloatingActionButton
                        capturing = true
                        imageCapture.takePicture(
                            cameraExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bytes = image.toJpegBytes()
                                    image.close()
                                    mainExecutor.execute {
                                        capturing = false
                                        if (bytes != null) onCaptured(bytes) else hint = "Capture failed — try again"
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    mainExecutor.execute {
                                        capturing = false
                                        hint = "Capture failed — try again"
                                    }
                                }
                            },
                        )
                    },
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Icon(Icons.Default.Camera, contentDescription = "Capture")
                }
            }
        }
    }
}

private fun ImageProxy.toJpegBytes(): ByteArray? {
    if (planes.isEmpty()) return null
    val buffer = planes[0].buffer
    return ByteArray(buffer.remaining()).also { buffer.get(it) }
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
