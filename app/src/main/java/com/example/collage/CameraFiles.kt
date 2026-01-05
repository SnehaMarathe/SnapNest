package com.example.collage

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Small helper for CameraX captures.
 * Uses the same FileProvider authority as UCropHelper: <package>.fileprovider
 */
object CameraFiles {

    fun createTempJpeg(context: Context): File {
        return File(context.cacheDir, "cap_${System.currentTimeMillis()}.jpg")
    }

    fun toContentUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
