package com.signage.player.utils

import android.content.Context
import com.signage.player.model.MediaFile
import com.signage.player.model.MediaType
import java.io.File

object MediaFileManager {

    private val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "wmv", "webm", "3gp", "m4v")
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

    fun getMediaDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "SignageMedia")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getMediaFiles(context: Context): List<MediaFile> {
        val dir = getMediaDirectory(context)
        return dir.listFiles()
            ?.filter { it.isFile && getType(it) != null }
            ?.map { MediaFile(it.name, it.absolutePath, getType(it)!!, it.lastModified()) }
            ?.sortedBy { it.dateAdded }
            ?: emptyList()
    }

    private fun getType(file: File): MediaType? {
        val ext = file.extension.lowercase()
        return when {
            VIDEO_EXT.contains(ext) -> MediaType.VIDEO
            IMAGE_EXT.contains(ext) -> MediaType.IMAGE
            else -> null
        }
    }
}
