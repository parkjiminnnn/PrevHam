package io.github.parkjiminnnn.compiler.mock

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The dividing line is "can the constructor be called", not "is it a data class". The `data` keyword
// changes nothing about whether a value can be built (issue #78).
@OptIn(ExperimentalCompilerApi::class)
class ConstructibleTypeTest {
    private fun generate(
        declarations: String,
        parameter: String,
        name: String = "Card",
    ): String {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "$name.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    $declarations

                    @Prev
                    @Composable
                    fun $name($parameter) {}
                    """,
                ),
            )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        return requireNotNull(result.generatedFile("${name}Preview.kt")) { result.messages }
    }

    @Test
    fun `constructs a plain class instead of mocking it`() {
        val generated = generate("class Plain(val x: String, val n: Int)", "plain: Plain")

        assertTrue(generated, generated.contains("""Plain(\n      x = "mock",\n      n = 1,\n    )""".replace("\\n", "\n")))
    }

    @Test
    fun `constructs a value class`() {
        // The clearest case: the constructor is public and wraps exactly one value, so mocking it
        // buys nothing at all.
        val generated = generate("@JvmInline value class UserId(val raw: String)", "id: UserId")

        assertTrue(generated, generated.contains("""UserId(\n      raw = "mock",\n    )""".replace("\\n", "\n")))
    }

    @Test
    fun `still constructs a data class`() {
        val generated = generate("data class User(val name: String)", "user: User")

        assertTrue(generated, generated.contains("""name = "mock""""))
    }

    @Test
    fun `mocks a class with no constructor parameters`() {
        // Nothing to put in, so constructing carries exactly what a mock does while additionally
        // running whatever the class's init does. On Android this is the ViewModel shape.
        val generated = generate("class Logger", "logger: Logger")

        assertTrue(generated, generated.contains("mockk<Logger>(relaxed = true)"))
    }

    @Test
    fun `mocks an abstract class`() {
        val generated = generate("abstract class Loader(val url: String)", "loader: Loader")

        assertTrue(generated, generated.contains("mockk<Loader>(relaxed = true)"))
    }

    @Test
    fun `mocks a class whose constructor is private`() {
        val generated = generate("class Opaque private constructor(val x: String)", "opaque: Opaque")

        assertTrue(generated, generated.contains("mockk<Opaque>(relaxed = true)"))
    }

    @Test
    fun `mocks a class whose constructor is protected`() {
        val generated = generate("open class Base protected constructor(val x: String)", "base: Base")

        assertTrue(generated, generated.contains("mockk<Base>(relaxed = true)"))
    }

    @Test
    fun `constructs through an internal constructor in the same compilation`() {
        // Another module's internal would not compile; this one is being compiled right here.
        val generated = generate("class Session internal constructor(val token: String)", "session: Session")

        assertTrue(generated, generated.contains("""token = "mock""""))
    }

    @Test
    fun `mocks an inner class`() {
        // An inner class needs an enclosing instance the generated file has no way to produce, so it
        // must not be constructed.
        val generated =
            generate(
                """
                class Outer {
                    inner class Inner(val x: String)
                }
                """,
                "inner: Outer.Inner",
            )

        assertTrue(generated, generated.contains("mockk<Outer.Inner>(relaxed = true)"))
    }

    @Test
    fun `keeps building a real subtype for a sealed class`() {
        // SealedTypeMockGenerator is registered first, and a sealed class carries no instances of
        // its own anyway.
        val generated =
            generate(
                """
                sealed class UiState {
                    data class Success(val title: String) : UiState()
                }
                """,
                "state: UiState",
            )

        assertTrue(generated, generated.contains("UiState.Success("))
    }

    @Test
    fun `keeps referencing a data object rather than calling a constructor`() {
        // A data object carries Modifier.DATA and a synthesised zero-parameter constructor. The
        // parameter count now rules it out on its own, on top of the kind check (issue #77).
        val generated =
            generate(
                """
                sealed interface UiState { data object Loading : UiState }
                """,
                "state: UiState.Loading",
            )

        assertTrue(generated, generated.contains("UiState.Loading"))
        assertTrue(generated, !generated.contains("Loading()"))
    }

    @Test
    fun `constructs a generic class with its type argument substituted`() {
        val generated = generate("class Box<T>(val value: T)", "box: Box<String>")

        assertTrue(generated, generated.contains("""value = "mock""""))
    }

    @Test
    fun `takes a configured value through a constructed plain class`() {
        // The point of constructing: the field goes through the slot pipeline rather than being
        // answered by relaxed mode.
        val generated = generate("class Plain(val title: String)", "plain: Plain")

        assertTrue(generated, generated.contains("""title = "mock""""))
    }
}
