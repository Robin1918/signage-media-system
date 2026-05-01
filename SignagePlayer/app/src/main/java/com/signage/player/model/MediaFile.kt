package com.signage.player.model

enum class MediaType { VIDEO, IMAGE }

data class MediaFile(
    val name: String,
    val path: String,
    val type: MediaType,
    val dateAdded: Long
)
