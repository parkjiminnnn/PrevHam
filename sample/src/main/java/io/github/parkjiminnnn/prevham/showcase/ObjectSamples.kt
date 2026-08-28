package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

// An object is its own value - one instance, no constructor to run, no fields to invent - so the
// reference is the whole answer. Before issue #77 nothing claimed ClassKind.OBJECT, so a Preview
// taking one was skipped entirely, and a `data object` was worse: DataClassMockGenerator claimed it
// on its DATA modifier and emitted `Loading()`, which doesn't compile.
//
// toClassName() carries the enclosing names, so a nested object and a companion are written the way
// they are actually referenced.

object CrashReporter {
    fun report(message: String) = Unit
}

class ApiClient {
    companion object Registry
}

data class TrackedScreen(
    val title: String,
    val reporter: CrashReporter,
)

@Prev
@Composable
fun ReporterCard(reporter: CrashReporter) {
    Text(text = "reporter")
}

@Prev
@Composable
fun RegistryCard(registry: ApiClient.Registry) {
    Text(text = "registry")
}

// A field reaches the generators through the constructor's parameter list, and one unsupported
// parameter skipped the whole Preview - so `reporter` below used to cost the entire Preview.

@Prev
@Composable
fun TrackedScreenCard(screen: TrackedScreen) {
    Text(text = screen.title)
}

// A data object used directly as the parameter type, rather than through its sealed supertype.
// This generated `ScreenUiState.Loading()`.

@Prev
@Composable
fun LoadingCard(state: ScreenUiState.Loading) {
    Text(text = "loading")
}

// An object held on a mock is stubbed rather than left to relaxed mode. MockK builds a fresh
// instance through Objenesis, so the singleton's identity is lost:
//
//     holder.state === ScreenUiState.Loading                 // false without the stub
//     when (holder.state) { ScreenUiState.Loading -> ... }   // matches nothing
//
// The generated argument:
//
//     mockk<LoadingHolder>(relaxed = true) {
//         every { this@mockk.state } returns ScreenUiState.Loading
//     }
//
// GeneratedMockValueTest in src/test asserts the identity holds.

interface LoadingHolder {
    val state: ScreenUiState.Loading
}

@Prev
@Composable
fun HolderCard(holder: LoadingHolder) {
    Text(text = if (holder.state === ScreenUiState.Loading) "singleton" else "copy")
}
