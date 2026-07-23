package io.github.parkjiminnnn.prevham

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

@Prev(darkMode = true, locales = ["ko", "en"], fontScales = [0.85f, 1.5f])
@Composable
fun PreviewVariantCard(text: String) {
    Text(text = text)
}
