package com.megamaced.crate.data.repository

import com.megamaced.crate.data.api.dto.MeDto
import com.megamaced.crate.data.db.dao.MediaItemDao
import com.megamaced.crate.data.db.dao.PlaylistDao
import com.megamaced.crate.data.db.entity.MediaItemEntity
import com.megamaced.crate.data.db.entity.PlaylistEntity
import com.megamaced.crate.data.db.entity.PlaylistItemCrossRef
import com.megamaced.crate.data.db.entity.PlaylistItemWithMedia
import com.megamaced.crate.data.db.entity.PlaylistWithItems
import com.megamaced.crate.data.prefs.AccountPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Minimal [MeDto] — only the user id matters to the repositories under test. */
fun meDto(userId: String) = MeDto(userId = userId, displayName = userId)

/**
 * In-memory [AccountPrefs]. Starts with no known user, which is the fail-open
 * state the collection queries have to handle on a fresh install.
 */
class FakeAccountPrefs(
    initial: String? = null,
) : AccountPrefs {
    private val userId = MutableStateFlow(initial)

    override val currentUserIdFlow: Flow<String?> = userId

    override suspend fun currentUserId(): String? = userId.value

    override suspend fun setCurrentUserId(userId: String?) {
        this.userId.value = userId
    }
}

/**
 * In-memory [PlaylistDao]. The cross-refs are kept as a flat list, like the
 * table, so ordering is deliberately *not* insertion order — a mapper that
 * forgets to sort by position fails here rather than passing by luck.
 */
class FakePlaylistDao : PlaylistDao {
    private val playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    private val crossRefs = MutableStateFlow<List<PlaylistItemCrossRef>>(emptyList())
    private val mediaItems = mutableMapOf<Long, MediaItemEntity>()

    fun seedPlaylists(rows: List<PlaylistEntity>) {
        playlists.value = rows
    }

    fun seedMediaItems(rows: List<MediaItemEntity>) {
        rows.forEach { mediaItems[it.id] = it }
    }

    fun snapshot(): List<PlaylistEntity> = playlists.value

    fun crossRefsFor(playlistId: Long): List<PlaylistItemCrossRef> = crossRefs.value.filter { it.playlistId == playlistId }

    override fun observeAll(): Flow<List<PlaylistWithItems>> = playlists.map { rows -> rows.map { withItems(it) } }

    override fun observeWithItems(id: Long): Flow<PlaylistWithItems?> =
        playlists.map { rows -> rows.firstOrNull { it.id == id }?.let(::withItems) }

    override suspend fun upsert(playlist: PlaylistEntity) {
        playlists.value = playlists.value.filterNot { it.id == playlist.id } + playlist
    }

    override suspend fun upsertAll(playlists: List<PlaylistEntity>) {
        playlists.forEach { upsert(it) }
    }

    override suspend fun delete(id: Long) {
        playlists.value = playlists.value.filterNot { it.id == id }
        crossRefs.value = crossRefs.value.filterNot { it.playlistId == id }
    }

    override suspend fun deleteAll() {
        playlists.value = emptyList()
        crossRefs.value = emptyList()
    }

    override suspend fun allIds(): List<Long> = playlists.value.map { it.id }

    override suspend fun deleteByIds(ids: List<Long>) {
        playlists.value = playlists.value.filterNot { it.id in ids }
        crossRefs.value = crossRefs.value.filterNot { it.playlistId in ids }
    }

    override suspend fun upsertCrossRefs(refs: List<PlaylistItemCrossRef>) {
        val keys = refs.map { it.playlistId to it.mediaItemId }.toSet()
        crossRefs.value = crossRefs.value.filterNot { (it.playlistId to it.mediaItemId) in keys } + refs
    }

    override suspend fun clearPlaylistItems(playlistId: Long) {
        crossRefs.value = crossRefs.value.filterNot { it.playlistId == playlistId }
    }

    // Room returns junction rows in index order, i.e. by media-item id, not by
    // the order they were written. Reversing here reproduces that.
    private fun withItems(playlist: PlaylistEntity) =
        PlaylistWithItems(
            playlist = playlist,
            itemRefs =
                crossRefs.value
                    .filter { it.playlistId == playlist.id }
                    .sortedByDescending { it.mediaItemId }
                    .map { PlaylistItemWithMedia(ref = it, item = mediaItems[it.mediaItemId]) },
        )
}

/**
 * In-memory [com.megamaced.crate.data.db.dao.MediaItemDao]. Mirrors the SQL
 * predicates, including the owner scoping the collection reads apply.
 */
class FakeMediaItemDao : MediaItemDao {
    private val rows = MutableStateFlow<List<MediaItemEntity>>(emptyList())

    fun seed(items: List<MediaItemEntity>) {
        rows.value = items
    }

    fun snapshot(): List<MediaItemEntity> = rows.value

    override fun observeAll(ownerId: String?): Flow<List<MediaItemEntity>> = rows.map { list -> list.filter { it.ownedBy(ownerId) } }

    override fun observeByCategory(
        category: String,
        status: String?,
        ownerId: String?,
    ): Flow<List<MediaItemEntity>> =
        rows.map { list ->
            list.filter {
                it.category == category && (status == null || it.status == status) && it.ownedBy(ownerId)
            }
        }

    override fun observe(id: Long): Flow<MediaItemEntity?> = rows.map { it.firstOrNull { row -> row.id == id } }

    override suspend fun get(id: Long): MediaItemEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun upsert(item: MediaItemEntity) {
        rows.value = rows.value.filterNot { it.id == item.id } + item
    }

    override suspend fun upsertAll(items: List<MediaItemEntity>) {
        items.forEach { upsert(it) }
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }

    override suspend fun foreignIds(ownerId: String): List<Long> =
        rows.value.filter { it.userId != null && it.userId != ownerId }.map { it.id }

    override suspend fun deleteByCategories(
        categories: List<String>,
        ownerId: String?,
    ) {
        rows.value = rows.value.filterNot { it.category in categories && it.ownedBy(ownerId) }
    }

    override suspend fun countOwnedBy(ownerId: String): Int = ownedBy(ownerId).size

    override suspend fun idsOwnedBy(ownerId: String): List<Long> = ownedBy(ownerId).map { it.id }

    override suspend fun deleteByIds(ids: List<Long>) {
        rows.value = rows.value.filterNot { it.id in ids }
    }

    private fun ownedBy(ownerId: String) = rows.value.filter { it.userId == null || it.userId == ownerId }
}

private fun MediaItemEntity.ownedBy(ownerId: String?) = ownerId == null || userId == null || userId == ownerId
