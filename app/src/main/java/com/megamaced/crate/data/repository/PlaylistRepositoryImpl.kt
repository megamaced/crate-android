package com.megamaced.crate.data.repository

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.CrateApiService
import com.megamaced.crate.data.api.apiCall
import com.megamaced.crate.data.api.dto.AddPlaylistItemRequest
import com.megamaced.crate.data.api.dto.CreatePlaylistRequest
import com.megamaced.crate.data.api.dto.PlaylistDto
import com.megamaced.crate.data.db.dao.MediaItemDao
import com.megamaced.crate.data.db.dao.PlaylistDao
import com.megamaced.crate.data.mapper.MediaItemJsonCodec
import com.megamaced.crate.data.mapper.toDomain
import com.megamaced.crate.data.mapper.toEntity
import com.megamaced.crate.data.prefs.AccountPrefs
import com.megamaced.crate.domain.model.Playlist
import com.megamaced.crate.domain.repository.PlaylistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl
    @Inject
    constructor(
        private val api: CrateApiService,
        private val dao: PlaylistDao,
        private val mediaItemDao: MediaItemDao,
        private val accountPrefs: AccountPrefs,
        private val codec: MediaItemJsonCodec,
    ) : PlaylistRepository {
        // Scoped to the signed-in user: opening a shared playlist caches the
        // owner's row in the same table, and that row is not one of yours.
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun observeAll(): Flow<List<Playlist>> =
            accountPrefs.currentUserIdFlow.flatMapLatest { ownerId ->
                dao.observeAll(ownerId).map { rows -> rows.map { it.toDomain(codec) } }
            }

        override fun observe(id: Long): Flow<Playlist?> = dao.observeWithItems(id).map { row -> row?.toDomain(codec) }

        override suspend fun refresh(): ApiResult<Unit> =
            apiCall {
                val playlists = api.listPlaylists()
                // Reconcile rather than upsert: this response is the whole
                // truth about which playlists exist, so anything local and
                // absent from it was deleted elsewhere.
                dao.replaceAll(playlists.map { it.toEntity() }, accountPrefs.currentUserId())
                playlists.forEach { playlist ->
                    playlist.items?.let { items ->
                        mediaItemDao.upsertAll(items.map { it.toEntity(codec) })
                        dao.replacePlaylistItems(
                            playlistId = playlist.id,
                            mediaItemIds = items.map { it.id },
                        )
                    }
                }
            }

        override suspend fun refresh(id: Long): ApiResult<Playlist> =
            apiCall {
                val playlist = api.getPlaylist(id)
                persistWithItems(playlist)
                playlist.toDomain()
            }

        override suspend fun create(name: String): ApiResult<Playlist> =
            apiCall {
                val playlist = api.createPlaylist(CreatePlaylistRequest(name))
                persistWithItems(playlist)
                playlist.toDomain()
            }

        override suspend fun rename(
            id: Long,
            name: String,
        ): ApiResult<Playlist> =
            apiCall {
                // PlaylistController::update() writes the description
                // unconditionally and it isn't cached locally, so read the
                // stored one first and send it back — otherwise renaming from
                // the phone silently clears a description entered on the web.
                val description = api.getPlaylist(id).description
                val playlist = api.updatePlaylist(id, CreatePlaylistRequest(name, description))
                persistWithItems(playlist)
                playlist.toDomain()
            }

        override suspend fun delete(id: Long): ApiResult<Unit> {
            val result = apiCall { api.deletePlaylist(id) }
            // A playlist already deleted elsewhere answers 404. The local row is
            // then the stale copy, and refusing to drop it strands it on the
            // phone: it can't be opened and can't be deleted. Treat it as done.
            val gone = result is ApiResult.HttpError && result.code == 404
            if (result is ApiResult.Success || gone) dao.delete(id)
            return if (gone) ApiResult.Success(Unit) else result
        }

        override suspend fun addItem(
            playlistId: Long,
            mediaItemId: Long,
        ): ApiResult<Playlist> =
            apiCall {
                val playlist = api.addPlaylistItem(playlistId, AddPlaylistItemRequest(mediaItemId))
                persistWithItems(playlist)
                playlist.toDomain()
            }

        override suspend fun removeItem(
            playlistId: Long,
            mediaItemId: Long,
        ): ApiResult<Playlist> =
            apiCall {
                val playlist = api.removePlaylistItem(playlistId, mediaItemId)
                persistWithItems(playlist)
                playlist.toDomain()
            }

        private suspend fun persistWithItems(playlist: PlaylistDto) {
            val items = playlist.items.orEmpty()
            mediaItemDao.upsertAll(items.map { it.toEntity(codec) })
            dao.upsert(playlist.toEntity())
            dao.replacePlaylistItems(
                playlistId = playlist.id,
                mediaItemIds = items.map { it.id },
            )
        }
    }
