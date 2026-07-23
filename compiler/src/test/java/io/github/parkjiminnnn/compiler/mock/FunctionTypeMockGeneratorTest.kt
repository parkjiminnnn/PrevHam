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
    fun `KNOWN LIMITATION - a parameter-less lambda body does not satisfy a 2+ arity function type`() {
        // FunctionTypeMockGenerator always emits a lambda with no declared parameter list
        // ("{ }" / "{ <value> }"), reasoning that a lambda body never needs to reference its
        // parameters. That's true for 0- and 1-parameter function types (Kotlin infers the single
        // implicit `it` even when unused), but for 2+ parameters Kotlin requires the lambda to at
        // least *name* every parameter - a bare "{ true }" is inferred as Function0<Boolean> and
        // fails to satisfy a Function2<String, Int, Boolean> target. This test documents the
        // current (broken) behavior rather than asserting success - see #37, filed from this
        // discovery. Once fixed, this test's expectations should flip to assert success.
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

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages.contains("Argument type mismatch"))
    }
}
