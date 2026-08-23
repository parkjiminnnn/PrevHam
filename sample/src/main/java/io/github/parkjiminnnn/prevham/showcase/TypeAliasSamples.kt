package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev
import kotlinx.coroutines.flow.StateFlow

// A typealias is a second name for a type, not a type of its own, so a parameter written with one
// reaches the same generator the underlying type would. KSP reports it as a KSTypeAlias declaration
// rather than a class, and every generator narrows to a class - so before issue #81 an aliased
// parameter matched nothing and the whole Preview was skipped.
//
// This was never limited to aliases written by hand: kotlin.Comparator is an alias for
// java.util.Comparator, so the same parameter was supported or not depending on which name it was
// spelled with.
//
// Aliases are resolved once, where a type enters the pipeline, so the generators only ever see real
// class declarations.

typealias OnSelect = (Int) -> Unit

typealias UserName = String

typealias Users = StateFlow<User>

interface UserFeed {
    val users: Users
}

@Prev
@Composable
fun AliasedLambdaCard(onSelect: OnSelect) {
    Text(text = "select")
}

@Prev
@Composable
fun AliasedPrimitiveCard(name: UserName) {
    Text(text = name)
}

// An alias for a generic type carries the use site's type argument onto the alias's own parameter:
// PagedList<User> becomes List<User>, not List<T>.

typealias PagedList<T> = List<T>

@Prev
@Composable
fun AliasedListCard(users: PagedList<User>) {
    Text(text = users.joinToString { it.name })
}

// An alias in front of a Flow has to be resolved before the member is stubbed too, or the mock
// would be left to relaxed mode and `.value` would come back as a bare Object.

@Prev
@Composable
fun AliasedFeedCard(feed: UserFeed) {
    Text(text = feed.users.value.name)
}
