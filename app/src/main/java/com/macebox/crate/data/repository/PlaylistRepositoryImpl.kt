package com.macebox.crate.data.repository

import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.data.api.CrateApiService
import com.macebox.crate.data.api.apiCall
import com.macebox.crate.data.api.dto.AddPlaylistItemRequest
import com.macebox.crate.data.api.dto.CreatePlaylistRequest
import com.macebox.crate.data.api.dto.PlaylistDto
import com.macebox.crate.data.db.dao.PlaylistDao
import com.macebox.crate.data.mapper.MediaItemJsonCodec
import com.macebox.crate.data.mapper.toDomain
import com.macebox.crate.data.mapper.toEntity
import com.macebox.crate.domain.model.Playlist
import com.macebox.crate.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl
    @Inject
    constructor(
        private val api: CrateApiService,
        private val dao: PlaylistDao,
        private val codec: MediaItemJsonCodec,
    ) : PlaylistRepository {
        override fun observeAll(): Flow<List<Playlist>> = dao.observeAll().map { rows -> rows.map { it.toDomain(codec) } }

        override fun observe(id: Long): Flow<Playlist?> = dao.observeWithItems(id).map { row -> row?.toDomain(codec) }

        override suspend fun refresh(): ApiResult<Unit> =
            apiCall {
                val playlists = api.listPlaylists()
                dao.upsertAll(playlists.map { it.toEntity() })
                playlists.forEach { playlist ->
                    playlist.items?.let { items ->
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
                val playlist = api.updatePlaylist(id, CreatePlaylistRequest(name))
                persistWithItems(playlist)
                playlist.toDomain()
            }

        override suspend fun delete(id: Long): ApiResult<Unit> =
            apiCall {
                api.deletePlaylist(id)
                dao.delete(id)
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
            dao.upsert(playlist.toEntity())
            dao.replacePlaylistItems(
                playlistId = playlist.id,
                mediaItemIds = playlist.items.orEmpty().map { it.id },
            )
        }
    }
