package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import io.github.parkjiminnnn.runtime.Prev

// A container from a compiled dependency, held by a type the consumer wrote. `State<T>` is what
// `mutableStateOf` produces, so a ViewModel exposing one is an ordinary Compose shape - and reading
// it is exactly what erases:
//
//     public abstract java.lang.Object getValue();
//
// Relaxed mode answers from that and hands back a bare Object, which the checkcast in
// `viewModel.uiState.value` rejects. Until issue #80 the member was left unstubbed, because the
// search stops at anything not declared in the sources being compiled - the gate that keeps
// `java.time.LocalDate` from being walked into (issue #75).
//
// It is now opened one level, and only from a member the consumer wrote:
//
//     every { this@mockk.uiState } returns mockk<State<CatalogUiState>>(relaxed = true) {
//         every { this@mockk.value } returns CatalogUiState(title = "mock", count = 1)
//     }
//
// CompiledContainerTest in src/test asserts the value that expression produces really does survive
// the cast - a compile test cannot see this failure, which is how it went unnoticed.

data class CatalogUiState(
    val title: String,
    val count: Int,
)

class CatalogViewModel(
    private val source: CatalogSource,
) : ViewModel() {
    val uiState: State<CatalogUiState> get() = source.state
}

interface CatalogSource {
    val state: State<CatalogUiState>
}

@Prev
@Composable
fun CatalogHeader(viewModel: CatalogViewModel) {
    val state = viewModel.uiState.value
    Text("${state.title} (${state.count})")
}
