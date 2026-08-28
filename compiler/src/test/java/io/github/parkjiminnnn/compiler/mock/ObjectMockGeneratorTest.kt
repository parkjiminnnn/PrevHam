package io.github.parkjiminnnn.compiler.mock

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// An object is its own value: the reference is the whole answer, with no constructor to run and no
// fields to invent. Nothing claimed ClassKind.OBJECT, so a Preview taking one was skipped - and a
// data object was worse, claimed by DataClassMockGenerator on its DATA modifier and emitted as a
// constructor call that doesn't compile (issue #77).
@OptIn(ExperimentalCompilerApi::class)
class ObjectMockGeneratorTest {
    private fun generate(
        name: String,
        declarations: String,
        parameter: String,
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
    fun `references an object rather than skipping it`() {
        val generated = generate("Tracked", "object AnalyticsTracker", "tracker: AnalyticsTracker")

        assertTrue(generated, generated.contains("tracker = AnalyticsTracker,"))
    }

    @Test
    fun `references a data object rather than calling a constructor`() {
        // A data object carries Modifier.DATA and has a synthesised zero-parameter constructor, so
        // DataClassMockGenerator's "every parameter can be mocked" was vacuously true and it emitted
        // Loading(). Compiling at all is the assertion.
        val generated = generate("Stated", "data object Loading", "state: Loading")

        assertTrue(generated, generated.contains("state = Loading,"))
    }

    @Test
    fun `references a data object nested in a sealed hierarchy`() {
        val generated =
            generate(
                "SealedMember",
                """
                sealed interface UiState {
                    data object Loading : UiState
                    data class Success(val title: String) : UiState
                }
                """,
                "state: UiState.Loading",
            )

        assertTrue(generated, generated.contains("state = UiState.Loading,"))
    }

    @Test
    fun `references an object nested in a class`() {
        val generated = generate("Nested", "class Outer { object Inner }", "inner: Outer.Inner")

        assertTrue(generated, generated.contains("inner = Outer.Inner,"))
    }

    @Test
    fun `references a companion object`() {
        val generated = generate("Companion", "class Host { companion object }", "host: Host.Companion")

        assertTrue(generated, generated.contains("host = Host.Companion,"))
    }

    @Test
    fun `references a named companion object`() {
        val generated = generate("NamedCompanion", "class Host { companion object Registry }", "host: Host.Registry")

        assertTrue(generated, generated.contains("host = Host.Registry,"))
    }

    @Test
    fun `references an object that implements an interface`() {
        val generated =
            generate(
                "Fake",
                """
                interface Repo { val name: String }
                object FakeRepo : Repo { override val name = "fake" }
                """,
                "repo: FakeRepo",
            )

        assertTrue(generated, generated.contains("repo = FakeRepo,"))
    }

    @Test
    fun `prefers the object over null for a nullable parameter`() {
        // NullableFallbackMockGenerator is last for exactly this reason: null is the answer when
        // nothing else serves, not the first one.
        val generated = generate("Optional", "object AnalyticsTracker", "tracker: AnalyticsTracker?")

        assertTrue(generated, generated.contains("tracker = AnalyticsTracker,"))
    }

    @Test
    fun `references an object held as a data class field`() {
        // A field reaches the generators through the constructor's parameter list, and one
        // unsupported parameter skipped the whole Preview.
        val generated =
            generate(
                "Held",
                """
                object AnalyticsTracker
                data class Screen(val title: String, val tracker: AnalyticsTracker)
                """,
                "screen: Screen",
            )

        assertTrue(generated, generated.contains("tracker = AnalyticsTracker,"))
    }

    @Test
    fun `stubs an object-typed member so the mock yields the singleton`() {
        // Relaxed mode builds a new instance through Objenesis, so `vm.state === Loading` and
        // `when (vm.state) { Loading -> ... }` are both false against it. Relaxed mode can't produce
        // the right value, which is the same reason members are stubbed at all - and the stub is a
        // plain reference, so it adds no recursion.
        val generated =
            generate(
                "Holder",
                """
                data object Loading
                interface HolderViewModel { val state: Loading }
                """,
                "viewModel: HolderViewModel",
            )

        assertTrue(generated, generated.contains("every { this@mockk.state } returns Loading"))
    }
}
