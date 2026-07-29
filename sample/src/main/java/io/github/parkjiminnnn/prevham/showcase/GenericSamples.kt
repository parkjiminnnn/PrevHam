package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

// Type arguments are resolved through KSFunctionDeclaration.asMemberOf, so `Box<String>`'s
// constructor parameter is seen as String rather than the type parameter T. Generic interfaces and
// plain classes carry their arguments into the emitted mock (mockk<Repository<String>>(...)),
// which a raw type name would have dropped.

data class Box<T>(
    val value: T,
)

interface Repository<T> {
    fun get(): T
}

class Logger<T>

@Prev
@Composable
fun BoxCard(box: Box<String>) {
    Text(text = box.value)
}

@Prev
@Composable
fun NestedBoxCard(box: Box<User>) {
    Text(text = box.value.name)
}

@Prev
@Composable
fun RepoCard(repository: Repository<String>) {
    Text(text = "repo")
}

@Prev
@Composable
fun LoggerCard(logger: Logger<String>) {
    Text(text = "logger")
}
