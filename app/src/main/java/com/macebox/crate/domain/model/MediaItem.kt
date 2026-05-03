package com.macebox.crate.domain.model

enum class Category(
    val apiValue: String,
    val label: String,
) {
    Music("music", "Music"),
    Films("film", "Films"),
    Books("book", "Books"),
    Games("game", "Games"),
    Comics("comic", "Comics"),
    ;

    companion object {
        fun fromApi(value: String?): Category? = entries.firstOrNull { it.apiValue == value }
    }
}

enum class Status(
    val apiValue: String,
) {
    Owned("owned"),
    Wanted("wanted"),
    ;

    companion object {
        fun fromApi(value: String?): Status? = entries.firstOrNull { it.apiValue == value }
    }
}

data class Track(
    val position: String?,
    val title: String?,
    val duration: String?,
)

data class ArtistMember(
    val name: String,
    val active: Boolean?,
)

data class MarketValue(
    val currency: String?,
    val main: Double?,
    val loose: Double?,
    val new: Double?,
    val fetchedAt: String?,
) {
    val isPresent: Boolean get() = main != null || loose != null || new != null
}

data class MediaItem(
    val id: Long,
    val userId: String?,
    val title: String,
    val artist: String?,
    val format: String?,
    val year: Int?,
    val barcode: String?,
    val notes: String?,
    val status: Status,
    val category: Category,
    val discogsId: String?,
    val artworkPath: String?,
    val label: String?,
    val country: String?,
    val genres: String?,
    val tracklist: List<Track>,
    val pressingNotes: String?,
    val discogsArtistId: String?,
    val artistBio: String?,
    val artistMembers: List<ArtistMember>,
    val marketValue: MarketValue,
    val createdAt: String?,
    val updatedAt: String?,
)

/**
 * Fields used when creating/updating a media item. Required vs optional
 * mirrors the API: `title`, `artist`, `format` are required; `status` and
 * `category` default server-side to `owned` / `music`.
 */
data class MediaItemDraft(
    val title: String,
    val artist: String,
    val format: String,
    val year: Int? = null,
    val barcode: String? = null,
    val notes: String? = null,
    val status: Status? = null,
    val category: Category? = null,
    val discogsId: String? = null,
    val artworkPath: String? = null,
    val label: String? = null,
    val country: String? = null,
)
