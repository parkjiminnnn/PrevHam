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

// MockKMatcherScope declares members of its own - get, invoke, less and hint among them - and inside
// every { } it is the innermost receiver, so an unqualified member name resolves against MockK's
// rather than the mock's and the generated code doesn't compile (issue #83). Whether a name actually
// collides depends on its arity and parameter types too, so stubs name their receiver instead of
// avoiding a list of names. `this@mockk` labels the lambda passed to mockk(), whose receiver is
// declared T.() -> Unit, so it is the mock - and in a nested mock it binds to the nearest enclosing
// one.
//
// The generated arguments for the two parameters below:
//
//     repository = mockk<KeyedRepository<User>>(relaxed = true) {
//         every { this@mockk.get(any()) } returns User(id = 1, name = "mock", age = 1)
//     },
//     holder = mockk<RepositoryHolder>(relaxed = true) {
//         every { this@mockk.repository } returns mockk<KeyedRepository<User>>(relaxed = true) {
//             every { this@mockk.get(any()) } returns User(id = 1, name = "mock", age = 1)
//         }
//     },
//
// GeneratedMockValueTest in src/test runs both, including the nested one - a stub bound to the wrong
// receiver would still compile, so only running it shows which mock it landed on.

interface KeyedRepository<T> {
    fun get(key: String): T
}

interface RepositoryHolder {
    val repository: KeyedRepository<User>
}

@Prev
@Composable
fun KeyedRepoCard(repository: KeyedRepository<User>) {
    Text(text = repository.get("key").name)
}

@Prev
@Composable
fun NestedRepoCard(holder: RepositoryHolder) {
    Text(text = holder.repository.get("key").name)
}
