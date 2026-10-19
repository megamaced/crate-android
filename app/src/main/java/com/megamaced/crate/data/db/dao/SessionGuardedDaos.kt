package com.megamaced.crate.data.db.dao

import com.megamaced.crate.data.auth.SessionGeneration
import com.megamaced.crate.data.db.entity.MediaItemEntity
import com.megamaced.crate.data.db.entity.PlaylistEntity
import com.megamaced.crate.data.db.entity.PlaylistItemCrossRef

/**
 * The collection DAO as the app is given it: reads pass straight through, and
 * every write goes through [SessionGeneration.write], so a response from a
 * session that has since ended can't land in the next one's cache.
 */
class SessionGuardedMediaItemDao(
    private val delegate: MediaItemDao,
) : MediaItemDao by delegate {
    override suspend fun upsert(item: MediaItemEntity) = SessionGeneration.write { delegate.upsert(item) }

    override suspend fun upsertAll(items: List<MediaItemEntity>) = SessionGeneration.write { delegate.upsertAll(items) }

    override suspend fun delete(id: Long) = SessionGeneration.write { delegate.delete(id) }

    override suspend fun deleteByIds(ids: List<Long>) = SessionGeneration.write { delegate.deleteByIds(ids) }

    override suspend fun deleteByCategories(
        categories: List<String>,
        ownerId: String?,
    ) = SessionGeneration.write { delegate.deleteByCategories(categories, ownerId) }

    override suspend fun deleteAll() = SessionGeneration.write { delegate.deleteAll() }
}

/** The playlist DAO as the app is given it; see [SessionGuardedMediaItemDao]. */
class SessionGuardedPlaylistDao(
    private val delegate: PlaylistDao,
) : PlaylistDao by delegate {
    override suspend fun upsert(playlist: PlaylistEntity) = SessionGeneration.write { delegate.upsert(playlist) }

    override suspend fun upsertAll(playlists: List<PlaylistEntity>) = SessionGeneration.write { delegate.upsertAll(playlists) }

    override suspend fun delete(id: Long) = SessionGeneration.write { delegate.delete(id) }

    override suspend fun deleteAll() = SessionGeneration.write { delegate.deleteAll() }

    override suspend fun deleteByIds(ids: List<Long>) = SessionGeneration.write { delegate.deleteByIds(ids) }

    override suspend fun replaceAll(
        playlists: List<PlaylistEntity>,
        ownerId: String?,
    ) = SessionGeneration.write { delegate.replaceAll(playlists, ownerId) }

    override suspend fun upsertCrossRefs(refs: List<PlaylistItemCrossRef>) = SessionGeneration.write { delegate.upsertCrossRefs(refs) }

    override suspend fun clearPlaylistItems(playlistId: Long) = SessionGeneration.write { delegate.clearPlaylistItems(playlistId) }

    override suspend fun replacePlaylistItems(
        playlistId: Long,
        mediaItemIds: List<Long>,
    ) = SessionGeneration.write { delegate.replacePlaylistItems(playlistId, mediaItemIds) }
}
