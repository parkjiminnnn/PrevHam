package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.parkjiminnnn.runtime.Prev

// InterfaceMockGenerator covers interfaces and non-data classes with mockk<T>(relaxed = true),
// which is why consumers need MockK on the classpath. It prefers a real instance when the type has
// a companion object implementing itself - `Modifier` below resolves to `Modifier`, not a mock.

interface ImageLoader {
    fun load(url: String)
}

class AnalyticsTracker

@Prev
@Composable
fun IconButtonCard(
    modifier: Modifier,
    loader: ImageLoader,
) {
    Text(text = "icon", modifier = modifier)
}

@Prev
@Composable
fun TrackedCard(tracker: AnalyticsTracker) {
    Text(text = "tracked")
}
