package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.parkjiminnnn.runtime.Prev

// Primitives and String are handled by PrimitiveMockGenerator and StringMockGenerator: each maps to
// a fixed literal (1, true, 'a', "mock"). `modifier` has a default value, so PrevHam may omit it -
// though here InterfaceMockGenerator resolves it to the real `Modifier` companion rather than a mock.

@Prev
@Composable
fun Greeting(
    name: String,
    modifier: Modifier = Modifier,
) {
    Text(text = "Hello $name!", modifier = modifier)
}

@Prev
@Composable
fun StatsRow(
    count: Int,
    ratio: Float,
    enabled: Boolean,
) {
    Text(text = "count=$count ratio=$ratio enabled=$enabled")
}
