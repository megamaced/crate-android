package com.megamaced.crate.ui.screen.addedit

import androidx.lifecycle.SavedStateHandle
import com.megamaced.crate.data.api.ApiResult
import com.megamaced.crate.data.api.dto.OpenLibraryResultDto
import com.megamaced.crate.data.prefs.CreateReceipts
import com.megamaced.crate.data.repository.FakeCrateApiService
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.domain.model.MarketSettings
import com.megamaced.crate.domain.model.MarketValue
import com.megamaced.crate.domain.model.MediaItem
import com.megamaced.crate.domain.model.MediaItemDraft
import com.megamaced.crate.domain.model.PurchasePrice
import com.megamaced.crate.domain.model.Status
import com.megamaced.crate.domain.model.UserProfile
import com.megamaced.crate.domain.repository.EnrichmentRepository
import com.megamaced.crate.domain.repository.MediaRepository
import com.megamaced.crate.domain.repository.SettingsRepository
import com.megamaced.crate.util.MAX_PICKED_IMAGE_BYTES
import com.megamaced.crate.util.PickedImageReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -- Purchase price -------------------------------------------------------

    @Test
    fun `purchase price accepts a comma decimal separator`() {
        val vm = newViewModel()

        vm.onPurchasePriceChange("12,50")

        assertEquals("12.50", vm.uiState.value.purchasePrice)
    }

    @Test
    fun `purchase price keeps only the first decimal separator`() {
        val vm = newViewModel()

        // Without the dedupe this survives the filter but fails to parse, so
        // the price is silently dropped on save while the field still shows it.
        vm.onPurchasePriceChange("12.3.4")

        assertEquals("12.34", vm.uiState.value.purchasePrice)
        assertNotNull(
            vm.uiState.value.purchasePrice
                .toDoubleOrNull(),
        )
    }

    @Test
    fun `purchase price strips non-numeric characters and can be cleared`() {
        val vm = newViewModel()

        vm.onPurchasePriceChange("£24.99")
        assertEquals("24.99", vm.uiState.value.purchasePrice)

        vm.onPurchasePriceChange("")
        assertEquals("", vm.uiState.value.purchasePrice)
    }

    @Test
    fun `a blank price sends no currency, a set price sends one`() =
        runTest {
            val media = RecordingMediaRepository()
            val vm = newViewModel(media = media)
            fillRequiredFields(vm)
            vm.onPurchasePriceCurrencyChange("EUR")

            vm.save()
            advanceUntilIdle()
            assertNull(media.created.last().purchasePrice)
            assertNull(media.created.last().purchasePriceCurrency)

            vm.onPurchasePriceChange("24.99")
            vm.save()
            advanceUntilIdle()
            assertEquals(24.99, media.created.last().purchasePrice!!, 0.001)
            assertEquals("EUR", media.created.last().purchasePriceCurrency)
        }

    // -- canSave --------------------------------------------------------------

    @Test
    fun `canSave requires title, artist and format`() {
        val vm = newViewModel()
        assertFalse(vm.uiState.value.canSave)

        vm.onTitleChange("OK Computer")
        assertFalse(vm.uiState.value.canSave)

        vm.onArtistChange("Radiohead")
        assertFalse(vm.uiState.value.canSave)

        vm.onFormatChange("Vinyl")
        assertTrue(vm.uiState.value.canSave)

        // Whitespace is not a value.
        vm.onArtistChange("   ")
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun `an out-of-range year blocks saving`() {
        val vm = newViewModel()
        fillRequiredFields(vm)

        vm.onYearChange("1797")
        assertTrue(vm.uiState.value.yearError)
        assertFalse(vm.uiState.value.canSave)

        vm.onYearChange("1997")
        assertFalse(vm.uiState.value.yearError)
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun `the year field only keeps four digits`() {
        val vm = newViewModel()

        vm.onYearChange("19x97456")

        assertEquals("1997", vm.uiState.value.year)
    }

    @Test
    fun `save is a no-op while the form is incomplete`() =
        runTest {
            val media = RecordingMediaRepository()
            val vm = newViewModel(media = media)

            vm.save()
            advanceUntilIdle()

            assertTrue(media.created.isEmpty())
        }

    @Test
    fun `a format the category doesn't know is flagged but still saveable`() {
        val vm = newViewModel()
        fillRequiredFields(vm)

        vm.onFormatChange("PlayStation 4")
        assertTrue(vm.uiState.value.formatUnrecognised)
        assertTrue(vm.uiState.value.canSave)

        vm.onFormatChange("Vinyl")
        assertFalse(vm.uiState.value.formatUnrecognised)
    }

    // -- applyExternalResult merge -------------------------------------------

    @Test
    fun `a provider result fills blanks without clobbering what the user typed`() {
        val vm = newViewModel()
        vm.onNotesChange("Sleeve has a split seam")
        vm.onCountryChange("UK")

        vm.applyExternalResult(
            ExternalSearchResult(
                title = "OK Computer",
                artist = "Radiohead",
                format = "Vinyl",
                year = 1997,
                barcode = "0724385522918",
                label = "Parlophone",
                // Blank/absent provider fields must not wipe existing values.
                country = "  ",
                discogsId = "5468",
            ),
        )

        val state = vm.uiState.value
        assertEquals("OK Computer", state.title)
        assertEquals("Radiohead", state.artist)
        assertEquals("Vinyl", state.format)
        assertEquals("1997", state.year)
        assertEquals("0724385522918", state.barcode)
        assertEquals("Parlophone", state.label)
        assertEquals("5468", state.discogsId)
        assertEquals("UK", state.country)
        // Notes are the user's own and no provider supplies them.
        assertEquals("Sleeve has a split seam", state.notes)
    }

    @Test
    fun `an empty provider title leaves the typed title alone`() {
        val vm = newViewModel()
        vm.onTitleChange("Kid A")

        vm.applyExternalResult(ExternalSearchResult(title = "", year = 2000))

        assertEquals("Kid A", vm.uiState.value.title)
        assertEquals("2000", vm.uiState.value.year)
    }

    @Test
    fun `a provider cover becomes the preview and undoes a pending removal`() {
        val vm = newViewModel()
        vm.onRemoveArtwork()
        assertTrue(vm.uiState.value.removeArtwork)

        vm.applyExternalResult(
            ExternalSearchResult(title = "OK Computer", coverUrl = "https://img.invalid/ok.jpg"),
        )

        val state = vm.uiState.value
        assertEquals("https://img.invalid/ok.jpg", state.pendingArtworkUrl)
        assertEquals("https://img.invalid/ok.jpg", state.artworkPath)
        assertFalse(state.removeArtwork)
        assertTrue(state.hasArtworkPreview)
    }

    // -- Server contract ------------------------------------------------------

    @Test
    fun `an emptied optional field goes out as blank, not omitted`() =
        runTest {
            val media = RecordingMediaRepository()
            val vm = newViewModel(media = media)
            fillRequiredFields(vm)
            vm.onLabelChange("Parlophone")
            vm.onCountryChange("UK")
            vm.onLabelChange("")
            vm.onCountryChange("  ")

            vm.save()
            advanceUntilIdle()

            // The server reads null as "leave unchanged" and "" as "clear", and
            // null keys are omitted from the payload entirely.
            assertEquals("", media.created.single().label)
            assertEquals("", media.created.single().country)
        }

    // -- Rating ---------------------------------------------------------------

    @Test
    fun `an edit that leaves the rating alone sends none, so the server keeps it`() =
        runTest {
            val media = RecordingMediaRepository(storedRating = 4)
            val vm = newViewModel(handle = editing(7L), media = media)
            advanceUntilIdle()
            assertEquals(4, vm.uiState.value.rating)

            vm.onTitleChange("OK Computer OKNOTOK")
            vm.save()
            advanceUntilIdle()

            assertNull(media.created.single().rating)
        }

    @Test
    fun `a rating set on the form goes out as its stars, a cleared one as 0`() =
        runTest {
            val media = RecordingMediaRepository(storedRating = 4)
            val vm = newViewModel(handle = editing(7L), media = media)
            advanceUntilIdle()

            vm.onRatingChange(5)
            vm.save()
            advanceUntilIdle()
            assertEquals(5, media.created.last().rating)

            vm.onRatingChange(null)
            vm.save()
            advanceUntilIdle()
            assertEquals(0, media.created.last().rating)
        }

    @Test
    fun `a form parked by a version without ratings saves without clearing the rating`() =
        runTest {
            val media = RecordingMediaRepository(storedRating = 4)
            // What the form held before ratings existed: no rating fields.
            val parked = """{"isEditing":true,"editingItemId":7,"title":"OK Computer","artist":"Radiohead","format":"Vinyl"}"""
            val vm = newViewModel(handle = editing(7L, parked = parked), media = media)
            advanceUntilIdle()

            vm.save()
            advanceUntilIdle()

            assertNull(media.created.single().rating)
        }

    @Test
    fun `the rating control shows only for a server that keeps ratings`() =
        runTest {
            val old = newViewModel(settings = StubSettingsRepository(crateVersion = "1.14.0"))
            val current = newViewModel(settings = StubSettingsRepository(crateVersion = "1.15.0"))
            advanceUntilIdle()

            assertFalse(old.uiState.value.ratingsSupported)
            assertTrue(current.uiState.value.ratingsSupported)
        }

    @Test
    fun `a restored form asks the server again whether it keeps ratings`() =
        runTest {
            val parked =
                """{"isEditing":true,"editingItemId":7,"title":"OK Computer","artist":"Radiohead",""" +
                    """"format":"Vinyl","rating":3,"ratingEdited":true}"""
            val vm =
                newViewModel(
                    handle = editing(7L, parked = parked),
                    settings = StubSettingsRepository(crateVersion = "1.15.0"),
                )
            assertFalse("not taken from the parked form", vm.uiState.value.ratingsSupported)
            assertEquals(3, vm.uiState.value.rating)

            advanceUntilIdle()

            assertTrue(vm.uiState.value.ratingsSupported)
        }

    // -- Picked search results --------------------------------------------------

    @Test
    fun `a book picked from search is saved linked to its work`() =
        runTest {
            val media = RecordingMediaRepository()
            val vm = newViewModel(media = media)
            vm.applyExternalResult(
                OpenLibraryResultDto(
                    workKey = "/works/OL27482W",
                    title = "Der Kleine Hobbit",
                    artist = "J.R.R. Tolkien",
                    artworkUrl = "https://covers.openlibrary.org/b/id/14626130-L.jpg",
                ).toResult(),
            )
            vm.onFormatChange("Paperback")

            vm.save()
            advanceUntilIdle()

            val draft = media.created.single()
            assertEquals("/works/OL27482W", draft.discogsId)
            assertEquals("Der Kleine Hobbit", draft.title)
        }

    @Test
    fun `an ISBN lookup links the item to the work it found`() =
        runTest {
            val api =
                FakeCrateApiService().apply {
                    isbnResult =
                        OpenLibraryResultDto(
                            workKey = "/works/OL27482W",
                            title = "The Hobbit",
                            artist = "J.R.R. Tolkien",
                        )
                }
            val vm = newViewModel(api = api)
            vm.onBarcodeChange("9780261102217")

            vm.lookupIsbn()
            advanceUntilIdle()

            assertEquals("/works/OL27482W", vm.uiState.value.discogsId)
        }

    // -- Picked images --------------------------------------------------------

    @Test
    fun `an oversized image is refused with a message and never enters the form`() =
        runTest {
            val reader = FakePickedImageReader(length = MAX_PICKED_IMAGE_BYTES + 1)
            val vm = newViewModel(imageReader = reader)

            vm.onArtworkPicked("content://media/1")
            advanceUntilIdle()

            assertNull(vm.uiState.value.pendingArtwork)
            assertNotNull(vm.uiState.value.errorMessage)
        }

    @Test
    fun `a picked image is held as a uri and read only at upload time`() =
        runTest {
            val reader = FakePickedImageReader()
            val media = RecordingMediaRepository()
            val vm = newViewModel(media = media, imageReader = reader)
            fillRequiredFields(vm)

            vm.onArtworkPicked("content://media/1")
            advanceUntilIdle()
            assertEquals(
                "content://media/1",
                vm.uiState.value.pendingArtwork
                    ?.uri,
            )
            assertTrue(reader.readUris.isEmpty())

            vm.save()
            advanceUntilIdle()
            assertEquals(listOf("content://media/1"), reader.readUris)
            assertEquals(1, media.artworkUploads.size)
        }

    @Test
    fun `a failed photo upload is reported in the same state as the saved id`() =
        runTest {
            val media = RecordingMediaRepository().apply { photoUploadResult = ApiResult.NetworkError }
            val vm = newViewModel(media = media, imageReader = FakePickedImageReader())
            fillRequiredFields(vm)
            vm.onPhotoPicked(slot = 1, uri = "content://media/9")
            advanceUntilIdle()

            vm.save()
            advanceUntilIdle()

            // Both in one emission, so the screen can show the warning to
            // completion before it navigates away.
            val state = vm.uiState.value
            assertNotNull(state.savedItemId)
            assertTrue(state.photoUploadFailed)
        }

    @Test
    fun `an image the provider declared no length for is caught when it is read`() =
        runTest {
            // Cloud and document providers routinely report no length, so the
            // check at pick time passes and the cap has to hold at upload time.
            val reader =
                FakePickedImageReader(
                    length = null,
                    result = PickedImageReader.ReadResult.TooLarge,
                )
            val media = RecordingMediaRepository()
            val vm = newViewModel(media = media, imageReader = reader)
            fillRequiredFields(vm)
            vm.onArtworkPicked("content://media/1")
            advanceUntilIdle()

            vm.save()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertNotNull(state.savedItemId)
            assertTrue(state.imageTooLarge)
            // Nothing was uploaded: the bytes never got past the cap.
            assertTrue(media.artworkUploads.isEmpty())
        }

    @Test
    fun `a successful save reports no photo failure`() =
        runTest {
            val media = RecordingMediaRepository()
            val vm = newViewModel(media = media, imageReader = FakePickedImageReader())
            fillRequiredFields(vm)
            vm.onPhotoPicked(slot = 2, uri = "content://media/9")
            advanceUntilIdle()

            vm.save()
            advanceUntilIdle()

            assertFalse(vm.uiState.value.photoUploadFailed)
            assertFalse(vm.uiState.value.imageTooLarge)
        }

    // -- SavedStateHandle survival -------------------------------------------

    @Test
    fun `the form survives process death`() =
        runTest {
            val handle = SavedStateHandle()
            val first = newViewModel(handle = handle)
            first.onTitleChange("OK Computer")
            first.onArtistChange("Radiohead")
            first.onFormatChange("Vinyl")
            first.onYearChange("1997")
            first.onArtworkPicked("content://media/1")
            advanceUntilIdle()

            // A new ViewModel over the same handle is what the framework builds
            // after the process is killed and the activity recreated.
            val restored = newViewModel(handle = handle)

            val state = restored.uiState.value
            assertEquals("OK Computer", state.title)
            assertEquals("Radiohead", state.artist)
            assertEquals("Vinyl", state.format)
            assertEquals("1997", state.year)
            assertEquals("content://media/1", state.pendingArtwork?.uri)
        }

    @Test
    fun `restoring drops the flags whose coroutines died with the process`() =
        runTest {
            val handle = SavedStateHandle()
            val media = RecordingMediaRepository()
            val first = newViewModel(handle = handle, media = media)
            fillRequiredFields(first)
            first.save()
            advanceUntilIdle()
            assertNotNull(first.uiState.value.savedItemId)

            val restored = newViewModel(handle = handle, media = media)

            assertNull(restored.uiState.value.savedItemId)
            assertFalse(restored.uiState.value.isSaving)
        }

    @Test
    fun `a form restored after its item was created updates that item instead of creating another`() =
        runTest {
            val handle = SavedStateHandle()
            val media = RecordingMediaRepository()
            // The create succeeds, then the process dies while the picked image
            // is still being read for upload.
            val first = newViewModel(handle = handle, media = media, imageReader = StuckImageReader())
            fillRequiredFields(first)
            first.onArtworkPicked("content://media/1")
            advanceUntilIdle()
            first.save()
            advanceUntilIdle()
            assertEquals(1, media.createCount)

            val restored = newViewModel(handle = handle, media = media)
            restored.save()
            advanceUntilIdle()

            assertEquals(1, media.createCount)
            assertEquals(listOf(1L), media.updatedIds)
            // The upload the first attempt never finished goes to that same item.
            assertEquals(listOf(1L), media.artworkUploads)
            assertEquals(1L, restored.uiState.value.savedItemId)
        }

    @Test
    fun `a create that lands after the form's snapshot was taken is still found when it is restored`() =
        runTest {
            val handle = SavedStateHandle()
            val media = RecordingMediaRepository().apply { createGate = CompletableDeferred() }
            val receipts = FakeCreateReceipts()
            val first = newViewModel(handle = handle, media = media, imageReader = StuckImageReader(), receipts = receipts)
            fillRequiredFields(first)
            first.onArtworkPicked("content://media/1")
            advanceUntilIdle()
            first.save()
            advanceUntilIdle()
            // Backgrounded with the create still out: the snapshot Android
            // keeps is the form as it is now. Nothing the form parks after
            // this reaches disk, and the process dies during the upload.
            val snapshot = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
            media.createGate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, media.createCount)

            val restored = newViewModel(handle = snapshot, media = media, receipts = receipts)
            assertNull("the snapshot predates the create", restored.uiState.value.createdItemId)
            restored.save()
            advanceUntilIdle()

            assertEquals(1, media.createCount)
            assertEquals(listOf(1L), media.updatedIds)
            assertEquals(listOf(1L), media.artworkUploads)
        }

    @Test
    fun `a photo removed from a restored form is deleted from the item its first save created`() =
        runTest {
            val handle = SavedStateHandle()
            val media = RecordingMediaRepository()
            // Photo 1 uploads, then the process dies reading photo 2.
            val first =
                newViewModel(handle = handle, media = media, imageReader = StuckImageReader(stuckUris = setOf("content://photo/2")))
            fillRequiredFields(first)
            first.onPhotoPicked(1, "content://photo/1")
            first.onPhotoPicked(2, "content://photo/2")
            advanceUntilIdle()
            first.save()
            advanceUntilIdle()
            assertEquals(listOf(1L to 1), media.photoUploads)

            val restored = newViewModel(handle = handle, media = media)
            restored.onRemovePhoto(1)
            restored.save()
            advanceUntilIdle()

            assertEquals(listOf(1L), media.updatedIds)
            assertEquals(listOf(1L to 1), media.deletedPhotos)
        }

    @Test
    fun `a receipt that can't be read holds the save rather than creating again`() =
        runTest {
            val handle = SavedStateHandle()
            val media = RecordingMediaRepository().apply { createGate = CompletableDeferred() }
            val receipts = FakeCreateReceipts()
            val first = newViewModel(handle = handle, media = media, imageReader = StuckImageReader(), receipts = receipts)
            fillRequiredFields(first)
            first.save()
            advanceUntilIdle()
            val snapshot = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
            media.createGate!!.complete(Unit)
            advanceUntilIdle()

            val restored = newViewModel(handle = snapshot, media = media, receipts = receipts)
            receipts.failingReads = 1
            restored.save()
            advanceUntilIdle()

            assertEquals(1, media.createCount)
            assertEquals(emptyList<Long>(), media.updatedIds)
            assertNotNull("the user is asked to try again", restored.uiState.value.errorMessage)
            assertFalse(restored.uiState.value.isSaving)

            restored.dismissError()
            restored.save()
            advanceUntilIdle()

            assertEquals(1, media.createCount)
            assertEquals(listOf(1L), media.updatedIds)
        }

    @Test
    fun `a create whose receipt can't be written stops before its images and says so`() =
        runTest {
            val media = RecordingMediaRepository()
            val receipts = FakeCreateReceipts().apply { writesFail = true }
            val vm = newViewModel(media = media, receipts = receipts)
            fillRequiredFields(vm)
            vm.onArtworkPicked("content://media/1")
            advanceUntilIdle()

            vm.save()
            advanceUntilIdle()

            assertEquals(1, media.createCount)
            assertEquals(emptyList<Long>(), media.artworkUploads)
            assertNotNull(vm.uiState.value.errorMessage)
            assertNull("the screen stays open", vm.uiState.value.savedItemId)

            vm.dismissError()
            vm.save()
            advanceUntilIdle()

            assertEquals(1, media.createCount)
            assertEquals(listOf(1L), media.updatedIds)
            assertEquals(listOf(1L), media.artworkUploads)
            assertEquals(1L, vm.uiState.value.savedItemId)
        }

    // -- Helpers --------------------------------------------------------------

    private fun fillRequiredFields(vm: AddEditViewModel) {
        vm.onTitleChange("OK Computer")
        vm.onArtistChange("Radiohead")
        vm.onFormatChange("Vinyl")
    }

    /** Nav args for editing item [id], with [parked] as the form saved across process death. */
    private fun editing(
        id: Long,
        parked: String? = null,
    ): SavedStateHandle =
        SavedStateHandle(
            buildMap {
                put("itemId", id)
                if (parked != null) put("add_edit_form", parked)
            },
        )

    private fun newViewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        media: MediaRepository = RecordingMediaRepository(),
        imageReader: PickedImageReader = FakePickedImageReader(),
        receipts: CreateReceipts = FakeCreateReceipts(),
        settings: SettingsRepository = StubSettingsRepository(),
        api: FakeCrateApiService = FakeCrateApiService(),
    ): AddEditViewModel =
        AddEditViewModel(
            savedStateHandle = handle,
            mediaRepository = media,
            settingsRepository = settings,
            enrichmentRepository = StubEnrichmentRepository(),
            imageReader = imageReader,
            api = api,
            createReceipts = receipts,
        )
}

/** Receipts that outlive the ViewModel, as the disk-backed ones outlive the process. */
private class FakeCreateReceipts : CreateReceipts {
    private val receipts = mutableMapOf<String, Long>()

    /** How many reads fail before one succeeds. */
    var failingReads = 0

    var writesFail = false

    override suspend fun record(
        formId: String,
        itemId: Long,
    ) {
        if (writesFail) throw IOException("disk full")
        receipts[formId] = itemId
    }

    override suspend fun find(formId: String): Long? {
        if (failingReads > 0) {
            failingReads--
            throw IOException("read failed")
        }
        return receipts[formId]
    }

    override fun forget(formId: String) {
        receipts.remove(formId)
    }
}

private class FakePickedImageReader(
    private val length: Long? = 1_024,
    private val result: PickedImageReader.ReadResult = PickedImageReader.ReadResult.Success(ByteArray(4) { 1 }),
) : PickedImageReader {
    val readUris = mutableListOf<String>()

    override suspend fun mimeType(uri: String): String = "image/jpeg"

    override suspend fun length(uri: String): Long? = length

    override suspend fun read(uri: String): PickedImageReader.ReadResult {
        readUris += uri
        return result
    }
}

/**
 * Picked images whose bytes never arrive, as when the process dies mid-read:
 * those in [stuckUris], or every one when it is null.
 */
private class StuckImageReader(
    private val stuckUris: Set<String>? = null,
) : PickedImageReader {
    override suspend fun mimeType(uri: String): String = "image/jpeg"

    override suspend fun length(uri: String): Long? = 1_024

    override suspend fun read(uri: String): PickedImageReader.ReadResult =
        if (stuckUris == null || uri in stuckUris) {
            awaitCancellation()
        } else {
            PickedImageReader.ReadResult.Success(ByteArray(4) { 1 })
        }
}

private class RecordingMediaRepository(
    /** The rating the server holds for the item being edited. */
    private val storedRating: Int? = null,
) : MediaRepository {
    private val items = MutableStateFlow<List<MediaItem>>(emptyList())

    val created = mutableListOf<MediaItemDraft>()
    var createCount = 0
    val updatedIds = mutableListOf<Long>()
    val artworkUploads = mutableListOf<Long>()
    val photoUploads = mutableListOf<Pair<Long, Int>>()
    val deletedPhotos = mutableListOf<Pair<Long, Int>>()
    var photoUploadResult: ApiResult<Unit> = ApiResult.Success(Unit)

    /** When set, a create waits for it before answering. */
    var createGate: CompletableDeferred<Unit>? = null

    override fun observeAll(): Flow<List<MediaItem>> = items

    override fun observeByCategory(
        category: Category,
        status: Status?,
    ): Flow<List<MediaItem>> = items

    override fun observe(id: Long): Flow<MediaItem?> = items.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun refresh(
        category: Category?,
        status: Status?,
        limit: Int,
        offset: Int,
    ): ApiResult<MediaRepository.RefreshResult> = ApiResult.Success(MediaRepository.RefreshResult(0, limit, offset, 0))

    override suspend fun refreshSingle(id: Long): ApiResult<MediaItem> = ApiResult.Success(savedItem(id, storedRating))

    override suspend fun create(draft: MediaItemDraft): ApiResult<MediaItem> {
        createGate?.await()
        created += draft
        createCount++
        return ApiResult.Success(savedItem(1))
    }

    override suspend fun update(
        id: Long,
        draft: MediaItemDraft,
    ): ApiResult<MediaItem> {
        created += draft
        updatedIds += id
        return ApiResult.Success(savedItem(id))
    }

    override suspend fun delete(id: Long): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun wipeCollection(scopes: List<String>): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun uploadArtwork(
        id: Long,
        bytes: ByteArray,
        mimeType: String,
    ): ApiResult<Unit> {
        artworkUploads += id
        return ApiResult.Success(Unit)
    }

    override suspend fun deleteArtwork(id: Long): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun uploadPhoto(
        id: Long,
        slot: Int,
        bytes: ByteArray,
        mimeType: String,
    ): ApiResult<Unit> {
        photoUploads += id to slot
        return photoUploadResult
    }

    override suspend fun deletePhoto(
        id: Long,
        slot: Int,
    ): ApiResult<Unit> {
        deletedPhotos += id to slot
        return ApiResult.Success(Unit)
    }

    override suspend fun syncDelta(
        updatedSince: String?,
        lastSeenWipedAt: String?,
    ): ApiResult<MediaRepository.SyncResult> =
        ApiResult.Success(MediaRepository.SyncResult(cursor = updatedSince, wipedAt = lastSeenWipedAt))
}

private class StubEnrichmentRepository : EnrichmentRepository {
    override suspend fun enrich(itemId: Long): ApiResult<MediaItem> = ApiResult.Success(savedItem(itemId))

    override suspend fun stripEnrichment(itemId: Long): ApiResult<MediaItem> = ApiResult.Success(savedItem(itemId))

    override suspend fun fetchMarketValue(
        itemId: Long,
        currency: String,
    ): ApiResult<MediaItem> = ApiResult.Success(savedItem(itemId))

    override suspend fun listRefreshableMarketValues() = error("not used")

    override suspend fun listUnenrichedItems(): ApiResult<List<Long>> = ApiResult.Success(emptyList())
}

private class StubSettingsRepository(
    private val crateVersion: String? = "0.4.2",
) : SettingsRepository {
    override suspend fun getMe(): ApiResult<UserProfile> =
        ApiResult.Success(
            UserProfile(
                userId = "test",
                displayName = "Test",
                avatarUrl = null,
                hasDiscogsToken = false,
                marketCurrency = "GBP",
                autoFetchMarketRates = false,
                autoEnrichOnClick = false,
                autoEnrichOnImport = false,
                hiddenCategories = emptySet(),
                onlineRecommendations = false,
                crateVersion = crateVersion,
            ),
        )

    override suspend fun hasDiscogsToken() = error("not used")

    override suspend fun setDiscogsToken(token: String) = error("not used")

    override suspend fun hasTmdbToken() = error("not used")

    override suspend fun setTmdbToken(token: String) = error("not used")

    override suspend fun hasRawgKey() = error("not used")

    override suspend fun setRawgKey(key: String) = error("not used")

    override suspend fun hasComicVineKey() = error("not used")

    override suspend fun setComicVineKey(key: String) = error("not used")

    override suspend fun hasPriceChartingToken() = error("not used")

    override suspend fun setPriceChartingToken(token: String) = error("not used")

    override suspend fun getMarketSettings() = ApiResult.Success(MarketSettings(false, "GBP"))

    override suspend fun setMarketSettings(settings: MarketSettings) = error("not used")

    override suspend fun getCurrencies() = error("not used")

    override val hiddenCategoriesFlow: Flow<Set<Category>> = flowOf(emptySet())

    override suspend fun setHiddenCategories(categories: Set<Category>) = ApiResult.Success(Unit)

    override val onlineRecommendationsFlow: Flow<Boolean> = flowOf(false)

    override suspend fun setOnlineRecommendations(enabled: Boolean) = ApiResult.Success(Unit)
}

private fun savedItem(
    id: Long,
    rating: Int? = null,
) = MediaItem(
    id = id,
    userId = null,
    title = "OK Computer",
    artist = "Radiohead",
    format = "Vinyl",
    year = 1997,
    barcode = null,
    notes = null,
    status = Status.Owned,
    category = Category.Music,
    discogsId = null,
    artworkPath = null,
    label = null,
    country = null,
    genres = null,
    tracklist = emptyList(),
    pressingNotes = null,
    discogsArtistId = null,
    artistBio = null,
    artistMembers = emptyList(),
    marketValue = MarketValue(null, null, null, null, null),
    purchasePrice = PurchasePrice(null, null),
    createdAt = null,
    updatedAt = null,
    rating = rating,
)
