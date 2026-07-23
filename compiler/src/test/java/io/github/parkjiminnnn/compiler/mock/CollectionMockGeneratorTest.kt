package io.github.parkjiminnnn.compiler.mock

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCompilerApi::class)
class CollectionMockGeneratorTest {
    @Test
    fun `generates listOf, setOf, and mapOf calls`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "TagCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    @Prev
                    @Composable
                    fun TagCard(tags: List<String>, ids: Set<Int>, scores: Map<String, Int>) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("TagCardPreview.kt"))
        assertTrue(generated.contains("tags = listOf(\"mock\")"))
        assertTrue(generated.contains("ids = setOf(1)"))
        assertTrue(generated.contains("scores = mapOf(\"mock\" to 1)"))
    }

    @Test
    fun `recurses into nested collection element types`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "MatrixCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    @Prev
                    @Composable
                    fun MatrixCard(matrix: List<List<Int>>) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("MatrixCardPreview.kt"))
        assertTrue(generated.contains("matrix = listOf(listOf(1))"))
    }
}
