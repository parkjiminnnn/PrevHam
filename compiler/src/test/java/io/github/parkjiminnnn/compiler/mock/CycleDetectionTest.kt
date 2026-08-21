package io.github.parkjiminnnn.compiler.mock

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Recursion is bounded by MockContext tracking the types being expanded on the current path, not by
// counting how deep that path has gone. A type that would expand into itself stops; anything finite
// runs to completion however deeply it nests.
@OptIn(ExperimentalCompilerApi::class)
class CycleDetectionTest {
    @Test
    fun `nests as deeply as the model does`() {
        // The shape reported in issue #60, which the old MAX_DEPTH = 3 rejected at List<Poster>:
        // Success -> Organization -> Festival -> List<Poster> -> Poster. No cycle anywhere in it.
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "FestivalCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    sealed interface FestivalUiState {
                        data class Success(val organization: Organization) : FestivalUiState
                    }
                    data class Organization(val id: Long, val universityName: String, val festival: Festival)
                    data class Festival(val festivalName: String, val festivalImages: List<Poster>)
                    data class Poster(val id: Long, val imageUrl: String, val sequence: Int)

                    @Prev
                    @Composable
                    fun FestivalCard(state: FestivalUiState) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("FestivalCardPreview.kt"))
        assertTrue(generated, generated.contains("FestivalUiState.Success("))
        assertTrue(generated, generated.contains("organization = Organization("))
        assertTrue(generated, generated.contains("festival = Festival("))
        assertTrue(generated, generated.contains("festivalImages = listOf(Poster("))
        assertTrue(generated, generated.contains("sequence = 1"))
    }

    @Test
    fun `falls back to null where a nullable field would revisit its own type`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "NodeCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class Node(val value: Int, val next: Node?)

                    @Prev
                    @Composable
                    fun NodeCard(node: Node) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("NodeCardPreview.kt"))
        // Stopping the recursion must cost as little as possible: `next` can't be expanded, but a
        // nullable field still has an answer, so the rest of Node is built normally.
        assertTrue(generated, generated.contains("value = 1"))
        assertTrue(generated, generated.contains("next = null"))
    }

    @Test
    fun `skips a data class that requires an instance of itself`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "InfiniteCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class Node(val next: Node)

                    @Prev
                    @Composable
                    fun InfiniteCard(node: Node) {}
                    """,
                ),
            )

        // No value of this type can be constructed at all - not by PrevHam, and not by hand either.
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        assertTrue(result.messages.contains("no mock generator available for parameter 'node'"))
        assertNull(result.generatedFile("InfiniteCardPreview.kt"))
    }

    @Test
    fun `stops a self-referential interface without failing it`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "ChainCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    interface Node { val next: Node }

                    @Prev
                    @Composable
                    fun ChainCard(node: Node) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("ChainCardPreview.kt"))
        // `next` returns a concrete type, which relaxed mode answers on its own, so it isn't stubbed
        // and the chain never starts. Were it erased, the cycle guard is what would stop it - see
        // `bounds a self-referential chain of stubbed members`.
        assertEquals(generated, 1, generated.split("mockk<Node>(relaxed = true)").size - 1)
        assertFalse(generated, generated.contains("every { this@mockk.next }"))
    }

    @Test
    fun `bounds a self-referential chain of stubbed members`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "LoopCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev
                    import kotlinx.coroutines.flow.StateFlow

                    data class Item(val title: String)
                    interface Node {
                        val next: Node
                        val items: StateFlow<Item>
                    }

                    @Prev
                    @Composable
                    fun LoopCard(node: Node) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("LoopCardPreview.kt"))
        // `next` leads to an erased member so it is stubbed, which makes the type expand into
        // itself. Re-entering Node is what stops it, one level down, with a mock that stubs nothing.
        assertEquals(generated, 2, generated.split("mockk<Node>(relaxed = true)").size - 1)
        assertEquals(generated, 1, generated.split("every { this@mockk.next }").size - 1)
    }

    @Test
    fun `breaks an indirect cycle between two types`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "MutualCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class A(val b: B)
                    data class B(val a: A?)

                    @Prev
                    @Composable
                    fun MutualCard(a: A) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("MutualCardPreview.kt"))
        // A doesn't reference itself directly - the path has to be tracked across types, not just
        // compared against the type currently being expanded.
        assertTrue(generated, generated.contains("a = A("))
        assertTrue(generated, generated.contains("b = B("))
        assertTrue(generated, generated.contains("a = null"))
    }

    @Test
    fun `skips an indirect cycle with no nullable link to break it`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "UnbreakableCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class A(val b: B)
                    data class B(val a: A)

                    @Prev
                    @Composable
                    fun UnbreakableCard(a: A) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        assertTrue(result.messages.contains("no mock generator available for parameter 'a'"))
        assertNull(result.generatedFile("UnbreakableCardPreview.kt"))
    }

    @Test
    fun `empties a collection whose element type would close a cycle`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "TreeCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class Tree(val label: String, val children: List<Tree>)

                    @Prev
                    @Composable
                    fun TreeCard(tree: Tree) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("TreeCardPreview.kt"))
        // An empty collection is a real value, and the leaf of any such tree, so the cycle costs
        // this one field rather than the whole Preview.
        assertTrue(generated, generated.contains("""label = "mock""""))
        assertTrue(generated, generated.contains("children = listOf()"))
    }

    @Test
    fun `breaks a cycle running through a function return type`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "LambdaCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class Node(val next: Node?)

                    @Prev
                    @Composable
                    fun LambdaCard(onNode: () -> Node) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("LambdaCardPreview.kt"))
        assertTrue(generated, generated.contains("onNode = { Node("))
        assertTrue(generated, generated.contains("next = null"))
    }

    @Test
    fun `stops a generic type that grows on every expansion`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "WrapperCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class Item(val title: String)
                    data class Wrapper<T>(val inner: Wrapper<Wrapper<T>>)

                    @Prev
                    @Composable
                    fun WrapperCard(w: Wrapper<Item>) {}
                    """,
                ),
            )

        // The one shape cycle detection can't see: each step produces a type never encountered
        // before, so no path key ever repeats. No value of this type exists in any code either, but
        // the declaration is legal, and MAX_PATH_LENGTH is what keeps it from running forever. This
        // test finishing at all is the assertion; the rest just pins the failure mode.
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        assertTrue(result.messages.contains("no mock generator available for parameter 'w'"))
        assertNull(result.generatedFile("WrapperCardPreview.kt"))
    }

    @Test
    fun `does not mistake a repeated type constructor for a cycle`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "NestedBoxCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class Item(val title: String)
                    interface Box<T> { val item: T }

                    @Prev
                    @Composable
                    fun NestedBoxCard(box: Box<Box<Item>>) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("NestedBoxCardPreview.kt"))
        // Box<Box<Item>> and Box<Item> share a declaration but are different types and the nesting
        // is finite, so the path key has to carry type arguments or this would be rejected.
        assertTrue(generated, generated.contains("mockk<Box<Box<Item>>>(relaxed = true) {"))
        assertTrue(generated, generated.contains("mockk<Box<Item>>(relaxed = true) {"))
        assertTrue(generated, generated.contains("""every { this@mockk.item } returns Item("""))
    }
}
