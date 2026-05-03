package com.macebox.crate.data.repository

import app.cash.turbine.test
import com.macebox.crate.data.api.ApiResult
import com.macebox.crate.data.api.dto.MediaItemDto
import com.macebox.crate.data.api.dto.PaginatedMediaDto
import com.macebox.crate.data.db.dao.MediaItemDao
import com.macebox.crate.data.db.entity.MediaItemEntity
import com.macebox.crate.data.mapper.MediaItemJsonCodec
import com.macebox.crate.domain.model.Category
import com.macebox.crate.domain.model.MediaItemDraft
import com.macebox.crate.domain.model.Status
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRepositoryImplTest {
    private val codec = MediaItemJsonCodec(Json)
    private val dao = FakeMediaItemDao()
    private val api = FakeCrateApiService()
    private val repo = MediaRepositoryImpl(api, dao, codec)

    @Test
    fun `refresh writes API page into DAO and surfaces total`() =
        runTest {
            api.nextPage =
                PaginatedMediaDto(
                    items =
                        listOf(
                            mediaDto(1, "OK Computer", category = "music"),
                            mediaDto(2, "Kid A", category = "music"),
                        ),
                    total = 2,
                    limit = 50,
                    offset = 0,
                )

            val result = repo.refresh(category = Category.Music)

            assertTrue(result is ApiResult.Success)
            val refresh = (result as ApiResult.Success).value
            assertEquals(2, refresh.total)
            assertEquals(2, refresh.itemCount)
            assertEquals(2, dao.snapshot().size)
        }

    @Test
    fun `observeByCategory maps Room rows to domain models`() =
        runTest {
            dao.seed(
                listOf(
                    entity(1, "Tracks", category = "music"),
                    entity(2, "The Wall", category = "music"),
                ),
            )

            repo.observeByCategory(Category.Music).test {
                val items = awaitItem()
                assertEquals(2, items.size)
                assertEquals("Tracks", items[0].title)
                assertEquals(Category.Music, items[0].category)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `create posts to API and writes returned item to DAO`() =
        runTest {
            api.nextCreated = mediaDto(99, "Hounds of Love", category = "music")

            val result = repo.create(MediaItemDraft(title = "Hounds of Love", artist = "Kate Bush", format = "LP"))

            assertTrue(result is ApiResult.Success)
            assertEquals("Hounds of Love", (result as ApiResult.Success).value.title)
            assertEquals(1, dao.snapshot().size)
            assertEquals(99L, dao.snapshot().first().id)
        }

    @Test
    fun `delete clears row from DAO on success`() =
        runTest {
            dao.seed(listOf(entity(7, "Dummy", category = "music")))

            val result = repo.delete(7)

            assertTrue(result is ApiResult.Success)
            assertEquals(0, dao.snapshot().size)
            assertEquals(7L, api.deletedIds.single())
        }

    private fun mediaDto(
        id: Long,
        title: String,
        category: String,
    ) = MediaItemDto(
        id = id,
        title = title,
        artist = "test",
        format = "LP",
        status = Status.Owned.apiValue,
        category = category,
    )

    private fun entity(
        id: Long,
        title: String,
        category: String,
    ) = MediaItemEntity(
        id = id,
        title = title,
        artist = "test",
        format = "LP",
        status = Status.Owned.apiValue,
        category = category,
    )
}

private class FakeMediaItemDao : MediaItemDao {
    private val rows = MutableStateFlow<List<MediaItemEntity>>(emptyList())

    fun seed(items: List<MediaItemEntity>) {
        rows.value = items
    }

    fun snapshot(): List<MediaItemEntity> = rows.value

    override fun observeAll(): Flow<List<MediaItemEntity>> = rows

    override fun observeByCategory(
        category: String,
        status: String?,
    ): Flow<List<MediaItemEntity>> =
        rows.map { list ->
            list.filter { it.category == category && (status == null || it.status == status) }
        }

    override fun observe(id: Long): Flow<MediaItemEntity?> = rows.map { it.firstOrNull { row -> row.id == id } }

    override suspend fun get(id: Long): MediaItemEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun maxUpdatedAt(): String? = rows.value.maxOfOrNull { it.updatedAt.orEmpty() }

    override suspend fun upsert(item: MediaItemEntity) {
        rows.value = rows.value.filterNot { it.id == item.id } + item
    }

    override suspend fun upsertAll(items: List<MediaItemEntity>) {
        items.forEach { upsert(it) }
    }

    override suspend fun delete(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}
