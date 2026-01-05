package com.example.collage

import android.net.Uri
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollagePreview(
    modifier: Modifier = Modifier,
    template: CollageTemplate,
    slotUris: List<Uri?>,
    slotTransforms: List<SlotTransform>,
    spacingPx: Float,
    cornerRadiusPx: Float,
    vm: CollageViewModel,
    activeCameraSlot: Int,
    focusedSlotIndex: Int = -1,
    onSlotTap: (Int) -> Unit,
    onSlotLongPress: (Int) -> Unit,
    onTransformChange: (Int, SlotTransform) -> Unit,
    onCameraCaptured: (Int, Uri) -> Unit,
    onCameraCancel: () -> Unit
) {
    val context = LocalContext.current

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
    ) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }

        val spacing = spacingPx.coerceAtLeast(0f)
        val radiusDp = with(density) { (cornerRadiusPx / density.density).dp }

        template.slots.forEachIndexed { idx, r ->
            val left = r.x * wPx + spacing / 2f
            val top = r.y * hPx + spacing / 2f
            val slotW = r.w * wPx - spacing
            val slotH = r.h * hPx - spacing
            val slotAspect = if (slotH > 0f) slotW / slotH else 1f

            val committedUri = slotUris.getOrNull(idx)
            val draftUri = vm.draftCaptureUris.getOrNull(idx)
            val displayUri = committedUri ?: draftUri

            val currentT = slotTransforms.getOrNull(idx) ?: SlotTransform()

            var thumb by remember(displayUri) { mutableStateOf<ImageBitmap?>(null) }

            LaunchedEffect(displayUri) {
                thumb = null
                val u = displayUri ?: return@LaunchedEffect
                vm.getCachedThumb(u)?.let { thumb = it; return@LaunchedEffect }
                val loaded = ThumbnailLoader.loadThumbnail(context, u, maxSizePx = 1024)
                if (loaded != null) vm.putCachedThumb(u, loaded)
                thumb = loaded
            }

            val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                val newScale = (currentT.scale * zoomChange).coerceIn(1f, 4f)
                val nx = if (slotW > 0f) currentT.offsetX + (panChange.x / slotW) * 2f else currentT.offsetX
                val ny = if (slotH > 0f) currentT.offsetY + (panChange.y / slotH) * 2f else currentT.offsetY
                onTransformChange(
                    idx,
                    SlotTransform(
                        scale = newScale,
                        offsetX = nx.coerceIn(-1f, 1f),
                        offsetY = ny.coerceIn(-1f, 1f)
                    )
                )
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .width(with(density) { slotW.toDp() })
                    .height(with(density) { slotH.toDp() })
                    .clip(RoundedCornerShape(radiusDp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                    .border(
                        width = if (idx == focusedSlotIndex) 2.dp else 1.dp,
                        color = if (idx == focusedSlotIndex) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(radiusDp)
                    )
                    .combinedClickable(
                        onClick = { onSlotTap(idx) },
                        onLongClick = { onSlotLongPress(idx) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    idx == activeCameraSlot -> {
                        CameraSlot(
                            modifier = Modifier.fillMaxSize(),
                            slotIndex = idx,
                            slotAspect = slotAspect,
                            vm = vm,
                            captureRequestToken = vm.captureRequests.getOrNull(idx) ?: 0,
                            onCancel = onCameraCancel
                        )
                    }

                    displayUri != null && thumb != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .transformable(state = transformState)
                        ) {
                            Image(
                                bitmap = thumb!!,
                                contentDescription = "Slot photo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = currentT.scale,
                                        scaleY = currentT.scale,
                                        translationX = (currentT.offsetX / 2f) * slotW,
                                        translationY = (currentT.offsetY / 2f) * slotH
                                    ),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    else -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                tonalElevation = 2.dp
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Camera", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Photo, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Hold for gallery", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class FlashModeUi { OFF, AUTO, ON }



@Composable
private fun CameraSlot(
    modifier: Modifier = Modifier,
    slotIndex: Int,
    slotAspect: Float,
    vm: CollageViewModel,
    captureRequestToken: Int,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isBound by remember { mutableStateOf(false) }

    val persistedDraft = vm.draftCaptureUris.getOrNull(slotIndex)
    var capturedUri by remember(slotIndex, persistedDraft) { mutableStateOf(persistedDraft) }
    var capturedThumb by remember { mutableStateOf<ImageBitmap?>(null) }


// External shutter: when token increments, trigger a capture (if we're not already holding a draft)
LaunchedEffect(captureRequestToken) {
    val cap = imageCapture
    if (cap != null && capturedUri == null) {
        // mimic the old shutter click behavior
        val file = CameraFiles.createTempJpeg(context)
        val out = ImageCapture.OutputFileOptions.Builder(file).build()
        cap.takePicture(
            out,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val uri = outputFileResults.savedUri ?: CameraFiles.toContentUri(context, file)
                    vm.setDraftCapture(slotIndex, uri)
                    capturedUri = uri
                    // thumbnail is loaded lazily below
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                override fun onError(exception: ImageCaptureException) {
                    // ignore; user can try again
                }
            }
        )
    }
}

// Global camera controls from ViewModel (keeps slot UI clean)
val lensFacing = if (vm.lensFacingUi.value == 1) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
val flashModeUi = when (vm.flashModeUi.value) {
        0 -> FlashModeUi.OFF
        1 -> FlashModeUi.AUTO
        else -> FlashModeUi.ON
    }
val gridOn = vm.gridOn.value


    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    fun uiToFlashMode(mode: FlashModeUi): Int = when (mode) {
        FlashModeUi.OFF -> ImageCapture.FLASH_MODE_OFF
        FlashModeUi.AUTO -> ImageCapture.FLASH_MODE_AUTO
        FlashModeUi.ON -> ImageCapture.FLASH_MODE_ON
    }


    LaunchedEffect(slotAspect, lensFacing, flashModeUi) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        val aspect = CameraAspect.closestCameraXAspect(slotAspect)
        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0

        val preview = Preview.Builder()
            .setTargetAspectRatio(aspect)
            .setTargetRotation(rotation)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val capture = ImageCapture.Builder()
            .setTargetAspectRatio(aspect)
            .setTargetRotation(rotation)
            .setFlashMode(uiToFlashMode(flashModeUi))
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        imageCapture = capture
        try {
            provider.unbindAll()
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            isBound = true
        } catch (_: Exception) {
            isBound = false
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                val rot = view.display?.rotation ?: Surface.ROTATION_0
                imageCapture?.targetRotation = rot
            }
        )

        if (capturedThumb != null) {
            Image(
                bitmap = capturedThumb!!,
                contentDescription = "Captured",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        if (gridOn) {
            RuleOfThirdsOverlay(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
        )

        // top-right controls
        
        /* In-slot grid/flash/flip controls moved to global controls below Templates */


        
        /* in-slot bottom bar removed */

    }

    LaunchedEffect(capturedUri) {
        val u = capturedUri ?: return@LaunchedEffect
        capturedThumb = vm.getCachedThumb(u) ?: ThumbnailLoader.loadThumbnail(context, u, 1600)?.also {
            vm.putCachedThumb(u, it)
        }
    }
}

@Composable
private fun RuleOfThirdsOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val x1 = w / 3f
        val x2 = 2f * w / 3f
        val y1 = h / 3f
        val y2 = 2f * h / 3f
        val c = Color.White.copy(alpha = 0.35f)

        drawLine(c, start = Offset(x1, 0f), end = Offset(x1, h), strokeWidth = 1.5f, cap = StrokeCap.Round)
        drawLine(c, start = Offset(x2, 0f), end = Offset(x2, h), strokeWidth = 1.5f, cap = StrokeCap.Round)
        drawLine(c, start = Offset(0f, y1), end = Offset(w, y1), strokeWidth = 1.5f, cap = StrokeCap.Round)
        drawLine(c, start = Offset(0f, y2), end = Offset(w, y2), strokeWidth = 1.5f, cap = StrokeCap.Round)
    }
}


@Composable
private fun RetakeButton(compact: Boolean, onClick: () -> Unit) {
    val size = if (compact) 64.dp else 76.dp
    val inner = if (compact) 48.dp else 58.dp

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(size),
            shape = RoundedCornerShape(999.dp),
            color = Color.White.copy(alpha = 0.86f),
            shadowElevation = 10.dp
        ) {}
        Surface(
            modifier = Modifier.size(inner),
            shape = RoundedCornerShape(999.dp),
            color = Color.White.copy(alpha = 0.98f)
        ) {}
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "Retake",
            tint = Color.Black.copy(alpha = 0.70f),
            modifier = Modifier.size(if (compact) 22.dp else 24.dp)
        )
    }
}
@Composable
private fun CaptureButton(enabled: Boolean, compact: Boolean, onClick: () -> Unit) {
    val outer = if (compact) 60.dp else 72.dp
    val inner = if (compact) 46.dp else 56.dp

    Box(
        modifier = Modifier
            .size(outer)
            .clip(RoundedCornerShape(100))
            .background(Color.White.copy(alpha = if (enabled) 0.18f else 0.10f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(outer),
            shape = RoundedCornerShape(100),
            color = Color.White.copy(alpha = if (enabled) 0.92f else 0.55f),
            shadowElevation = 10.dp
        ) {}
        Surface(
            modifier = Modifier.size(inner),
            shape = RoundedCornerShape(100),
            color = Color.White.copy(alpha = if (enabled) 1f else 0.65f)
        ) {}
    }
}
