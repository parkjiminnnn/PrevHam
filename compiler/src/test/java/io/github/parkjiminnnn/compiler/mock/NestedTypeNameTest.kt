package io.github.parkjiminnnn.compiler.mock

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// A type declared inside another class has to be written through its enclosing classes. Three
// generators built its name from packageName plus simpleName instead, which compiles only for
// top-level types - and since the file that fails is generated, the consumer's whole build stops on
// an error pointing into build/generated (issue #118).
//
// Every test here compiles the generated output. That is the assertion that matters: the broken
// names were plausible-looking text that simply did not resolve.
@OptIn(ExperimentalCompilerApi::class)
class NestedTypeNameTest {
    private fun generate(
        declarations: String,
        parameter: String,
    ): String {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "Card.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    $declarations

                    @Prev
                    @Composable
                    fun Card($parameter) {}
                    """,
                ),
            )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        return requireNotNull(result.generatedFile("CardPreview.kt")) { result.messages }
    }

    @Test
    fun `names a nested enum through its enclosing class`() {
        // The Android shape: a tab or filter declared inside the screen that uses it.
        val generated = generate("class FestivalScreen { enum class Tab { LINEUP, MAP } }", "tab: FestivalScreen.Tab")

        assertTrue(generated, generated.contains("tab = FestivalScreen.Tab.LINEUP"))
    }

    @Test
    fun `names a nested interface through its enclosing class`() {
        val generated = generate("class FestivalScreen { interface Listener }", "listener: FestivalScreen.Listener")

        assertTrue(generated, generated.contains("mockk<FestivalScreen.Listener>(relaxed = true)"))
    }

    @Test
    fun `names a nested abstract class through its enclosing class`() {
        val generated = generate("class FestivalScreen { abstract class Loader }", "loader: FestivalScreen.Loader")

        assertTrue(generated, generated.contains("mockk<FestivalScreen.Loader>(relaxed = true)"))
    }

    @Test
    fun `names an inner class through its enclosing class`() {
        val generated = generate("class Outer { inner class Inner(val x: String) }", "inner: Outer.Inner")

        assertTrue(generated, generated.contains("mockk<Outer.Inner>(relaxed = true)"))
    }

    @Test
    fun `names a type nested more than one level deep`() {
        val generated =
            generate(
                "class App { class Festival { enum class Stage { MAIN, SUB } } }",
                "stage: App.Festival.Stage",
            )

        assertTrue(generated, generated.contains("stage = App.Festival.Stage.MAIN"))
    }

    @Test
    fun `names a nested type used as a type argument`() {
        // The case a top-level test would never find: Item itself is a data class and is named
        // correctly as a parameter, but here it is reached through the mock's type-argument
        // recursion, which used the broken name builder too.
        val generated =
            generate(
                """
                class Screen { data class Item(val title: String) }
                interface Repository<T> { fun first(): T }
                """,
                "repository: Repository<Screen.Item>",
            )

        assertTrue(generated, generated.contains("mockk<Repository<Screen.Item>>(relaxed = true)"))
    }

    @Test
    fun `names a nested generic type with its argument`() {
        val generated =
            generate(
                """
                data class Item(val title: String)
                class Screen { interface Source<T> { fun first(): T } }
                """,
                "source: Screen.Source<Item>",
            )

        assertTrue(generated, generated.contains("mockk<Screen.Source<Item>>(relaxed = true)"))
    }

    @Test
    fun `names a nested self-implementing companion through its enclosing class`() {
        // The Modifier shape - an interface whose companion implements it - declared inside another
        // class. The companion is referenced through the interface's own name.
        val generated =
            generate(
                """
                class Screen {
                    interface Style { companion object : Style }
                }
                """,
                "style: Screen.Style",
            )

        assertTrue(generated, generated.contains("style = Screen.Style"))
    }
}
