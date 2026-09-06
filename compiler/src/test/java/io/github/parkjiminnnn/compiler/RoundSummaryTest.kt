package io.github.parkjiminnnn.compiler

import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// A build gave no summary of what @Prev produced, so there was no telling a project with few
// Previews from one where most were being skipped - and those call for different responses.
@OptIn(ExperimentalCompilerApi::class)
class RoundSummaryTest {
    private fun compile(body: String): String =
        compilePrev(
            SourceFile.kotlin(
                "Sources.kt",
                """
                package test
                import androidx.compose.runtime.Composable
                import io.github.parkjiminnnn.runtime.Prev

                $body
                """,
            ),
        ).messages

    @Test
    fun `gathers the skipped ones into the summary`() {
        val messages =
            compile(
                """
                data class Node(val next: Node)
                data class Plain(val title: String)

                @Prev @Composable fun Good(plain: Plain) {}
                @Prev @Composable fun Bad(node: Node) {}
                """,
            )

        assertTrue(messages, messages.contains("2 @Prev found: 1 generated, 1 skipped:"))
        assertTrue(messages, messages.contains("Bad: no mock generator available for parameter 'node'"))
    }

    @Test
    fun `no longer warns once per skip`() {
        // The scattering was the problem: one warning per skip, in a log that carries every other
        // warning a build produces, is easy to miss and impossible to count.
        val messages =
            compile(
                """
                data class Node(val next: Node)

                @Prev @Composable fun Bad(node: Node) {}
                """,
            )

        assertFalse(messages, messages.contains("skipping @Prev on"))
        assertTrue(messages, messages.contains("1 @Prev found: 0 generated, 1 skipped:"))
    }

    @Test
    fun `counts a Prev that no version of PrevHam could honour as failed`() {
        val messages =
            compile(
                """
                data class Plain(val title: String)

                @Prev @Composable fun Good(plain: Plain) {}
                @Prev fun NotComposable() {}
                """,
            )

        assertTrue(messages, messages.contains("2 @Prev found: 1 generated, 1 failed"))
        // The error still says which one and why; the summary only says how many.
        assertTrue(messages, messages.contains("is not annotated with @Composable"))
    }

    @Test
    fun `says nothing when there is no Prev to report on`() {
        val messages = compile("class Untouched")

        assertFalse(messages, messages.contains("@Prev found"))
    }

    @Test
    fun `a clean round is not a warning`() {
        // KSP has no level between info and warn, so a healthy build has to fall on the quiet side:
        // there is nothing wrong with it to warn about.
        val messages =
            compile(
                """
                data class Plain(val title: String)

                @Prev @Composable fun Good(plain: Plain) {}
                """,
            )

        assertFalse(messages, messages.contains("w: [ksp] [PrevHam] 1 @Prev found"))
    }
}
