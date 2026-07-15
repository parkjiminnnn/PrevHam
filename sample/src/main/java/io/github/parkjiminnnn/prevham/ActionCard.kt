package io.github.parkjiminnnn.prevham

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

@Prev
@Composable
fun ActionCard(onClick: () -> Unit) {
    Text(text = "action")
}

@Prev
@Composable
fun ValidatorCard(validate: (String) -> Boolean) {
    Text(text = "validator: ${validate("test")}")
}
