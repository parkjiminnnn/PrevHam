package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

// darkMode, locales, and fontScales each add one @Preview on top of the default one. Compose's
// @Preview is @Repeatable, so they stack on a single generated function rather than needing one
// wrapper per variant - this composable produces six Previews in the IDE.

@Prev(darkMode = true, locales = ["ko", "en"], fontScales = [0.85f, 1.5f])
@Composable
fun PreviewVariantCard(text: String) {
    Text(text = text)
}
