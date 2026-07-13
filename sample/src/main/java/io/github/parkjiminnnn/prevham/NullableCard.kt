package io.github.parkjiminnnn.prevham

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

@Prev
@Composable
fun NullableCard(
    name: String?,
    onClick: (() -> Unit)?,
) {
    Text(text = name ?: "no name")
}
