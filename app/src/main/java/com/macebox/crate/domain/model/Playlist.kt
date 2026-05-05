package com.macebox.crate.domain.model

data class Playlist(
    val id: Long,
    val name: String,
    val userId: String?,
    val items: List<MediaItem>,
    val itemCount: Int = 0,
    val createdAt: String?,
    val updatedAt: String?,
)
