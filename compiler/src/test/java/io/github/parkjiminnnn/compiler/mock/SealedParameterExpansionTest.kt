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
import java.io.File

// A UI state is sealed because each subtype is a different screen. One Preview of whichever subtype
// sorted first left the rest unreachable short of writing them by hand, and the fixed sort read as a
// bug when reordering the declarations changed nothing (issue #138).
@OptIn(ExperimentalCompilerApi::class)
class SealedParameterExpansionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun generate(
        declarations: String,
        signature: String,
        prev: String = "@Prev",
        options: Map<String, String> = emptyMap(),
    ): String {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "Header.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    $declarations

                    $prev
                    @Composable
                    fun $signature {}
                    """,
                ),
                options = options,
            )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        return requireNotNull(result.generatedFile("HeaderPreview.kt"))
    }

    private fun functionNames(generated: String): List<String> =
        Regex("""private fun (\w+)\(""").findAll(generated).map { it.groupValues[1] }.toList()

    @Test
    fun `generates one Preview per subtype, named after it`() {
        val generated = generate(UI_STATE, "Header(state: UiState)")

        assertEquals(generated, listOf("HeaderLoadingPreview", "HeaderErrorPreview", "HeaderSuccessPreview"), functionNames(generated))
        assertTrue(generated, generated.contains("""@Preview(name = "Loading")"""))
        assertTrue(generated, generated.contains("state = UiState.Loading"))
        assertTrue(generated, Regex("""state = UiState.Error\(\s*message = "mock"""").containsMatchIn(generated))
        assertTrue(generated, Regex("""state = UiState.Success\(\s*title = "mock"""").containsMatchIn(generated))
    }

    @Test
    fun `generates the same file whatever order the subtypes are declared in`() {
        val reordered =
            """
            sealed interface UiState {
                data class Success(val title: String) : UiState
                data class Error(val message: String) : UiState
                data object Loading : UiState
            }
            """

        assertEquals(generate(UI_STATE, "Header(state: UiState)"), generate(reordered, "Header(state: UiState)"))
    }

    @Test
    fun `leaves out a subtype that cannot be built`() {
        val generated =
            generate(
                """
                sealed interface UiState {
                    data object Loading : UiState
                    data class Paged<T>(val items: List<T>) : UiState
                    data class Success(val title: String) : UiState
                }
                """,
                "Header(state: UiState)",
            )

        assertEquals(generated, listOf("HeaderLoadingPreview", "HeaderSuccessPreview"), functionNames(generated))
    }

    @Test
    fun `keeps a single Preview when only one subtype can be built`() {
        val generated =
            generate(
                """
                sealed interface UiState {
                    data object Loading : UiState
                    data class Paged<T>(val items: List<T>) : UiState
                }
                """,
                "Header(state: UiState)",
            )

        assertEquals(generated, listOf("HeaderPreview"), functionNames(generated))
        assertFalse(generated, generated.contains("name = "))
    }

    @Test
    fun `expands only the first sealed parameter`() {
        // Expanding both would be their product - nine Previews here, and it grows from there.
        val generated =
            generate(
                """
                $UI_STATE
                sealed interface Tab {
                    data object Home : Tab
                    data object Settings : Tab
                }
                """,
                "Header(state: UiState, tab: Tab)",
            )

        assertEquals(generated, 3, functionNames(generated).size)
        assertEquals(generated, 3, Regex("tab = Tab.Home").findAll(generated).count())
        assertFalse(generated, generated.contains("Tab.Settings"))
    }

    @Test
    fun `expands a sealed parameter that is not the first parameter`() {
        val generated = generate(UI_STATE, "Header(title: String, state: UiState)")

        assertEquals(generated, 3, functionNames(generated).size)
        assertEquals(generated, 3, Regex("""Header\(\s*title = "mock"""").findAll(generated).count())
    }

    @Test
    fun `expands a nullable sealed parameter`() {
        val generated = generate(UI_STATE, "Header(state: UiState?)")

        assertEquals(generated, 3, functionNames(generated).size)
    }

    @Test
    fun `expands a sealed parameter declared through a typealias`() {
        val generated = generate("$UI_STATE\ntypealias State = UiState", "Header(state: State)")

        assertEquals(generated, 3, functionNames(generated).size)
    }

    @Test
    fun `picks one subtype of a sealed subtype rather than expanding it too`() {
        val generated =
            generate(
                """
                sealed interface UiState {
                    data object Loading : UiState
                    sealed interface Failure : UiState {
                        data object Network : Failure
                        data object Server : Failure
                    }
                }
                """,
                "Header(state: UiState)",
            )

        assertEquals(generated, listOf("HeaderLoadingPreview", "HeaderFailurePreview"), functionNames(generated))
        assertTrue(generated, generated.contains("state = UiState.Failure.Network"))
        assertFalse(generated, generated.contains("Server"))
    }

    @Test
    fun `picks one subtype of a sealed type reached through a field`() {
        val generated = generate("$UI_STATE\ndata class Screen(val state: UiState)", "Header(screen: Screen)")

        assertEquals(generated, listOf("HeaderPreview"), functionNames(generated))
    }

    @Test
    fun `stacks every variant on each subtype's Preview`() {
        val generated = generate(UI_STATE, "Header(state: UiState)", prev = "@Prev(darkMode = true)")

        assertEquals(generated, 3, functionNames(generated).size)
        assertTrue(generated, generated.contains("""@Preview(name = "Loading")"""))
        assertTrue(generated, generated.contains("""name = "Loading - Dark Mode""""))
        assertEquals(generated, 3, Regex("Dark Mode").findAll(generated).count())
    }

    @Test
    fun `puts a configured name ahead of the subtype`() {
        val generated = generate(UI_STATE, "Header(state: UiState)", prev = """@Prev(name = "Header")""")

        assertTrue(generated, generated.contains("""name = "Header - Loading""""))
    }

    @Test
    fun `tells apart subtypes that share a simple name`() {
        val generated =
            generate(
                """
                sealed interface UiState
                object Feed { data object Error : UiState }
                object Profile { data object Error : UiState }
                """,
                "Header(state: UiState)",
            )

        assertEquals(generated, listOf("HeaderFeedErrorPreview", "HeaderProfileErrorPreview"), functionNames(generated))
    }

    @Test
    fun `lists the fields of every subtype in the manifest`() {
        // Each subtype's fields are slots now that each is built - and a slot the manifest leaves out
        // is one the generation task never fills.
        val manifest = File(folder.root, "slots.json")

        generate(UI_STATE, "Header(state: UiState)", options = mapOf("prevham.slotManifest" to manifest.absolutePath))

        val text = manifest.readText()
        assertTrue(text, text.contains(""""slot": "test.UiState.Error.message""""))
        assertTrue(text, text.contains(""""slot": "test.UiState.Success.title""""))
    }

    private companion object {
        val UI_STATE =
            """
            sealed interface UiState {
                data object Loading : UiState
                data class Success(val title: String) : UiState
                data class Error(val message: String) : UiState
            }
            """
    }
}
