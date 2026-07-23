package io.github.parkjiminnnn.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCompilerApi::class)
class PrevSymbolProcessorTest {
    @Test
    fun `rejects a Prev function that is not also Composable`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "NotComposable.kt",
                    """
                    package test
                    import io.github.parkjiminnnn.runtime.Prev

                    @Prev
                    fun notComposable() {}
                    """,
                ),
            )

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("@Prev can only be applied to a @Composable function"))
    }

    @Test
    fun `generates a single plain Preview when no variant options are requested`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "PlainCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    @Prev
                    @Composable
                    fun PlainCard(text: String) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("PlainCardPreview.kt"))
        assertEquals(1, Regex("@Preview").findAll(generated).count())
        assertFalse(generated.contains("uiMode"))
    }

    @Test
    fun `stacks one Preview per requested darkMode, locale, and fontScale variant`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "VariantCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    @Prev(darkMode = true, locales = ["ko", "en"], fontScales = [0.85f, 1.5f])
                    @Composable
                    fun VariantCard(text: String) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("VariantCardPreview.kt"))

        // 1 default + 1 dark mode + 2 locales + 2 font scales = 6 stacked @Preview annotations.
        assertEquals(6, Regex("@Preview").findAll(generated).count())
        assertTrue(generated.contains("uiMode = Configuration.UI_MODE_NIGHT_YES"))
        assertTrue(generated.contains("locale = \"ko\""))
        assertTrue(generated.contains("locale = \"en\""))
        assertTrue(generated.contains("fontScale = 0.85f"))
        assertTrue(generated.contains("fontScale = 1.5f"))
    }
}
