package io.github.parkjiminnnn.prevham

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

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
    Text(text = status.name)
}

@Prev
@Composable
fun TaskCard(task: Task) {
    Text(text = "${task.title}: ${task.status}")
}
