package com.example.collage

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.yalantis.ucrop.UCrop
import java.io.File

object UCropHelper {

    fun buildIntent(context: Context, source: Uri): Intent {
        val destFile = File(context.cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
        val destUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            destFile
        )

        val options = UCrop.Options().apply {
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(95)
            setFreeStyleCropEnabled(true)
            setHideBottomControls(false)
        }

        return UCrop.of(source, destUri)
            .withOptions(options)
            .getIntent(context)
            .apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
    }
}
