package io.github.parkjiminnnn.compiler.mock

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCompilerApi::class)
class FunctionTypeMockGeneratorTest {
    @Test
    fun `generates an empty lambda for a Unit-returning function type`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "ActionCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    @Prev
                    @Composable
                    fun ActionCard(onClick: () -> Unit) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("ActionCardPreview.kt"))
        assertTrue(generated.contains("onClick = { }"))
    }

    @Test
    fun `recurses into the return type for a non-Unit, single-parameter function type`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "ValidatorCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    @Prev
                    @Composable
                    fun ValidatorCard(validate: (String) -> Boolean) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("ValidatorCardPreview.kt"))
        assertTrue(generated.contains("validate = { true }"))
    }

    @Test
    fun `names lambda parameters for 2+ arity function types`() {
        // Fixed in #37/#39: a lambda with no declared parameter list ("{ }" / "{ <value> }") only
        // type-checks for 0- and 1-parameter function types (Kotlin infers the single implicit
        // `it` even when unused). For 2+ parameters, Kotlin requires every parameter to at least
        // be *named*, so FunctionTypeMockGenerator prefixes the lambda with "_, _ -> " in that case.
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "ValidatorCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    @Prev
                    @Composable
                    fun ValidatorCard(validate: (String, Int) -> Boolean) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("ValidatorCardPreview.kt"))
        assertTrue(generated.contains("validate = { _, _ -> true }"))
    }
}
