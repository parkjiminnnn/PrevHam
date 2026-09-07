package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import io.github.parkjiminnnn.runtime.Prev
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// The shape that used to crash the Preview renderer (issue #59): a composable taking a ViewModel and
// reading a StateFlow of a sealed type off it.
//
// The ViewModel itself is still a MockK mock - PrevHam can't build one for real, since its
// constructor dependencies are whatever the app's DI graph provides. What changed is that every
// member PrevHam can build a value for is stubbed on that mock up front.
//
// Without those stubs, relaxed mode has to invent a value for `uiState.value` from the only type it
// can see - and `StateFlow<T>.value` erases to Object, so it answers with a bare Object. The
// checkcast in `when (state) { is ScreenUiState.Loading -> ... }` then throws ClassCastException.
//
// The generated argument for the parameter below:
//
//     mockk<ScreenViewModel>(relaxed = true) {
//         every { this@mockk.uiState } returns MutableStateFlow(ScreenUiState.Loading)
//         every { this@mockk.screenTitle } returns "2026 대동제"
//         every { this@mockk.titleFor(any()) } returns "제 1회 대학 음악제"
//     }
//
// Two different reasons to stub, and both are needed:
//
//   * `uiState` *has* to be stubbed. It erases, so relaxed mode cannot answer it at all.
//   * `screenTitle` and `titleFor` *may* be stubbed, and are here only because
//     src/main/prevham/mock-values.json holds values for them (issue #103). Relaxed mode would answer
//     a String on its own, just an empty one - which is what a Preview showed before.
//
// Everything else is left to relaxed mode. Stubbing members that need neither is what made
// generation grow with the size of the dependency graph (issue #75), and a configured value does not
// bring that back: its stub is a literal, so nothing recurses through it, and one is emitted only
// where a person wrote a value.
//
// MockCastingTest in src/test asserts that the value this expression produces really does pass the
// `is` checks below, rather than being another mock.

class ScreenViewModel : ViewModel() {
    private val internalUiState = MutableStateFlow<ScreenUiState>(ScreenUiState.Loading)

    val uiState: StateFlow<ScreenUiState> = internalUiState.asStateFlow()

    val screenTitle: String = "대동제"

    fun titleFor(state: ScreenUiState): String = state.toString()
}

@Prev
@Composable
fun StateHolderCard(viewModel: ScreenViewModel) {
    val state by viewModel.uiState.collectAsState()
    Text(
        text =
            when (state) {
                is ScreenUiState.Loading -> viewModel.screenTitle
                is ScreenUiState.Success -> viewModel.titleFor(state)
                is ScreenUiState.Error -> "error"
            },
    )
}
