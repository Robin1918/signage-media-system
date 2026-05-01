package com.signage.player.utils

import android.content.Context
import com.signage.player.model.MediaFile
import com.signage.player.model.MediaType
import java.io.File

object MediaFileManager {

    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v")
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic")

    fun getMediaDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "SignageMedia")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getMediaFiles(context: Context): List<MediaFile> {
        val dir = getMediaDirectory(context)
        return dir.listFiles()
            ?.filter { it.isFile && getMediaType(it) != null }
            ?.map { file ->
                MediaFile(
                    name = file.name,
                    path = file.absolutePath,
                    type = getMediaType(file)!!,
                    size = file.length(),
                    dateAdded = file.lastModified()
                )
            }
            ?.sortedBy { it.dateAdded }
            ?: emptyList()
    }

    private fun getMediaType(file: File): MediaType? {
        val ext = file.extension.lowercase()
        return when {
            VIDEO_EXTENSIONS.contains(ext) -> MediaType.VIDEO
            IMAGE_EXTENSIONS.contains(ext) -> MediaType.IMAGE
            else -> null
        }
    }

    fun deleteFile(context: Context, fileName: String): Boolean {
        val file = File(getMediaDirectory(context), fileName)
        return file.delete()
    }
}
