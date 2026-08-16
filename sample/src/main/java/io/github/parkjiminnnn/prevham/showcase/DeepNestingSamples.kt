package io.github.parkjiminnnn.prevham.showcase

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.parkjiminnnn.runtime.Prev

// Recursion is bounded by cycle detection rather than a depth counter, so a finite model is built
// out however many levels it has. This shape - a UI state wrapping a domain object, wrapping
// another, wrapping a list - is the one issue #60 was reported for; the old MAX_DEPTH = 3 gave up
// at `posters` and skipped the whole Preview.

sealed interface OrganizationUiState {
    data class Success(
        val organization: Organization,
    ) : OrganizationUiState
}

data class Organization(
    val id: Long,
    val universityName: String,
    val festival: Festival,
)

data class Festival(
    val festivalName: String,
    val posters: List<Poster>,
)

data class Poster(
    val id: Long,
    val imageUrl: String,
    val sequence: Int,
)

// A type that would contain itself stops where it repeats instead of failing: `next` can't be
// expanded, but it's nullable, so the rest of the node is still built.
data class Node(
    val value: Int,
    val next: Node?,
)

@Prev
@Composable
fun OrganizationCard(state: OrganizationUiState) {
    Text(text = state.toString())
}

@Prev
@Composable
fun NodeCard(node: Node) {
    Text(text = node.toString())
}
