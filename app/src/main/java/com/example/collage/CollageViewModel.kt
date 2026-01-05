package com.example.collage

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.collection.LruCache
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel

class CollageViewModel : ViewModel() {

    val selectedTemplate = mutableStateOf(CollageTemplates.all.first { it.id == "hero_two" })
    val slotUris = mutableStateListOf<Uri?>()
    val slotTransforms = mutableStateListOf<SlotTransform>()

    // Per-slot draft capture (camera persistence)
    val draftCaptureUris = mutableStateListOf<Uri?>()
    // Per-slot capture trigger (increment to request a capture from the active CameraSlot)
    val captureRequests = mutableStateListOf<Int>()

// Global camera controls (apply to all slots)
// flashModeUi: 0=OFF, 1=AUTO, 2=ON
val flashModeUi = mutableStateOf(1)
// lensFacingUi: 0=BACK, 1=FRONT
val lensFacingUi = mutableStateOf(0)
val gridOn = mutableStateOf(true)

// Editor controls
    val spacingPx = mutableStateOf(14f)
    val cornerRadiusPx = mutableStateOf(26f)
    val backgroundColorArgb = mutableStateOf(Color.parseColor("#0B0F19")) // default dark

    // Recent exports (loaded from MediaStore + appended on export)
    val recentExports = mutableStateListOf<Uri>()

    private val thumbCache = LruCache<String, ImageBitmap>(90)

    init { setTemplate(selectedTemplate.value) }

    fun setTemplate(t: CollageTemplate) {
        selectedTemplate.value = t
        slotUris.clear()
        slotTransforms.clear()
        draftCaptureUris.clear()
        captureRequests.clear()
        repeat(t.slots.size) {
            slotUris.add(null)
            slotTransforms.add(SlotTransform())
            draftCaptureUris.add(null)
            captureRequests.add(0)
        }
    }

    fun setSlotUri(index: Int, uri: Uri) {
    if (index in 0 until slotUris.size) {
        // Invalidate thumbnail cache so edits (crop) reflect immediately.
        val prev = slotUris[index]
        if (prev != null) clearCachedThumb(prev)
        clearCachedThumb(uri)

        slotUris[index] = uri
        slotTransforms[index] = SlotTransform()
        clearDraftCapture(index)
    }

}

    fun clearSlot(index: Int) {
        if (index in 0 until slotUris.size) {
            slotUris[index] = null
            slotTransforms[index] = SlotTransform()
            clearDraftCapture(index)
        }
    }

    fun setSlotTransform(index: Int, transform: SlotTransform) {
        if (index in 0 until slotTransforms.size) slotTransforms[index] = transform
    }

    fun setDraftCapture(index: Int, uri: Uri?) {
        if (index in 0 until draftCaptureUris.size) draftCaptureUris[index] = uri
    }

    fun clearDraftCapture(index: Int) = setDraftCapture(index, null)

    fun requestCapture(index: Int) {
        if (index in 0 until captureRequests.size) {
            captureRequests[index] = captureRequests[index] + 1
        }
    }

    fun getCachedThumb(uri: Uri) = thumbCache.get(uri.toString())
    fun putCachedThumb(uri: Uri, bmp: ImageBitmap) { thumbCache.put(uri.toString(), bmp) }
    fun clearCachedThumb(uri: Uri) { thumbCache.remove(uri.toString()) }

    fun addRecentExport(uri: Uri) {
        // newest first
        recentExports.remove(uri)
        recentExports.add(0, uri)
        // cap
        while (recentExports.size > 24) recentExports.removeLast()
    }

    fun loadRecentExportsFromMediaStore(context: Context, limit: Int = 24) {
        // best-effort: load latest images saved by this app folder
        try {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.RELATIVE_PATH)
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%Pictures/AutoCollage%")
            val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, sort)
                ?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    recentExports.clear()
                    var count = 0
                    while (c.moveToNext() && count < limit) {
                        val id = c.getLong(idCol)
                        val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                        recentExports.add(uri)
                        count++
                    }
                }
        } catch (_: Exception) {
            // ignore
        }
    }
}
