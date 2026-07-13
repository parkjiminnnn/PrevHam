package io.github.parkjiminnnn.prevham

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.parkjiminnnn.runtime.Prev

@Prev
@Composable
fun UserCard(
    user: User,
    onClick: () -> Unit = {},
) {
    Text(
        text = "${user.name} (${user.age})",
        modifier = Modifier.padding(8.dp),
    )
}
