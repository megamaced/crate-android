package com.megamaced.crate.ui.screen.scan

import androidx.lifecycle.SavedStateHandle
import com.megamaced.crate.data.api.CrateApiService
import com.megamaced.crate.data.api.dto.DiscogsSearchResultDto
import com.megamaced.crate.testing.unimplemented
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The candidate sheet is for the barcode that opened it. Coming back from the
 * background used to restart the camera behind the sheet, and a second barcode
 * then replaced the first one's candidates while the user was choosing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BarcodeScanViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val lookedUp = mutableListOf<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a second barcode does not replace an open candidate sheet`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onBarcodeDetected("5099902987026")
            advanceUntilIdle()

            vm.onBarcodeDetected("0602547925660")
            advanceUntilIdle()

            assertEquals("5099902987026", vm.uiState.value.barcode)
            assertEquals(
                listOf("Title for 5099902987026"),
                vm.uiState.value.candidates
                    .map { it.title },
            )
            assertEquals(listOf("5099902987026"), lookedUp)
        }

    @Test
    fun `scanning resumes once the sheet is dismissed`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onBarcodeDetected("5099902987026")
            advanceUntilIdle()
            vm.dismissSheet()

            vm.onBarcodeDetected("0602547925660")
            advanceUntilIdle()

            assertEquals("0602547925660", vm.uiState.value.barcode)
            assertEquals(listOf("5099902987026", "0602547925660"), lookedUp)
        }

    private fun viewModel() =
        BarcodeScanViewModel(
            SavedStateHandle(mapOf("category" to "music")),
            object : CrateApiService by unimplemented<CrateApiService>() {
                override suspend fun discogsBarcode(barcode: String): List<DiscogsSearchResultDto> {
                    lookedUp += barcode
                    return listOf(DiscogsSearchResultDto(discogsId = "1", title = "Title for $barcode", barcode = barcode))
                }
            },
        )
}
