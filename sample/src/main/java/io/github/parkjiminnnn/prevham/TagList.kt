package io.github.parkjiminnnn.prevham

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

@Prev
@Composable
fun TagList(tags: List<String>) {
    Text(text = tags.joinToString())
}

@Prev
@Composable
fun UserList(users: List<User>) {
    Text(text = users.size.toString())
}

@Prev
@Composable
fun ScoreBoard(scores: Map<String, Int>) {
    Text(text = scores.toString())
}

@Prev
@Composable
fun StatusList(statuses: List<Status>) {
    Text(text = statuses.toString())
}
