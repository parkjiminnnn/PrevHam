package io.github.parkjiminnnn.prevham.showcase

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runtime cover for issue #59.
 *
 * The compile tests in `:compiler` assert that PrevHam *emits* these expressions; this asserts that
 * the expressions actually produce values a composable can use. Each one is copied verbatim from
 * `StateHolderCardPreview.kt` as generated into `sample/build/generated/ksp`, so if the generator's
 * output shape changes, the compile tests catch it and this file has to be updated to match.
 */
class GeneratedMockValueTest {
    @Test
    fun `a stubbed StateFlow member yields the real state, not a mock`() {
        val viewModel =
            mockk<ScreenViewModel>(relaxed = true) {
                every { this@mockk.uiState } returns MutableStateFlow(ScreenUiState.Loading)
            }

        val state = viewModel.uiState.value

        assertSame(ScreenUiState.Loading, state)
        assertTrue(state is ScreenUiState.Loading)
        assertEquals("loading", state.branchLabel())
    }

    @Test
    fun `relaxed mode alone cannot produce a usable value for a generic member`() {
        // What PrevHam used to generate. StateFlow<T>.value erases to Object, so relaxed mode has
        // no type to work from and answers with a bare Object - which the caller's checkcast to
        // ScreenUiState then rejects. This is the crash from issue #59, and the reason the stubs in
        // the test above exist. Kept as an executable record of the bug: if MockK ever starts
        // handling this, this test fails and the workaround can be revisited.
        val viewModel = mockk<ScreenViewModel>(relaxed = true)

        val failure =
            assertThrows(ClassCastException::class.java) {
                // The cast is what fails, so the value has to actually be used at its declared
                // type - exactly as the composable does when it branches on the state.
                val state: ScreenUiState = viewModel.uiState.value
                state.branchLabel()
            }

        assertTrue(failure.message, failure.message.orEmpty().contains("java.lang.Object"))
    }

    @Test
    fun `a sealed parameter is built as a real subtype with real field values`() {
        // What PrevHam generates for a bare sealed-type parameter. MockK can produce a sealed value
        // too, but it goes through Objenesis: the subtype is whichever one it happens to pick, and
        // its fields are left at MockK's defaults rather than the values PrevHam's generators
        // produce.
        val state: ScreenUiState = ScreenUiState.Loading

        assertSame(ScreenUiState.Loading, state)
        assertEquals("loading", state.branchLabel())
    }

    private fun ScreenUiState.branchLabel(): String =
        when (this) {
            is ScreenUiState.Loading -> "loading"
            is ScreenUiState.Success -> "success"
            is ScreenUiState.Error -> "error"
        }

    @Test
    fun `a stub on a member named like MockK's own applies to the mock`() {
        // Copied from KeyedRepoCardPreview.kt. MockKMatcherScope declares a get of its own and is
        // the innermost receiver inside every { }, so without naming the receiver this doesn't
        // compile at all (issue #83).
        val repository =
            mockk<KeyedRepository<User>>(relaxed = true) {
                every { this@mockk.get(any()) } returns User(id = 1, name = "mock", age = 1)
            }

        val user = repository.get("key")

        assertEquals("mock", user.name)
        assertEquals(1, user.id)
    }

    @Test
    fun `a nested stub binds to the inner mock, not the outer one`() {
        // Copied from NestedRepoCardPreview.kt. Both stubs are written this@mockk, and the inner
        // one has to resolve to the inner mockk lambda. Bound to the outer mock it would still
        // compile and still be a valid program, so only running it shows where the stub landed.
        val holder =
            mockk<RepositoryHolder>(relaxed = true) {
                every { this@mockk.repository } returns
                    mockk<KeyedRepository<User>>(relaxed = true) {
                        every { this@mockk.get(any()) } returns User(id = 1, name = "mock", age = 1)
                    }
            }

        val user = holder.repository.get("key")

        assertEquals("mock", user.name)
    }
}
