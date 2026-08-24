package com.megamaced.crate.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/**
 * The dispatcher the view models push CPU work onto — bucketing, filtering,
 * sorting, grouping and similarity ranking over a whole category.
 *
 * It is injected rather than referenced directly so tests can substitute a
 * dispatcher tied to their own scheduler. `Dispatchers.setMain` only controls
 * the main dispatcher, so a hardcoded `Dispatchers.Default` inside a `flowOn`
 * escapes the test scheduler entirely and makes emission ordering
 * non-deterministic.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
