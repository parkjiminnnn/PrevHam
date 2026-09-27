package io.github.parkjiminnnn.compiler.mock

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// A container from a compiled dependency - State, Lazy, LiveData, Optional - exists to be read
// through, and reading through it is what erases: `value` compiles to `Object getValue()`, relaxed
// mode answers from that, and the caller's checkcast rejects it. The search never reached them,
// because it stops at anything not declared in the sources being compiled (issue #80).
//
// It is opened one level, and only when a member the consumer wrote is holding it. Both limits are
// load-bearing: #84 had neither and turned one LocalDate field into 61 mocks (issue #87).
@OptIn(ExperimentalCompilerApi::class)
class CompiledContainerStubbingTest {
    private fun generate(
        declarations: String,
        parameter: String = "viewModel: ScreenViewModel",
    ): String {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "Screen.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev
                    import kotlinx.coroutines.flow.StateFlow

                    data class Item(val title: String)

                    $declarations

                    @Prev
                    @Composable
                    fun Screen($parameter) {}
                    """,
                ),
            )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        return requireNotNull(result.generatedFile("ScreenPreview.kt")) { result.messages }
    }

    private fun String.mocks() = split("mockk<").size - 1

    @Test
    fun `stubs a Lazy held by a member the consumer wrote`() {
        val generated = generate("interface ScreenViewModel { val holder: Lazy<Item> }")

        assertTrue(generated, generated.contains("every { this@mockk.holder } returns mockk<Lazy<Item>>(relaxed = true)"))
        assertTrue(generated, generated.contains("every { this@mockk.value } returns Item("))
    }

    @Test
    fun `stubs an Optional held by a member the consumer wrote`() {
        val generated = generate("interface ScreenViewModel { val maybe: java.util.Optional<Item> }")

        assertTrue(generated, generated.contains("mockk<Optional<Item>>(relaxed = true)"))
        assertTrue(generated, generated.contains("every { this@mockk.get() } returns Item("))
    }

    @Test
    fun `stubs an Iterator held by a member the consumer wrote`() {
        val generated = generate("interface ScreenViewModel { val items: Iterator<Item> }")

        assertTrue(generated, generated.contains("every { this@mockk.next() } returns Item("))
    }

    @Test
    fun `does not open a compiled type nothing is read out of`() {
        // Comparator<T> takes T and returns Int. Nothing is ever read back as T, so there is
        // nothing to erase and no reason to descend.
        val generated = generate("interface ScreenViewModel { val order: Comparator<Item> }")

        assertEquals(generated, 1, generated.mocks())
        assertFalse(generated, generated.contains("every {"))
    }

    @Test
    fun `does not open a compiled type held by a compiled type's own member`() {
        // The #87 path. LocalDate.datesUntil() hands back a Stream, and from there Stream, Optional
        // and Iterator reference each other - but nobody wrote `datesUntil()`, so it stays shut.
        val generated = generate("interface ScreenViewModel { val startDate: java.time.LocalDate }")

        assertEquals(generated, 1, generated.mocks())
        assertFalse(generated, generated.contains("Stream<"))
    }

    @Test
    fun `opens a dense compiled generic exactly one level`() {
        // Stream is the type that multiplied in #87: around forty members, several handing back a
        // Stream, Optional or Iterator of the same element. Held by a member the consumer wrote it
        // is opened - and stops there, because those members are Stream's own.
        val generated = generate("interface ScreenViewModel { val items: java.util.stream.Stream<Item> }")

        assertEquals(generated, 2, generated.mocks())
        assertFalse(generated, generated.contains("Optional<"))
    }

    @Test
    fun `leaves a two-hop compiled container alone`() {
        // Sequence reaches T through Iterator, so finding it would mean walking from one compiled
        // type into another - the shape that exploded. Deliberately out of scope; a value read this
        // way still throws at render time.
        val generated = generate("interface ScreenViewModel { val items: Sequence<Item> }")

        assertEquals(generated, 1, generated.mocks())
    }

    @Test
    fun `still stubs a Flow, which no search would find`() {
        // Flow's T never appears in a return type - it arrives through the collector - so the
        // structural rule finds nothing inside it however wide the door is opened. It stays named.
        val generated = generate("interface ScreenViewModel { val state: StateFlow<Item> }")

        assertTrue(generated, generated.contains("every { this@mockk.state } returns MutableStateFlow("))
    }

    @Test
    fun `still expands a container the consumer declared themselves`() {
        val generated =
            generate(
                """
                interface Box<T> { val value: T }
                interface ScreenViewModel { val box: Box<Item> }
                """,
            )

        assertTrue(generated, generated.contains("every { this@mockk.value } returns Item("))
    }
}
