package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

// NullableFallbackMockGenerator is checked last, so a nullable type still gets a real mock when
// some other generator supports it - `name` becomes "mock", not null. Only types nothing else can
// handle fall back to a literal null.

@Prev
@Composable
fun NullableCard(
    name: String?,
    onClick: (() -> Unit)?,
) {
    Text(text = name ?: "no name")
}
