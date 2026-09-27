package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.runtime.State
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runtime cover for issue #80.
 *
 * A compile test cannot see this failure: the generated code compiles either way, and what differs
 * is whether reading through the container hands back something the caller's checkcast accepts.
 * That is the same blind spot that let #59 ship.
 *
 * Both expressions are copied verbatim from `CatalogHeaderPreview.kt` as generated into
 * `sample/build/generated/ksp`.
 */
class CompiledContainerTest {
    @Test
    fun `a stubbed compiled container yields a real value, not an Object`() {
        val viewModel =
            mockk<CatalogViewModel>(relaxed = true) {
                every { this@mockk.uiState } returns
                    mockk<State<CatalogUiState>>(relaxed = true) {
                        every { this@mockk.value } returns CatalogUiState(title = "mock", count = 1)
                    }
            }

        val state: CatalogUiState = viewModel.uiState.value

        assertEquals("mock", state.title)
        assertEquals(1, state.count)
    }

    @Test
    fun `an unstubbed compiled container still throws`() {
        // What PrevHam generated before #80, kept as an executable record of the bug. State<T>.value
        // compiles to Object getValue(), so relaxed mode has only the erased type to work from and
        // answers with a bare Object.
        val viewModel = mockk<CatalogViewModel>(relaxed = true)

        val failure =
            assertThrows(ClassCastException::class.java) {
                val state: CatalogUiState = viewModel.uiState.value
                state.title
            }

        assertTrue(failure.message, failure.message.orEmpty().contains("java.lang.Object"))
    }
}
