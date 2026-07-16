package io.github.parkjiminnnn.prevham

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

data class Box<T>(
    val value: T,
)

interface Repository<T> {
    fun get(): T
}

@Prev
@Composable
fun BoxCard(box: Box<String>) {
    Text(text = box.value)
}

@Prev
@Composable
fun RepoCard(repository: Repository<String>) {
    Text(text = "repo")
}

class Logger<T>

@Prev
@Composable
fun LoggerCard(logger: Logger<String>) {
    Text(text = "logger")
}
