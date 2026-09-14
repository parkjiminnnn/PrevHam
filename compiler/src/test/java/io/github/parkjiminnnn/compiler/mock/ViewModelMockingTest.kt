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

// A ViewModel is never constructed, however callable its constructor is. Everything built for the
// shape reaches a mock and only a mock: the erased-member stub that keeps uiState from throwing
// (#59), and any configured value for its members (#103). Constructing one dropped both (#119).
@OptIn(ExperimentalCompilerApi::class)
class ViewModelMockingTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun generate(
        declarations: String,
        parameter: String,
        values: String? = null,
    ): String {
        val options =
            values?.let {
                mapOf("prevham.mockValues" to folder.newFile("values.json").apply { writeText(it) }.absolutePath)
            } ?: emptyMap()
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "Card.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import androidx.lifecycle.ViewModel
                    import io.github.parkjiminnnn.runtime.Prev

                    $declarations

                    @Prev
                    @Composable
                    fun Card($parameter) {}
                    """,
                ),
                options = options,
            )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        return requireNotNull(result.generatedFile("CardPreview.kt")) { result.messages }
    }

    @Test
    fun `mocks a ViewModel that takes constructor parameters`() {
        // The ordinary shape with dependency injection, and the one that regressed.
        val generated =
            generate(
                """
                interface FestivalRepository { fun load(): String }
                class FestivalViewModel(private val repository: FestivalRepository) : ViewModel()
                """,
                "viewModel: FestivalViewModel",
            )

        assertTrue(generated, generated.contains("mockk<FestivalViewModel>(relaxed = true)"))
        assertFalse(generated, generated.contains("FestivalViewModel("))
    }

    @Test
    fun `still mocks a ViewModel with no constructor parameters`() {
        val generated = generate("class HomeViewModel : ViewModel()", "viewModel: HomeViewModel")

        assertTrue(generated, generated.contains("mockk<HomeViewModel>(relaxed = true)"))
    }

    @Test
    fun `mocks a ViewModel reached through a base class`() {
        // The whole supertype chain is searched, so a project's own BaseViewModel counts.
        val generated =
            generate(
                """
                abstract class BaseViewModel : ViewModel()
                class FestivalViewModel(val id: String) : BaseViewModel()
                """,
                "viewModel: FestivalViewModel",
            )

        assertTrue(generated, generated.contains("mockk<FestivalViewModel>(relaxed = true)"))
    }

    @Test
    fun `a configured value reaches a ViewModel with parameters again`() {
        // The regression that mattered most: the value was in the file and never arrived, because
        // only a mock's members are stubbed from it.
        val generated =
            generate(
                """
                interface FestivalRepository { fun load(): String }
                class FestivalViewModel(private val repository: FestivalRepository) : ViewModel() {
                    val title: String get() = repository.load()
                }
                """,
                "viewModel: FestivalViewModel",
                values = """{"test.FestivalViewModel.title": "2026 대동제"}""",
            )

        assertTrue(generated, generated.contains("""every { this@mockk.title } returns "2026 대동제""""))
    }

    @Test
    fun `an erased member of a ViewModel with parameters is stubbed again`() {
        // Issue #59's stub only exists on a mock. Constructing the ViewModel removed it.
        val generated =
            generate(
                """
                import kotlinx.coroutines.flow.StateFlow
                data class UiState(val label: String)
                interface FestivalRepository { fun load(): String }
                class FestivalViewModel(private val repository: FestivalRepository) : ViewModel() {
                    val uiState: StateFlow<UiState> get() = throw NotImplementedError()
                }
                """,
                "viewModel: FestivalViewModel",
            )

        assertTrue(generated, generated.contains("every { this@mockk.uiState } returns MutableStateFlow("))
    }

    @Test
    fun `still constructs a class that is not a ViewModel`() {
        val generated = generate("class Ticket(val holder: String, val price: Int)", "ticket: Ticket")

        assertTrue(generated, generated.contains("""holder = "mock""""))
        assertTrue(generated, generated.contains("price = 1"))
    }

    @Test
    fun `still constructs a data class that extends a class`() {
        // The alternative rule - decline anything extending a class - would have taken every sealed
        // subtype with it.
        val generated =
            generate(
                """
                sealed class UiState {
                    data class Success(val title: String) : UiState()
                }
                """,
                "state: UiState",
            )

        assertTrue(generated, generated.contains("""UiState.Success("""))
        assertTrue(generated, generated.contains("""title = "mock""""))
    }
}
