package com.macebox.crate.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["updatedAt"]),
        Index(value = ["category"]),
        Index(value = ["status"]),
    ],
)
data class MediaItemEntity(
    @PrimaryKey val id: Long,
    val userId: String? = null,
    val title: String,
    val artist: String? = null,
    val format: String? = null,
    val year: Int? = null,
    val barcode: String? = null,
    val notes: String? = null,
    val status: String,
    val category: String,
    val discogsId: String? = null,
    val artworkPath: String? = null,
    val label: String? = null,
    val country: String? = null,
    val genres: String? = null,
    /** JSON-encoded `List<TrackDto>` */
    val tracklistJson: String? = null,
    val pressingNotes: String? = null,
    val discogsArtistId: String? = null,
    val artistBio: String? = null,
    /** JSON-encoded `List<ArtistMemberDto>` */
    val artistMembersJson: String? = null,
    val marketValue: Double? = null,
    val marketValueLoose: Double? = null,
    val marketValueNew: Double? = null,
    val marketValueCurrency: String? = null,
    val marketValueFetchedAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
