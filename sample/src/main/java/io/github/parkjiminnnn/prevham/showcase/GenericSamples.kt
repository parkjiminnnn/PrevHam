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

// A generic type from a compiled dependency erases exactly the way a declared one does - Lazy<T>.value
// is `T`, the same as Box<T>.value. What differs is that the stub search will not look inside a
// compiled type on its own, so a Lazy held behind a mock used to be left to relaxed mode and read
// back as a bare Object (issue #80). It is entered now because it is generic; a compiled type with no
// type argument, such as java.time.LocalDate, still is not - entering those is what made generation
// explode in issue #75.
//
// The generated argument for the parameter below:
//
//     mockk<UserHolder>(relaxed = true) {
//         every { user } returns mockk<Lazy<User>>(relaxed = true) {
//             every { value } returns User(id = 1, name = "mock", age = 1)
//         }
//     }
//
// GeneratedMockValueTest in src/test asserts the value that expression produces really is a User.

interface UserHolder {
    val user: Lazy<User>
}

@Prev
@Composable
fun HeldUserCard(holder: UserHolder) {
    Text(text = holder.user.value.name)
}
