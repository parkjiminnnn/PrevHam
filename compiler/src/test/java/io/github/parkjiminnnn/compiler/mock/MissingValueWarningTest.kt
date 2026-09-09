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

// A value file goes stale on its own: a field added after it was written keeps its default, the
// build succeeds, and nothing says the generation task should be run again. These are about the
// build saying so - never about it going and fixing it.
@OptIn(ExperimentalCompilerApi::class)
class MissingValueWarningTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun compile(
        fields: String,
        values: String? = null,
        valuesPath: String? = null,
        options: Map<String, String> = emptyMap(),
    ): String {
        val path =
            valuesPath
                ?: values?.let { folder.newFile("mock-values.json").apply { writeText(it) }.absolutePath }
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "Festival.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class Festival($fields)

                    @Prev
                    @Composable
                    fun Festival(festival: Festival) {}
                    """,
                ),
                options =
                    buildMap {
                        path?.let { put("prevham.mockValues", it) }
                        putAll(options)
                    },
            )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        return result.messages
    }

    @Test
    fun `names the slots the value file has nothing for`() {
        val messages =
            compile(
                "val festivalName: String, val slogan: String",
                values = """{"test.Festival.festivalName": "2026 대동제"}""",
            )

        assertTrue(messages, messages.contains("1 slot(s) have no mock value"))
        assertTrue(messages, messages.contains("test.Festival.slogan"))
        // The path out has to travel with the complaint - a warning that only says something is
        // missing leaves the reader to find the task name themselves.
        assertTrue(messages, messages.contains("prevhamGenerateMockValues"))
        assertFalse(messages, messages.contains("festivalName"))
    }

    @Test
    fun `says nothing when every slot has a value`() {
        val messages =
            compile(
                "val festivalName: String",
                values = """{"test.Festival.festivalName": "2026 대동제"}""",
            )

        assertFalse(messages, messages.contains("no mock value"))
    }

    @Test
    fun `treats a blank value as undecided`() {
        // The generation task scaffolds missing slots with "" so the paths are there to fill in.
        // Those are precisely the slots still waiting on someone, so they belong in the warning -
        // the same rule that keeps a blank from reaching a Preview.
        val messages = compile("val slogan: String", values = """{"test.Festival.slogan": ""}""")

        assertTrue(messages, messages.contains("test.Festival.slogan"))
    }

    @Test
    fun `says nothing when no value file is configured`() {
        val messages = compile("val slogan: String")

        assertFalse(messages, messages.contains("no mock value"))
    }

    @Test
    fun `says nothing when the configured file does not exist yet`() {
        // The state of every project that has applied the plugin and not yet run the generation
        // task. The plugin always sets the path, so warning here would greet every new consumer with
        // a complaint about a file they have never heard of.
        val messages =
            compile("val slogan: String", valuesPath = folder.root.resolve("absent.json").absolutePath)

        assertFalse(messages, messages.contains("no mock value"))
        assertFalse(messages, messages.contains("could not read mock values"))
    }

    @Test
    fun `says nothing when switched off`() {
        val messages =
            compile(
                "val slogan: String",
                values = """{"test.Festival.festivalName": "2026 대동제"}""",
                options = mapOf("prevham.warnOnMissingValues" to "false"),
            )

        assertFalse(messages, messages.contains("no mock value"))
    }

    @Test
    fun `truncates a long list`() {
        // Adopting this mid-project leaves every slot undecided on the first build. A wall of paths
        // helps nobody; the count and the task name are what is worth reading there.
        val fields = (1..25).joinToString(", ") { "val field$it: String" }
        val messages = compile(fields, values = "{}")

        assertTrue(messages, messages.contains("25 slot(s) have no mock value"))
        assertTrue(messages, messages.contains("... and 15 more"))
    }
}
