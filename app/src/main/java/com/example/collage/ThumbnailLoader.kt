package com.example.collage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ThumbnailLoader {
    suspend fun loadThumbnail(context: Context, uri: Uri, maxSizePx: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= 28) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        val w = info.size.width
                        val h = info.size.height
                        val scale = maxSizePx.toFloat() / maxOf(w, h).toFloat()
                        val tw = (w * scale).toInt().coerceAtLeast(1)
                        val th = (h * scale).toInt().coerceAtLeast(1)
                        decoder.setTargetSize(tw, th)
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = false
                    }
                    bmp.asImageBitmap()
                } else {
                    @Suppress("DEPRECATION")
                    val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
                    input.use {
                        val full = android.graphics.BitmapFactory.decodeStream(it) ?: return@withContext null
                        val scale = maxSizePx.toFloat() / maxOf(full.width, full.height).toFloat()
                        val tw = (full.width * scale).toInt().coerceAtLeast(1)
                        val th = (full.height * scale).toInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(full, tw, th, true)
                        if (scaled != full) full.recycle()
                        scaled.asImageBitmap()
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
}
