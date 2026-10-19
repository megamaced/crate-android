package com.megamaced.crate.ui.screen.search

import com.megamaced.crate.data.api.CrateApiService
import com.megamaced.crate.data.api.dto.DiscogsSearchResultDto
import com.megamaced.crate.data.api.dto.OpenLibraryResultDto
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.repository.MediaRepository
import com.megamaced.crate.domain.repository.SettingsRepository
import com.megamaced.crate.testing.unimplemented
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Switching provider cancels the running search, and cancellation is rethrown
 * past the code that used to clear the spinner — so the new provider showed a
 * spinner with nothing running, hiding the hint that offers Search.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `changing provider mid-search leaves the new provider idle`() =
        runTest(dispatcher) {
            val vm = viewModel(object : CrateApiService by unimplemented<CrateApiService>() {
                override suspend fun discogsSearch(query: String): List<DiscogsSearchResultDto> = awaitCancellation()
            })
            backgroundScope.launch { vm.uiState.collect {} }
            vm.onQueryChange("Spiderland")
            vm.runExternalSearch()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isExternalLoading)

            vm.selectCategory(Category.Books)
            advanceUntilIdle()

            assertEquals(Category.Books, vm.uiState.value.externalCategory)
            assertFalse(vm.uiState.value.isExternalLoading)
        }

    @Test
    fun `a replaced search cannot clear the spinner of the one that replaced it`() =
        runTest(dispatcher) {
            val second = CompletableDeferred<List<OpenLibraryResultDto>>()
            var calls = 0
            val vm = viewModel(object : CrateApiService by unimplemented<CrateApiService>() {
                override suspend fun openLibrarySearch(query: String): List<OpenLibraryResultDto> =
                    if (++calls == 1) awaitCancellation() else second.await()
            })
            backgroundScope.launch { vm.uiState.collect {} }
            vm.selectCategory(Category.Books)
            vm.onQueryChange("Dune")
            vm.runExternalSearch()
            advanceUntilIdle()

            // The second search cancels the first, whose cleanup then runs.
            vm.runExternalSearch()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isExternalLoading)

            second.complete(emptyList())
            advanceUntilIdle()
            assertFalse(vm.uiState.value.isExternalLoading)
            assertTrue(vm.uiState.value.externalHasSearched)
        }

    private fun viewModel(api: CrateApiService) =
        SearchViewModel(
            api,
            object : MediaRepository by unimplemented<MediaRepository>() {
                override fun observeAll() = flowOf(emptyList<com.megamaced.crate.domain.model.MediaItem>())
            },
            object : SettingsRepository by unimplemented<SettingsRepository>() {
                override val hiddenCategoriesFlow = flowOf(emptySet<Category>())
            },
        )
}
