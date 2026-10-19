package com.megamaced.crate.data.repository

import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.CrateApiService
import com.megamaced.crate.data.api.dto.HiddenCategoriesRequest
import com.megamaced.crate.data.api.dto.HiddenCategoriesResponse
import com.megamaced.crate.data.api.dto.MeDto
import com.megamaced.crate.data.auth.SessionGeneration
import com.megamaced.crate.data.prefs.ProfilePrefs
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.testing.unimplemented
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `/me` and the profile setters mirror the server's answer into the local
 * profile. An answer that arrives after its account signed out used to be
 * written there anyway — naming the previous user as the next one's owner,
 * which every "my collection" query is scoped by.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryImplTest {
    private val me = CompletableDeferred<MeDto>()
    private val hiddenSaved = CompletableDeferred<HiddenCategoriesResponse>()
    private val prefs = RecordingProfilePrefs()

    private val repo =
        SettingsRepositoryImpl(
            object : CrateApiService by unimplemented<CrateApiService>() {
                override suspend fun getMe(): MeDto = me.await()

                override suspend fun setHiddenCategories(body: HiddenCategoriesRequest) = hiddenSaved.await()
            },
            prefs,
        )

    @Test
    fun `a profile that arrives after sign-out is not mirrored for the next account`() =
        runTest {
            val call = async { repo.getMe() }
            advanceUntilIdle()

            SessionGeneration.advance()
            me.complete(meDto("alice"))

            assertEquals(ApiResult.Unauthorised, call.await())
            assertNull(prefs.userId)
        }

    @Test
    fun `a profile within its own session is mirrored`() =
        runTest {
            me.complete(meDto("alice"))

            assertTrue(repo.getMe() is ApiResult.Success)
            assertEquals("alice", prefs.userId)
        }

    @Test
    fun `a hidden-category change confirmed after sign-out is not mirrored`() =
        runTest {
            val call = async { repo.setHiddenCategories(setOf(Category.Films)) }
            advanceUntilIdle()

            SessionGeneration.advance()
            hiddenSaved.complete(HiddenCategoriesResponse(listOf("film")))

            assertEquals(ApiResult.Unauthorised, call.await())
            assertEquals(emptySet<Category>(), prefs.hidden.value)
        }
}

private class RecordingProfilePrefs : ProfilePrefs {
    var userId: String? = null
    val hidden = MutableStateFlow<Set<Category>>(emptySet())
    private val recommendations = MutableStateFlow(false)

    override val currentUserIdFlow: Flow<String?> = MutableStateFlow(null)

    override suspend fun currentUserId(): String? = userId

    override suspend fun setCurrentUserId(userId: String?) {
        this.userId = userId
    }

    override val hiddenCategoriesFlow: Flow<Set<Category>> = hidden

    override suspend fun setHiddenCategories(categories: Set<Category>) {
        hidden.value = categories
    }

    override val onlineRecommendationsFlow: Flow<Boolean> = recommendations

    override suspend fun setOnlineRecommendations(enabled: Boolean) {
        recommendations.value = enabled
    }
}
