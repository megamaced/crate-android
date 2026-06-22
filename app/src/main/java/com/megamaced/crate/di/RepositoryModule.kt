package com.megamaced.crate.di

import com.megamaced.crate.data.auth.CurrentSession
import com.megamaced.crate.data.auth.TokenStoreCurrentSession
import com.megamaced.crate.data.mapper.MediaItemJsonCodec
import com.megamaced.crate.data.repository.EnrichmentRepositoryImpl
import com.megamaced.crate.data.repository.HomeRepositoryImpl
import com.megamaced.crate.data.repository.MediaRepositoryImpl
import com.megamaced.crate.data.repository.PlaylistRepositoryImpl
import com.megamaced.crate.data.repository.SettingsRepositoryImpl
import com.megamaced.crate.data.repository.ShareRepositoryImpl
import com.megamaced.crate.domain.repository.EnrichmentRepository
import com.megamaced.crate.domain.repository.HomeRepository
import com.megamaced.crate.domain.repository.MediaRepository
import com.megamaced.crate.domain.repository.PlaylistRepository
import com.megamaced.crate.domain.repository.SettingsRepository
import com.megamaced.crate.domain.repository.ShareRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindHomeRepository(impl: HomeRepositoryImpl): HomeRepository

    @Binds
    @Singleton
    abstract fun bindEnrichmentRepository(impl: EnrichmentRepositoryImpl): EnrichmentRepository

    @Binds
    @Singleton
    abstract fun bindShareRepository(impl: ShareRepositoryImpl): ShareRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindCurrentSession(impl: TokenStoreCurrentSession): CurrentSession

    companion object {
        @Provides
        @Singleton
        fun provideMediaItemJsonCodec(json: Json): MediaItemJsonCodec = MediaItemJsonCodec(json)
    }
}
