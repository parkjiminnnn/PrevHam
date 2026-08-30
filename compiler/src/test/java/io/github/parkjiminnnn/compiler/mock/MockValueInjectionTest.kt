package io.github.parkjiminnnn.compiler.mock

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// A value belongs to a slot - where it is declared - not to a type. Festival.name and User.name are
// both String and want different answers, so the declaring path travels with the recursion and the
// leaf generator looks the slot up before falling back to its default.
@OptIn(ExperimentalCompilerApi::class)
class MockValueInjectionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun generate(
        name: String,
        declarations: String,
        parameter: String,
        values: String? = null,
    ): String {
        val options =
            values?.let {
                val file = folder.newFile("mock-values.json").apply { writeText(it) }
                mapOf("prevham.mockValues" to file.absolutePath)
            } ?: emptyMap()
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
                options = options,
            )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        return requireNotNull(result.generatedFile("${name}Preview.kt")) { result.messages }
    }

    @Test
    fun `uses the configured value for a data class field`() {
        val generated =
            generate(
                "Festival",
                "data class Festival(val festivalName: String)",
                "festival: Festival",
                values = """{"test.Festival.festivalName": "2026 대동제"}""",
            )

        assertTrue(generated, generated.contains("""festivalName = "2026 대동제""""))
    }

    @Test
    fun `uses the configured value for the composable's own parameter`() {
        // A top-level parameter has no declaring class, so its slot is owned by the composable.
        val generated =
            generate("Greeting", "", "name: String", values = """{"test.Greeting.name": "김지민"}""")

        assertTrue(generated, generated.contains("""name = "김지민""""))
    }

    @Test
    fun `tells two same-typed slots apart`() {
        // The whole reason a slot is a declaring path rather than a type.
        val generated =
            generate(
                "Card",
                """
                data class Festival(val name: String)
                data class User(val name: String)
                data class Screen(val festival: Festival, val user: User)
                """,
                "screen: Screen",
                values =
                    """
                    {"test.Festival.name": "2026 대동제", "test.User.name": "김지민"}
                    """,
            )

        assertTrue(generated, generated.contains(""""2026 대동제""""))
        assertTrue(generated, generated.contains(""""김지민""""))
    }

    @Test
    fun `falls back to the default for a slot with no value`() {
        val generated =
            generate(
                "Partial",
                "data class Partial(val filled: String, val empty: String)",
                "partial: Partial",
                values = """{"test.Partial.filled": "real"}""",
            )

        assertTrue(generated, generated.contains("""filled = "real""""))
        assertTrue(generated, generated.contains("""empty = "mock""""))
    }

    @Test
    fun `changes nothing when no file is configured`() {
        val generated = generate("Plain", "data class Plain(val title: String)", "plain: Plain")

        assertTrue(generated, generated.contains("""title = "mock""""))
    }

    @Test
    fun `escapes a value that would not compile verbatim`() {
        // A hand-edited file is not a trusted source of Kotlin syntax. KotlinPoet's %S escapes the
        // value, so quotes and backslashes can't produce a literal that doesn't compile - and the
        // compilation succeeding here is the assertion.
        val generated =
            generate(
                "Quoted",
                "data class Quoted(val title: String)",
                "quoted: Quoted",
                values = """{"test.Quoted.title": "he said \"hi\"\\n"}""",
            )

        assertFalse(generated, generated.contains("""title = "he said "hi""""))
        assertTrue(generated, generated.contains("\\\""))
    }

    @Test
    fun `warns and keeps generating when the file cannot be read`() {
        // A typo in the path must not look like the values simply having no effect, and must not
        // fail the round either.
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "Missing.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class Missing(val title: String)

                    @Prev
                    @Composable
                    fun Missing(missing: Missing) {}
                    """,
                ),
                options = mapOf("prevham.mockValues" to "/nowhere/mock-values.json"),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        assertTrue(result.messages, result.messages.contains("could not read mock values"))
        val generated = requireNotNull(result.generatedFile("MissingPreview.kt"))
        assertTrue(generated, generated.contains("""title = "mock""""))
    }
}
