package com.example.collage

import android.net.Uri
import android.view.Surface
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.atomic.AtomicReference

/**
 * Full-screen overlay that *tries* to open FRONT + BACK cameras at the same time.
 *
 * Notes:
 * - Concurrent camera is device-dependent. If the device does not support it, we show an error.
 * - Capture is triggered for both ImageCapture use cases as close together as possible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualCameraCaptureDialog(
    onDismiss: () -> Unit,
    onCaptured: (frontUri: Uri, backUri: Uri) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { ContextCompat.getMainExecutor(context) }

    // Two previews (2-slot UI)
    val backPreviewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val frontPreviewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    var backCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var frontCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var bindError by remember { mutableStateOf<String?>(null) }
    var isBinding by remember { mutableStateOf(true) }
    var isCapturing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isBinding = true
        bindError = null

        try {
            val provider = ProcessCameraProvider.getInstance(context).get()

            val rotationBack = backPreviewView.display?.rotation ?: Surface.ROTATION_0
            val rotationFront = frontPreviewView.display?.rotation ?: Surface.ROTATION_0

            val backPreview = Preview.Builder()
                .setTargetRotation(rotationBack)
                .build()
                .also { it.setSurfaceProvider(backPreviewView.surfaceProvider) }
            val frontPreview = Preview.Builder()
                .setTargetRotation(rotationFront)
                .build()
                .also { it.setSurfaceProvider(frontPreviewView.surfaceProvider) }

            val backImg = ImageCapture.Builder()
                .setTargetRotation(rotationBack)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val frontImg = ImageCapture.Builder()
                .setTargetRotation(rotationFront)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            backCapture = backImg
            frontCapture = frontImg

            provider.unbindAll()

            // --- Concurrent bind (CameraX 1.2+). This is device-dependent. ---
            // We intentionally keep this in a try/catch so unsupported devices fail gracefully.
            @Suppress("UNCHECKED_CAST")
            try {
                val clazz = Class.forName("androidx.camera.core.ConcurrentCamera")
                val singleCfgClazz = Class.forName("androidx.camera.core.ConcurrentCamera\$SingleCameraConfig")
                val cameraSelectorClazz = Class.forName("androidx.camera.core.CameraSelector")
                val defaultBack = cameraSelectorClazz.getField("DEFAULT_BACK_CAMERA").get(null)
                val defaultFront = cameraSelectorClazz.getField("DEFAULT_FRONT_CAMERA").get(null)

                val useCasesBack = listOf(backPreview, backImg)
                val useCasesFront = listOf(frontPreview, frontImg)

                val singleCtor = singleCfgClazz.constructors.first()
                val cfgBack = singleCtor.newInstance(defaultBack, useCasesBack)
                val cfgFront = singleCtor.newInstance(defaultFront, useCasesFront)

                // provider.bindToLifecycle(lifecycleOwner, cfgBack, cfgFront)
                val bindMethod = provider.javaClass.methods.firstOrNull { m ->
                    m.name == "bindToLifecycle" && m.parameterTypes.size == 3 &&
                        m.parameterTypes[0].isAssignableFrom(lifecycleOwner.javaClass)
                } ?: provider.javaClass.methods.first { m ->
                    m.name == "bindToLifecycle" && m.parameterTypes.size == 3
                }
                bindMethod.invoke(provider, lifecycleOwner, cfgBack, cfgFront)
            } catch (e: Throwable) {
                // Fallback: if ConcurrentCamera API isn't present or device can't do it.
                bindError = "Dual-camera preview isn't supported on this device."
            }
        } catch (e: Throwable) {
            bindError = "Unable to open cameras."
        } finally {
            isBinding = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp)),
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(18.dp)
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = { Text("Dual Camera") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Close")
                            }
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black)
                        ) {
                            AndroidView(factory = { backPreviewView }, modifier = Modifier.fillMaxSize())
                            Text(
                                "Back",
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black)
                        ) {
                            AndroidView(factory = { frontPreviewView }, modifier = Modifier.fillMaxSize())
                            Text(
                                "Front",
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(10.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }

                    if (isBinding) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    if (bindError != null) {
                        Text(
                            bindError!!,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                val back = backCapture
                                val front = frontCapture
                                if (back == null || front == null || bindError != null || isCapturing) return@FilledTonalIconButton

                                isCapturing = true

                                val backFile = CameraFiles.createTempJpeg(context)
                                val frontFile = CameraFiles.createTempJpeg(context)

                                val backOut = ImageCapture.OutputFileOptions.Builder(backFile).build()
                                val frontOut = ImageCapture.OutputFileOptions.Builder(frontFile).build()

                                val backUriRef = AtomicReference<Uri?>(null)
                                val frontUriRef = AtomicReference<Uri?>(null)

                                fun maybeFinish() {
                                    val b = backUriRef.get()
                                    val f = frontUriRef.get()
                                    if (b != null && f != null) {
                                        isCapturing = false
                                        onCaptured(f, b)
                                    }
                                }

                                // Trigger both captures as close together as we can.
                                back.takePicture(
                                    backOut,
                                    executor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                            val uri = output.savedUri ?: CameraFiles.toContentUri(context, backFile)
                                            backUriRef.set(uri)
                                            maybeFinish()
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            isCapturing = false
                                        }
                                    }
                                )

                                front.takePicture(
                                    frontOut,
                                    executor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                            val uri = output.savedUri ?: CameraFiles.toContentUri(context, frontFile)
                                            frontUriRef.set(uri)
                                            maybeFinish()
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            isCapturing = false
                                        }
                                    }
                                )
                            },
                            modifier = Modifier.size(76.dp)
                        ) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = "Capture both",
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
