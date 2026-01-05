package com.example.collage

import android.net.Uri
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
private fun CrossfadeThumb(
    bitmap: ImageBitmap?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    durationMs: Int = 160
) {
    // We crossfade between the previous cached frame and the newest cached frame.
    // This removes the harsh "blink" feeling when frames update.
    var current by remember { mutableStateOf<ImageBitmap?>(null) }
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(bitmap) {
        if (bitmap == null) {
            // Nothing to show.
            previous = current
            current = null
            alpha.snapTo(1f)
            return@LaunchedEffect
        }

        if (current === bitmap) return@LaunchedEffect
        previous = current
        current = bitmap
        alpha.snapTo(0f)
        alpha.animateTo(1f, animationSpec = tween(durationMs))
    }

    Box(modifier) {
        val p = previous
        val c = current

        if (p != null && c != null) {
            Image(
                bitmap = p,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                alpha = (1f - alpha.value).coerceIn(0f, 1f)
            )
            Image(
                bitmap = c,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                alpha = alpha.value.coerceIn(0f, 1f)
            )
        } else if (c != null) {
            Image(
                bitmap = c,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )
        }
    }
}

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
    var isMultiplexFallback by remember { mutableStateOf(false) }
    var isBinding by remember { mutableStateOf(true) }
    var isCapturing by remember { mutableStateOf(false) }

    // Fallback: keep the latest images we "fake-capture" by alternating cameras.
    var latestBackUri by remember { mutableStateOf<Uri?>(null) }
    var latestFrontUri by remember { mutableStateOf<Uri?>(null) }

    // In fallback mode we actively bind only one lens at a time.
    var activeLensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    val scope = rememberCoroutineScope()
    var multiplexJob by remember { mutableStateOf<Job?>(null) }

    // Load thumbnails for cached URIs (fallback mode UI)
    val backThumb: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, latestBackUri) {
        value = latestBackUri?.let { ThumbnailLoader.loadThumbnail(context, it, 900) }
    }
    val frontThumb: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, latestFrontUri) {
        value = latestFrontUri?.let { ThumbnailLoader.loadThumbnail(context, it, 900) }
    }

    @Composable
    fun CrossfadeThumb(
        bitmap: ImageBitmap?,
        contentDescription: String,
        modifier: Modifier = Modifier
    ) {
        // Keep a previous frame around and crossfade to the new one.
        var previous by remember { mutableStateOf<ImageBitmap?>(null) }
        var current by remember { mutableStateOf<ImageBitmap?>(null) }

        val alpha = remember { Animatable(1f) }

        LaunchedEffect(bitmap) {
            if (bitmap == null) return@LaunchedEffect
            if (current == null) {
                current = bitmap
                alpha.snapTo(1f)
                return@LaunchedEffect
            }
            if (bitmap == current) return@LaunchedEffect

            previous = current
            current = bitmap
            alpha.snapTo(0f)
            alpha.animateTo(1f, animationSpec = tween(durationMillis = 180))
        }

        Box(modifier) {
            val prev = previous
            val cur = current
            if (prev != null) {
                Image(
                    bitmap = prev,
                    contentDescription = "$contentDescription (previous)",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 1f - alpha.value
                )
            }
            if (cur != null) {
                Image(
                    bitmap = cur,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = alpha.value
                )
            }
        }
    }

    @Composable
    fun CrossfadeThumb(
        bitmap: ImageBitmap?,
        contentDescription: String,
        modifier: Modifier = Modifier
    ) {
        // Keep previous frame and crossfade to the new one.
        var current by remember { mutableStateOf<ImageBitmap?>(null) }
        var previous by remember { mutableStateOf<ImageBitmap?>(null) }
        val progress = remember { Animatable(1f) }

        LaunchedEffect(bitmap) {
            if (bitmap == null) return@LaunchedEffect
            if (current == null) {
                current = bitmap
                progress.snapTo(1f)
                return@LaunchedEffect
            }
            if (bitmap === current) return@LaunchedEffect
            previous = current
            current = bitmap
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                animationSpec = tween(durationMillis = 180)
            )
            // Drop the previous frame after the fade completes to save memory.
            previous = null
        }

        Box(modifier) {
            val p = progress.value
            val prev = previous
            val curr = current
            if (prev != null) {
                Image(
                    bitmap = prev,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = 1f - p),
                    contentScale = ContentScale.Crop
                )
            }
            if (curr != null) {
                Image(
                    bitmap = curr,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = p),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }

    suspend fun takePictureSuspend(capture: ImageCapture, file: java.io.File): Uri =
        suspendCancellableCoroutine { cont ->
            val out = ImageCapture.OutputFileOptions.Builder(file).build()
            capture.takePicture(
                out,
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val uri = output.savedUri ?: CameraFiles.toContentUri(context, file)
                        if (!cont.isCompleted) cont.resume(uri)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (!cont.isCompleted) cont.resumeWithException(exception)
                    }
                }
            )
        }

    suspend fun bindSingleCamera(lensFacing: Int) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        provider.unbindAll()

        val rotation = when (lensFacing) {
            CameraSelector.LENS_FACING_FRONT -> frontPreviewView.display?.rotation ?: Surface.ROTATION_0
            else -> backPreviewView.display?.rotation ?: Surface.ROTATION_0
        }

        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build()
            .also {
                val pv = if (lensFacing == CameraSelector.LENS_FACING_FRONT) frontPreviewView else backPreviewView
                it.setSurfaceProvider(pv.surfaceProvider)
            }

        val img = ImageCapture.Builder()
            .setTargetRotation(rotation)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        provider.bindToLifecycle(lifecycleOwner, selector, preview, img)

        if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            frontCapture = img
            backCapture = null
        } else {
            backCapture = img
            frontCapture = null
        }
    }

    fun stopMultiplex() {
        multiplexJob?.cancel()
        multiplexJob = null
    }

    fun startMultiplex() {
        stopMultiplex()
        multiplexJob = scope.launch {
            // "Fake live" strategy:
            // - Alternate cameras rapidly (time-multiplexing).
            // - Capture a low-latency still for each lens to keep the last frame fresh.
            // - UI shows the live Preview of the *currently bound* lens + the latest cached frame
            //   for the other lens.
            //
            // NOTE: This is still not a true simultaneous dual-camera preview. But by reducing the
            // cycle time, it feels much closer to a live dual feed than a 1s tick.

            val settleMs = 60L      // wait a moment after binding so preview/capture becomes valid
            val betweenLensMs = 60L // small gap between back->front to reduce thrash
            val cycleGapMs = 80L    // gap before starting the next back->front cycle

            while (isActive) {
                try {
                    // If user is doing a shutter capture, pause background updates.
                    if (isCapturing) {
                        delay(200)
                        continue
                    }
                    // One "cycle" refreshes BOTH: BACK then FRONT as fast as possible.

                    // BACK
                    activeLensFacing = CameraSelector.LENS_FACING_BACK
                    bindSingleCamera(CameraSelector.LENS_FACING_BACK)
                    delay(settleMs)
                    backCapture?.let { cap ->
                        val f = CameraFiles.createTempJpeg(context)
                        latestBackUri = takePictureSuspend(cap, f)
                    }

                    delay(betweenLensMs)

                    // FRONT
                    activeLensFacing = CameraSelector.LENS_FACING_FRONT
                    bindSingleCamera(CameraSelector.LENS_FACING_FRONT)
                    delay(settleMs)
                    frontCapture?.let { cap ->
                        val f = CameraFiles.createTempJpeg(context)
                        latestFrontUri = takePictureSuspend(cap, f)
                    }
                } catch (_: Throwable) {
                    // If something fails in fallback loop, just keep trying next tick.
                }

                // Start the next cycle quickly for a more "live" feel.
                delay(cycleGapMs)
            }
        }
    }

    LaunchedEffect(Unit) {
        isBinding = true
        bindError = null
        isMultiplexFallback = false

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
                isMultiplexFallback = true
                bindError = null
                startMultiplex()
            }
        } catch (e: Throwable) {
            bindError = "Unable to open cameras."
        } finally {
            isBinding = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopMultiplex()
            try {
                val provider = ProcessCameraProvider.getInstance(context).get()
                provider.unbindAll()
            } catch (_: Throwable) {
            }
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
                            if (isMultiplexFallback) {
                                // Fallback mode: show rapidly refreshed cached frames.
                                // Avoid swapping PreviewViews in/out (causes blinking on many devices).
                                CrossfadeThumb(
                                    bitmap = backThumb,
                                    contentDescription = "Back cached",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AndroidView(factory = { backPreviewView }, modifier = Modifier.fillMaxSize())
                            }
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
                            if (isMultiplexFallback) {
                                CrossfadeThumb(
                                    bitmap = frontThumb,
                                    contentDescription = "Front cached",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                AndroidView(factory = { frontPreviewView }, modifier = Modifier.fillMaxSize())
                            }
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

                    if (isMultiplexFallback) {
                        Text(
                            "Fallback mode: rapid time‑multiplexing (fake live). Tap shutter to use the latest pair.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                if (bindError != null || isCapturing) return@FilledTonalIconButton

                                // If we have a cached pair in fallback mode, use it immediately.
                                if (isMultiplexFallback) {
                                    val b = latestBackUri
                                    val f = latestFrontUri
                                    if (b != null && f != null) {
                                        onCaptured(f, b)
                                        return@FilledTonalIconButton
                                    }

                                    // Otherwise: do a quick refresh cycle (capture active then other) and commit.
                                    stopMultiplex()
                                    isCapturing = true
                                    scope.launch {
                                        try {
                                            // Capture currently bound lens first.
                                            val cap1 = frontCapture ?: backCapture
                                            val lens1IsFront = frontCapture != null
                                            if (cap1 != null) {
                                                val f1 = CameraFiles.createTempJpeg(context)
                                                val u1 = takePictureSuspend(cap1, f1)
                                                if (lens1IsFront) latestFrontUri = u1 else latestBackUri = u1
                                            }

                                            // Switch and capture the other lens.
                                            val otherLens = if (lens1IsFront) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
                                            bindSingleCamera(otherLens)
                                            delay(80)
                                            val cap2 = if (otherLens == CameraSelector.LENS_FACING_FRONT) frontCapture else backCapture
                                            if (cap2 != null) {
                                                val f2 = CameraFiles.createTempJpeg(context)
                                                val u2 = takePictureSuspend(cap2, f2)
                                                if (otherLens == CameraSelector.LENS_FACING_FRONT) latestFrontUri = u2 else latestBackUri = u2
                                            }

                                            val b2 = latestBackUri
                                            val f2u = latestFrontUri
                                            if (b2 != null && f2u != null) {
                                                onCaptured(f2u, b2)
                                            }
                                        } catch (_: Throwable) {
                                        } finally {
                                            isCapturing = false
                                            // Restart background loop so it keeps updating previews.
                                            if (isMultiplexFallback) startMultiplex()
                                        }
                                    }
                                    return@FilledTonalIconButton
                                }

                                val back = backCapture
                                val front = frontCapture
                                if (back == null || front == null) return@FilledTonalIconButton

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
