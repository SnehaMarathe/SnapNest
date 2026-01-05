package com.example.collage

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.collage.ui.theme.BrandBlue
import com.example.collage.ui.theme.BrandPink
import com.example.collage.ui.theme.BrandPurple
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.BorderStroke
import androidx.compose.ui.text.style.TextAlign

@Composable
private fun TopBarIconBox(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    tint: Color
) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint
            )
        }
    }
}

@Composable
private fun FollowInstagramBar() {
    val context = LocalContext.current
    Surface(tonalElevation = 1.dp) {
        TextButton(
            onClick = {
                val uri = Uri.parse("https://instagram.com/Snap2Nest")
                val i = Intent(Intent.ACTION_VIEW, uri)
                try { context.startActivity(i) } catch (_: Exception) { }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text(
                text = "Follow on Instagram @Snap2Nest",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private enum class Tab { TEMPLATES, ADJUST, EXPORT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaunchUiRoot(vm: CollageViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var tab by remember { mutableStateOf(Tab.TEMPLATES) }

    // Forces preview subtree to recreate after edits (busts image/thumbnail caches)
    var imageRefreshTick by remember { mutableIntStateOf(0) }

    // Slot being edited (for crop / highlight)
    var activeSlot by remember { mutableIntStateOf(-1) }
    var cropSlot by remember { mutableIntStateOf(-1) }
    // Slot currently showing CameraX preview
    var activeCameraSlot by remember { mutableIntStateOf(-1) }
    // Slot that has the most recent draft capture (for Edit even after auto-advance)
    var lastDraftSlot by remember { mutableIntStateOf(-1) }

    var showAdjustSheet by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var lastExportUri by remember { mutableStateOf<Uri?>(null) }

    // Dual camera capture (front + back concurrently, best-effort)
    var showDualCamera by remember { mutableStateOf(false) }

    // ---- Crop flow ----
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        when (res.resultCode) {
            Activity.RESULT_OK -> {
                val intent = res.data
                val out: Uri? = intent?.let { UCrop.getOutput(it) } ?: intent?.data
                if (out != null && cropSlot >= 0) {
                    // If we're editing a draft (captured but not confirmed), update the draft so the slot updates instantly.
                    val hasDraft = vm.draftCaptureUris.getOrNull(cropSlot) != null
                    if (hasDraft) {
                        vm.setDraftCapture(cropSlot, out)
                    } else {
                        vm.setSlotUri(cropSlot, out)
                    }
                    // Close live camera overlay so the edited image shows immediately
                    activeCameraSlot = -1
                    scope.launch {
                        val t = ThumbnailLoader.loadThumbnail(context, out, maxSizePx = 1024)
                        if (t != null) vm.putCachedThumb(out, t)
                    }
                    // 🔥 bump refresh so UI reloads immediately (avoids cached bitmap showing)
                    imageRefreshTick++
                    activeSlot = -1
                    cropSlot = -1
                } else {
                    scope.launch { snackbar.showSnackbar("Crop finished") }
                }
            }
            UCrop.RESULT_ERROR -> {
                val err = UCrop.getError(res.data!!)
                scope.launch { snackbar.showSnackbar("Crop failed: ${err?.message ?: "unknown"}") }
            }
            else -> { /* cancelled */ }
        }
        // Note: Don't clear draft captures here. Clearing can revert the slot back to the live camera preview
        // before the edited image is displayed.
    }

    fun launchCrop(slotIndex: Int, source: Uri) {
        cropSlot = slotIndex
        cropLauncher.launch(UCropHelper.buildIntent(context, source))
    }

    // ---- Gallery picker (LONG PRESS) ----
    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && activeSlot >= 0) {
            // Commit before crop so slot never becomes empty.
            vm.setSlotUri(activeSlot, uri)
            launchCrop(activeSlot, uri)
        }
        activeSlot = -1
    }

    // ---- Camera permission + open camera (TAP) ----
    val cameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            activeCameraSlot = -1
            scope.launch { snackbar.showSnackbar("Camera permission required") }
        }
    }

    val dualCameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            showDualCamera = false
            scope.launch { snackbar.showSnackbar("Camera permission required") }
        } else {
            showDualCamera = true
        }
    }

    fun startCamera(slotIdx: Int) {
        activeCameraSlot = slotIdx
        activeSlot = slotIdx
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        cameraPermission.launch(Manifest.permission.CAMERA)
    }

    // Auto-start camera for each template (best UX).
    // When template changes, start camera on the first empty slot (or slot 0).
    LaunchedEffect(vm.selectedTemplate.value.id) {
        val slotsCount = vm.selectedTemplate.value.slots.size
        if (slotsCount == 0) return@LaunchedEffect
        // If user is in dual camera overlay, don't interrupt.
        if (showDualCamera) return@LaunchedEffect

        // Pick first empty slot; fallback to slot 0.
        val firstEmpty = (0 until slotsCount).firstOrNull { i ->
            (vm.slotUris.getOrNull(i) == null && vm.draftCaptureUris.getOrNull(i) == null)
        } ?: 0
        startCamera(firstEmpty)
    }

    // ---- Export + share ----
    fun exportNow(): Uri? =
        CollageRenderer.renderAndSave(
            context = context,
            template = vm.selectedTemplate.value,
            slotUris = vm.slotUris.mapIndexed { i, u -> u ?: vm.draftCaptureUris.getOrNull(i) }.toList(),
            slotTransforms = vm.slotTransforms.toList(),
            spacingPx = vm.spacingPx.value,
            cornerRadiusPx = vm.cornerRadiusPx.value,
            outSizePx = 2048
        )

    fun shareTo(packageName: String, uri: Uri) {
        val pm = context.packageManager
        val direct = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(packageName)
        }
        val finalIntent =
            if (direct.resolveActivity(pm) != null) direct
            else Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Share collage"
            )
        try {
            context.startActivity(finalIntent)
        } catch (_: Exception) {
            scope.launch { snackbar.showSnackbar("Unable to share") }
        }
    }

    fun shareChooser(uri: Uri) {
        val chooser = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Share collage"
        )
        try {
            context.startActivity(chooser)
        } catch (_: Exception) {
            scope.launch { snackbar.showSnackbar("Unable to share") }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {},
                title = {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(
                                SpanStyle(
                                    brush = Brush.linearGradient(listOf(BrandPurple, BrandPink)),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            ) { append("Snap2") }
                            withStyle(
                                SpanStyle(
                                    brush = Brush.linearGradient(listOf(BrandBlue, Color(0xFF1F6BFF))),
                                    fontWeight = FontWeight.ExtraBold
                                )
                            ) { append("Nest") }
                        },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        },
        bottomBar = {
            Column {
                FollowInstagramBar()
                NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.TEMPLATES,
                    onClick = { tab = Tab.TEMPLATES },
                    icon = { Icon(Icons.Filled.Collections, contentDescription = null) },
                    label = { Text("Templates") }
                )
                NavigationBarItem(
                    selected = tab == Tab.ADJUST,
                    onClick = { tab = Tab.ADJUST; showAdjustSheet = true },
                    icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    label = { Text("Adjust") }
                )
                NavigationBarItem(
                    selected = tab == Tab.EXPORT,
                    onClick = {
                        tab = Tab.EXPORT
                        showExportSheet = true
                    },
                    icon = { Icon(Icons.Filled.Upload, contentDescription = null) },
                    label = { Text("Export") }
                )
            }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ElevatedCard(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CollageTemplates.all) { t ->
                            FilterChip(
                                selected = t.id == vm.selectedTemplate.value.id,
                                onClick = {
                                    vm.setTemplate(t)
                                    activeSlot = -1
                                    activeCameraSlot = -1
                                },
                                label = { Text(t.name) }
                            )
                        }
                    }

Spacer(Modifier.height(4.dp))
// Global camera controls (icons only) — applies to all slots
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 2.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    TopBarIconBox(
        modifier = Modifier.weight(1f),
        onClick = { vm.gridOn.value = !vm.gridOn.value },
        icon = Icons.Filled.GridOn,
        contentDescription = "Grid",
        tint = if (vm.gridOn.value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )

    TopBarIconBox(
        modifier = Modifier.weight(1f),
        onClick = {
            vm.flashModeUi.value = when (vm.flashModeUi.value) {
                0 -> 1
                1 -> 2
                else -> 0
            }
        },
        icon = when (vm.flashModeUi.value) {
            1 -> Icons.Filled.FlashOn
            2 -> Icons.Filled.FlashAuto
            else -> Icons.Filled.FlashOff
        },
        contentDescription = "Flash",
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )

    TopBarIconBox(
        modifier = Modifier.weight(1f),
        onClick = { vm.lensFacingUi.value = if (vm.lensFacingUi.value == 0) 1 else 0 },
        icon = Icons.Filled.Cameraswitch,
        contentDescription = "Flip",
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // Dual camera (front + back)
    TopBarIconBox(
        modifier = Modifier.weight(1f),
        onClick = { dualCameraPermission.launch(Manifest.permission.CAMERA) },
        icon = Icons.Filled.Dashboard,
        contentDescription = "Dual",
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
)


            .clip(RoundedCornerShape(22.dp)),

                template = vm.selectedTemplate.value,
                slotUris = vm.slotUris,
                slotTransforms = vm.slotTransforms,
                spacingPx = vm.spacingPx.value,
                cornerRadiusPx = vm.cornerRadiusPx.value,
                vm = vm,
                activeCameraSlot = activeCameraSlot,
                focusedSlotIndex = if (activeCameraSlot >= 0) activeCameraSlot else activeSlot,
                onSlotTap = { idx -> startCamera(idx) },
                onSlotLongPress = { idx ->
                    activeSlot = idx
                    galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onTransformChange = { i, tr -> vm.setSlotTransform(i, tr) },
                onCameraCaptured = { slotIdx, _ ->
                    lastDraftSlot = slotIdx
                    // Auto-advance: after a shot, move camera to the next slot (if any)
                    activeSlot = slotIdx
                    // Find the next empty slot (so we don't overwrite existing images/drafts)
                    val slotsCount = vm.selectedTemplate.value.slots.size
                    var next = slotIdx + 1
                    while (next < slotsCount && (vm.slotUris[next] != null || vm.draftCaptureUris[next] != null)) {
                        next++
                    }
                    if (next < slotsCount) {
                        startCamera(next)
                    } else {
                        // No more empty slots; keep action bar available for Edit
                        activeCameraSlot = -1
                    }
                },
                onCameraCancel = {
                    if (activeCameraSlot >= 0) {
                        vm.clearDraftCapture(activeCameraSlot)
                        if (lastDraftSlot == activeCameraSlot) lastDraftSlot = -1
                    }
                    activeCameraSlot = -1
                }
            )


    }



            }


            // ✅ Global camera action bar BELOW the slot (keeps slot UI clean)
// Always visible: stays in place for a calmer, more professional UX.
val hasActionContext = activeCameraSlot >= 0 || lastDraftSlot >= 0
val actionSlot = when {
    lastDraftSlot >= 0 -> lastDraftSlot
    activeCameraSlot >= 0 -> activeCameraSlot
    else -> 0
}
                val draft = vm.draftCaptureUris.getOrNull(actionSlot)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        enabled = hasActionContext,
                        onClick = {
                            if (actionSlot >= 0) vm.clearDraftCapture(actionSlot)
                            if (lastDraftSlot == actionSlot) lastDraftSlot = -1
                            activeSlot = -1
                            activeCameraSlot = -1
                        }
                    ) { Text("Cancel") }

                    FilledTonalIconButton(
                        onClick = {
                            if (activeCameraSlot < 0) return@FilledTonalIconButton
                            val activeDraft = vm.draftCaptureUris.getOrNull(activeCameraSlot)
                            if (activeDraft == null) vm.requestCapture(activeCameraSlot)
                            else vm.clearDraftCapture(activeCameraSlot)
                        },
                        modifier = Modifier.size(72.dp),
                        enabled = activeCameraSlot >= 0
                    ) {
                        Icon(
                            imageVector = if (activeCameraSlot >= 0 && vm.draftCaptureUris.getOrNull(activeCameraSlot) != null) Icons.Filled.Refresh else Icons.Filled.CameraAlt,
                            contentDescription = if (activeCameraSlot >= 0 && vm.draftCaptureUris.getOrNull(activeCameraSlot) != null) "Retake" else "Capture",
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Button(
                        onClick = {
                            val uri = draft ?: return@Button
                            vm.setSlotUri(actionSlot, uri)
                            activeSlot = actionSlot
                            // once committed, clear lastDraftSlot if it was this slot
                            if (lastDraftSlot == actionSlot) lastDraftSlot = -1
                            launchCrop(activeSlot, uri)
                        },
                        enabled = draft != null
                    ) { Text("Edit") }
                }
        }
    }

    if (showAdjustSheet) {
        ModalBottomSheet(onDismissRequest = { showAdjustSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Adjust layout", style = MaterialTheme.typography.titleMedium)
                        Text("Spacing", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = vm.spacingPx.value, onValueChange = { vm.spacingPx.value = it }, valueRange = 0f..64f)
                        Text("Corner radius", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = vm.cornerRadiusPx.value, onValueChange = { vm.cornerRadiusPx.value = it }, valueRange = 0f..96f)
                Button(onClick = { showAdjustSheet = false }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    
if (showExportSheet) {
    ModalBottomSheet(onDismissRequest = { showExportSheet = false }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Export", style = MaterialTheme.typography.titleMedium)
                        Text("Choose what to do next:", color = MaterialTheme.colorScheme.onSurfaceVariant)

            Button(
                onClick = {
                    val out = exportNow()
                    if (out != null) {
                        lastExportUri = out
                        scope.launch { snackbar.showSnackbar("Saved to Gallery") }
                        showExportSheet = false
                    } else {
                        scope.launch { snackbar.showSnackbar("Save failed") }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save to gallery") }

            OutlinedButton(
                onClick = {
                    val out = exportNow()
                    if (out != null) {
                        lastExportUri = out
                        shareTo("com.instagram.android", out)
                        showExportSheet = false
                    } else {
                        scope.launch { snackbar.showSnackbar("Export failed") }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Share on Instagram") }

            OutlinedButton(
                onClick = {
                    val out = exportNow()
                    if (out != null) {
                        lastExportUri = out
                        shareTo("com.whatsapp", out)
                        showExportSheet = false
                    } else {
                        scope.launch { snackbar.showSnackbar("Export failed") }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Share on WhatsApp") }

            Spacer(Modifier.height(4.dp))
        }
    }
}

    if (showDualCamera) {
        DualCameraCaptureDialog(
            onDismiss = { showDualCamera = false },
            onCaptured = { frontUri, backUri ->
                // Place into the next available empty slots (so we never overwrite existing images/drafts)
                val slotsCount = vm.selectedTemplate.value.slots.size
                val empty = (0 until slotsCount).filter { vm.slotUris[it] == null && vm.draftCaptureUris[it] == null }.toMutableList()

                val backSlot = if (empty.isNotEmpty()) empty.removeAt(0) else null
                val frontSlot = if (empty.isNotEmpty()) empty.removeAt(0) else null

                if (backSlot != null) vm.setSlotUri(backSlot, backUri)
                if (frontSlot != null) vm.setSlotUri(frontSlot, frontUri)

                // After dual capture, auto-start camera on next empty slot (if any)
                val nextSlot = empty.firstOrNull()
                if (nextSlot != null) {
                    startCamera(nextSlot)
                } else {
                    activeCameraSlot = -1
                }

                // Cache thumbs to refresh faster
                scope.launch {
                    ThumbnailLoader.loadThumbnail(context, backUri, 1024)?.let { vm.putCachedThumb(backUri, it) }
                    ThumbnailLoader.loadThumbnail(context, frontUri, 1024)?.let { vm.putCachedThumb(frontUri, it) }
                }
                imageRefreshTick++
                showDualCamera = false
                scope.launch { snackbar.showSnackbar("Captured both cameras") }
            }
        )
    }

}