package com.example.collage

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.OutputStream
import kotlin.math.roundToInt

object CollageRenderer {

    fun renderAndSave(
        context: Context,
        template: CollageTemplate,
        slotUris: List<Uri?>,
        slotTransforms: List<SlotTransform>,
        spacingPx: Float,
        cornerRadiusPx: Float,
        outSizePx: Int = 2048,
        backgroundColor: Int = Color.WHITE
    ): Uri? {
        val bmp = renderBitmap(
            context, template, slotUris, slotTransforms,
            spacingPx, cornerRadiusPx,
            outSizePx, outSizePx, backgroundColor
        )
        return saveBitmapToGallery(context, bmp, "collage_${System.currentTimeMillis()}.jpg")
    }

    private fun renderBitmap(
        context: Context,
        template: CollageTemplate,
        slotUris: List<Uri?>,
        slotTransforms: List<SlotTransform>,
        spacingPx: Float,
        cornerRadiusPx: Float,
        widthPx: Int,
        heightPx: Int,
        backgroundColor: Int
    ): Bitmap {
        val out = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(backgroundColor)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
        val spacing = spacingPx.coerceAtLeast(0f)
        val radius = cornerRadiusPx.coerceAtLeast(0f)

        template.slots.forEachIndexed { idx, r ->
            val dst = normToRectF(r, widthPx, heightPx, spacing)
            val uri = slotUris.getOrNull(idx)
            val t = slotTransforms.getOrNull(idx) ?: SlotTransform()

            if (uri == null) {
                drawPlaceholderTile(canvas, dst, radius)
                return@forEachIndexed
            }

            val srcBitmap = decodeBitmap(context, uri) ?: run {
                drawPlaceholderTile(canvas, dst, radius)
                return@forEachIndexed
            }

            val saveCount = canvas.save()
            val path = Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) }
            canvas.clipPath(path)

            val srcRect = computeSrcRectWithTransform(
                srcW = srcBitmap.width,
                srcH = srcBitmap.height,
                dstW = dst.width(),
                dstH = dst.height(),
                transform = t
            )

            canvas.drawBitmap(srcBitmap, srcRect, dst, paint)
            canvas.restoreToCount(saveCount)
        }

        return out
    }

    private fun normToRectF(r: RectFNorm, w: Int, h: Int, spacing: Float): RectF {
        val left = r.x * w + spacing / 2f
        val top = r.y * h + spacing / 2f
        val right = (r.x + r.w) * w - spacing / 2f
        val bottom = (r.y + r.h) * h - spacing / 2f
        return RectF(left, top, right, bottom)
    }

    private fun computeSrcRectWithTransform(
        srcW: Int,
        srcH: Int,
        dstW: Float,
        dstH: Float,
        transform: SlotTransform
    ): Rect {
        val srcAspect = srcW.toFloat() / srcH.toFloat()
        val dstAspect = dstW / dstH

        val base: Rect = if (srcAspect > dstAspect) {
            val newW = (srcH * dstAspect).roundToInt()
            val xOff = (srcW - newW) / 2
            Rect(xOff, 0, xOff + newW, srcH)
        } else {
            val newH = (srcW / dstAspect).roundToInt()
            val yOff = (srcH - newH) / 2
            Rect(0, yOff, srcW, yOff + newH)
        }

        val scale = transform.scale.coerceIn(1f, 4f)
        val baseW = base.width()
        val baseH = base.height()

        val zoomW = (baseW / scale).roundToInt().coerceAtLeast(1)
        val zoomH = (baseH / scale).roundToInt().coerceAtLeast(1)

        val maxDx = (baseW - zoomW) / 2
        val maxDy = (baseH - zoomH) / 2

        val dx = (transform.offsetX.coerceIn(-1f, 1f) * maxDx).roundToInt()
        val dy = (transform.offsetY.coerceIn(-1f, 1f) * maxDy).roundToInt()

        val cx = base.centerX() + dx
        val cy = base.centerY() + dy

        var left = cx - zoomW / 2
        var top = cy - zoomH / 2
        var right = left + zoomW
        var bottom = top + zoomH

        if (left < base.left) { right += (base.left - left); left = base.left }
        if (top < base.top) { bottom += (base.top - top); top = base.top }
        if (right > base.right) { left -= (right - base.right); right = base.right }
        if (bottom > base.bottom) { top -= (bottom - base.bottom); bottom = base.bottom }

        return Rect(left, top, right, bottom)
    }

    private fun drawPlaceholderTile(canvas: Canvas, dst: RectF, radius: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEFEFEF.toInt() }
        canvas.drawRoundRect(dst, radius, radius, paint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF9A9A9A.toInt()
            textAlign = Paint.Align.CENTER
            textSize = kotlin.math.max(26f, dst.width() * 0.06f)
        }
        canvas.drawText("+", dst.centerX(), dst.centerY() + textPaint.textSize / 3f, textPaint)
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            } else {
                @Suppress("DEPRECATION")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }
        } catch (_: Exception) { null }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AutoCollage")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        var out: OutputStream? = null
        try {
            out = resolver.openOutputStream(uri) ?: return null
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            out.flush()
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            return null
        } finally {
            try { out?.close() } catch (_: Exception) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }
}
