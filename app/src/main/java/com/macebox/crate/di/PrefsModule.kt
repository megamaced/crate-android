package com.macebox.crate.di

import com.macebox.crate.data.prefs.CollectionPrefs
import com.macebox.crate.data.prefs.UserPreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PrefsModule {
    @Binds
    @Singleton
    abstract fun bindCollectionPrefs(impl: UserPreferences): CollectionPrefs
}
