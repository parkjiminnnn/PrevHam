package io.github.parkjiminnnn.compiler.mock

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCompilerApi::class)
class InterfaceMockGeneratorTest {
    @Test
    fun `generates a relaxed MockK mock for a plain interface`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "IconButtonCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    interface ImageLoader { fun load(): String }

                    @Prev
                    @Composable
                    fun IconButtonCard(loader: ImageLoader) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("IconButtonCardPreview.kt"))
        assertTrue(generated.contains("mockk<ImageLoader>(relaxed = true)"))
    }

    @Test
    fun `generates a relaxed MockK mock for a non-data class`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "LoggerCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    class Logger

                    @Prev
                    @Composable
                    fun LoggerCard(logger: Logger) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("LoggerCardPreview.kt"))
        assertTrue(generated.contains("mockk<Logger>(relaxed = true)"))
    }

    @Test
    fun `carries generic type arguments into the mockk call`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "RepoCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    interface Repository<T> { fun get(): T }

                    @Prev
                    @Composable
                    fun RepoCard(repository: Repository<String>) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("RepoCardPreview.kt"))
        assertTrue(generated.contains("mockk<Repository<String>>(relaxed = true)"))
    }

    @Test
    fun `prefers a self-implementing companion instance over a MockK mock`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "ModifierCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import androidx.compose.ui.Modifier
                    import io.github.parkjiminnnn.runtime.Prev

                    @Prev
                    @Composable
                    fun ModifierCard(modifier: Modifier) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("ModifierCardPreview.kt"))
        assertTrue(generated.contains("modifier = Modifier"))
        assertFalse(generated.contains("mockk<Modifier>"))
    }
}
