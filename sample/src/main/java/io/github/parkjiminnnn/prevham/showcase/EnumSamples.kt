package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

// EnumMockGenerator picks the first declared entry, so the mock is always a valid constant rather
// than a synthesized value - which matters for exhaustive `when` branches in the composable.

enum class Status {
    ACTIVE,
    INACTIVE,
    PENDING,
}

data class Task(
    val title: String,
    val status: Status,
)

@Prev
@Composable
fun StatusBadge(status: Status) {
    Text(
        text =
            when (status) {
                Status.ACTIVE -> "Active"
                Status.INACTIVE -> "Inactive"
                Status.PENDING -> "Pending"
            },
    )
}

@Prev
@Composable
fun TaskCard(task: Task) {
    Text(text = "${task.title}: ${task.status}")
}
