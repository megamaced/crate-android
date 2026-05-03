package com.macebox.crate.di

import com.macebox.crate.data.mapper.MediaItemJsonCodec
import com.macebox.crate.data.repository.EnrichmentRepositoryImpl
import com.macebox.crate.data.repository.HomeRepositoryImpl
import com.macebox.crate.data.repository.MediaRepositoryImpl
import com.macebox.crate.data.repository.PlaylistRepositoryImpl
import com.macebox.crate.data.repository.SettingsRepositoryImpl
import com.macebox.crate.data.repository.ShareRepositoryImpl
import com.macebox.crate.domain.repository.EnrichmentRepository
import com.macebox.crate.domain.repository.HomeRepository
import com.macebox.crate.domain.repository.MediaRepository
import com.macebox.crate.domain.repository.PlaylistRepository
import com.macebox.crate.domain.repository.SettingsRepository
import com.macebox.crate.domain.repository.ShareRepository
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

    companion object {
        @Provides
        @Singleton
        fun provideMediaItemJsonCodec(json: Json): MediaItemJsonCodec = MediaItemJsonCodec(json)
    }
}
