package io.github.parkjiminnnn.prevham

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.parkjiminnnn.runtime.Prev

interface ImageLoader {
    fun load(url: String)
}

@Prev
@Composable
fun IconButtonCard(
    modifier: Modifier,
    loader: ImageLoader,
) {
    Text(text = "icon", modifier = modifier)
}
