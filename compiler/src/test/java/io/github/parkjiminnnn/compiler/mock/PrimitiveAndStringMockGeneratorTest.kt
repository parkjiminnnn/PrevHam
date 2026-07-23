package io.github.parkjiminnnn.compiler.mock

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCompilerApi::class)
class PrimitiveAndStringMockGeneratorTest {
    @Test
    fun `generates literal mocks for primitive and string parameters`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "Card.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    @Prev
                    @Composable
                    fun Card(
                        count: Int,
                        price: Double,
                        ratio: Float,
                        enabled: Boolean,
                        initial: Char,
                        label: String,
                    ) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)

        val generated = requireNotNull(result.generatedFile("CardPreview.kt"))
        assertTrue(generated.contains("count = 1"))
        assertTrue(generated.contains("price = 1.0"))
        assertTrue(generated.contains("ratio = 1f"))
        assertTrue(generated.contains("enabled = true"))
        assertTrue(generated.contains("initial = 'a'"))
        assertTrue(generated.contains("label = \"mock\""))
    }
}
