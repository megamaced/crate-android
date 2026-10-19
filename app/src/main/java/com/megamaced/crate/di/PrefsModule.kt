package com.megamaced.crate.di

import com.megamaced.crate.data.prefs.AccountPrefs
import com.megamaced.crate.data.prefs.CollectionPrefs
import com.megamaced.crate.data.prefs.CreateReceipts
import com.megamaced.crate.data.prefs.DataStoreCreateReceipts
import com.megamaced.crate.data.prefs.FullSweepPrefs
import com.megamaced.crate.data.prefs.ProfilePrefs
import com.megamaced.crate.data.prefs.SyncStatePrefs
import com.megamaced.crate.data.prefs.UserPreferences
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

    @Binds
    @Singleton
    abstract fun bindAccountPrefs(impl: UserPreferences): AccountPrefs

    @Binds
    @Singleton
    abstract fun bindProfilePrefs(impl: UserPreferences): ProfilePrefs

    @Binds
    @Singleton
    abstract fun bindFullSweepPrefs(impl: UserPreferences): FullSweepPrefs

    @Binds
    @Singleton
    abstract fun bindSyncStatePrefs(impl: UserPreferences): SyncStatePrefs

    @Binds
    @Singleton
    abstract fun bindCreateReceipts(impl: DataStoreCreateReceipts): CreateReceipts
}
