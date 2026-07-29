package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

// CollectionMockGenerator emits listOf/setOf/mapOf with a single element, resolving the element
// type through the registry - so a collection of data classes or enums works, and nesting
// (List<List<Int>>) recurses one level deeper.

@Prev
@Composable
fun TagList(tags: List<String>) {
    Text(text = tags.joinToString())
}

@Prev
@Composable
fun UserList(users: List<User>) {
    Text(text = users.joinToString { it.name })
}

@Prev
@Composable
fun StatusList(statuses: List<Status>) {
    Text(text = statuses.joinToString { it.name })
}

@Prev
@Composable
fun ScoreBoard(scores: Map<String, Int>) {
    Text(text = scores.entries.joinToString { "${it.key}=${it.value}" })
}

@Prev
@Composable
fun MatrixCard(rows: List<List<Int>>) {
    Text(text = rows.toString())
}
