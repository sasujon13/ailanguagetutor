package com.cheradip.ailanguagetutor.feature.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cheradip.ailanguagetutor.core.image.CurveBoundary
import com.cheradip.ailanguagetutor.core.image.CurvedDocumentDetector
import com.cheradip.ailanguagetutor.core.image.DocumentDetectionHints
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

@Composable
fun ScannerLiveCamera(
    onCapture: (ByteArray) -> Unit,
    onDone: () -> Unit,
    onImportGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var detectedBoundary by remember { mutableStateOf<CurveBoundary?>(null) }
    var detectionWidth by remember { mutableStateOf(0) }
    var detectionHeight by remember { mutableStateOf(0) }
    var detectionStatus by remember { mutableStateOf("Align document in frame") }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val lastAnalysisMs = remember { AtomicLong(0L) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor) { proxy ->
                    val now = System.currentTimeMillis()
                    if (now - lastAnalysisMs.get() < 280L) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    lastAnalysisMs.set(now)
                    runCatching {
                        val bitmap = proxy.toBitmap()?.let { rotateBitmap(it, proxy.imageInfo.rotationDegrees) }
                        if (bitmap != null) {
                            val curve = CurvedDocumentDetector.detect(
                                bitmap,
                                DocumentDetectionHints(),
                            )
                            previewView.post {
                                detectedBoundary = curve
                                detectionWidth = bitmap.width
                                detectionHeight = bitmap.height
                                detectionStatus = if (curve.isValid) {
                                    "Document detected — tap capture"
                                } else {
                                    "Align document in frame"
                                }
                            }
                            bitmap.recycle()
                        }
                    }
                    proxy.close()
                }
                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                        imageCapture,
                    )
                }
            }, ContextCompat.getMainExecutor(context))
        }

        detectedBoundary?.takeIf { it.isValid && detectionWidth > 0 && detectionHeight > 0 }?.let { boundary ->
            CurveGuideOverlay(
                boundary = boundary,
                imageWidth = detectionWidth,
                imageHeight = detectionHeight,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Text(
            text = detectionStatus,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingActionButton(onClick = onImportGallery) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery")
            }
            FloatingActionButton(
                onClick = {
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bytes = image.toJpegBytes()
                                image.close()
                                if (bytes != null) onCapture(bytes)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                // no-op
                            }
                        },
                    )
                },
            ) {
                Icon(Icons.Default.Camera, contentDescription = "Capture")
            }
            FloatingActionButton(onClick = onDone) {
                Icon(Icons.Default.Check, contentDescription = "Done")
            }
        }
    }
}

private fun ImageProxy.toBitmap(): Bitmap? {
    val plane = planes.firstOrNull() ?: return null
    if (format != ImageFormat.YUV_420_888) {
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuv.compressToJpeg(Rect(0, 0, width, height), 85, out)
    return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
}

private fun ImageProxy.toJpegBytes(): ByteArray? {
    val bitmap = toBitmap()?.let { rotateBitmap(it, imageInfo.rotationDegrees) } ?: return null
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
    bitmap.recycle()
    return out.toByteArray()
}

private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    if (rotationDegrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated != bitmap) bitmap.recycle()
    return rotated
}
