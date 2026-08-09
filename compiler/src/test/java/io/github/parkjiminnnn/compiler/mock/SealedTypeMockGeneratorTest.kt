package io.github.parkjiminnnn.compiler.mock

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// A sealed type is built as one of its real subtypes rather than mocked. MockK's own answer goes
// through Objenesis, which skips the constructor and picks a subtype the generated file never
// records - see SealedTypeMockGenerator's docs.
@OptIn(ExperimentalCompilerApi::class)
class SealedTypeMockGeneratorTest {
    @Test
    fun `builds an object subtype of a sealed interface instead of mocking it`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "StateCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    sealed interface UiState {
                        data object Loading : UiState
                        data class Success(val title: String) : UiState
                    }

                    @Prev
                    @Composable
                    fun StateCard(state: UiState) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("StateCardPreview.kt"))
        assertTrue(generated, generated.contains("state = UiState.Loading"))
        assertFalse(generated, generated.contains("mockk<UiState>"))
    }

    @Test
    fun `builds a data class subtype when the sealed type has no object subtype`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "ResultCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    sealed interface LoadResult {
                        data class Success(val title: String) : LoadResult
                    }

                    @Prev
                    @Composable
                    fun ResultCard(result: LoadResult) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("ResultCardPreview.kt"))
        // Nested subtypes have to be referenced through their parent, not by simple name alone.
        assertTrue(generated, generated.contains("LoadResult.Success("))
        assertTrue(generated, generated.contains("""title = "mock""""))
    }

    @Test
    fun `picks the same subtype regardless of declaration order`() {
        fun compile(subtypeDeclarations: String) =
            compilePrev(
                SourceFile.kotlin(
                    "OrderCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    sealed interface Order {
                        $subtypeDeclarations
                    }

                    @Prev
                    @Composable
                    fun OrderCard(order: Order) {}
                    """,
                ),
            ).generatedFile("OrderCardPreview.kt")

        val successFirst = requireNotNull(compile("data class Success(val title: String) : Order\ndata object Idle : Order"))
        val idleFirst = requireNotNull(compile("data object Idle : Order\ndata class Success(val title: String) : Order"))

        assertEquals(successFirst, idleFirst)
        assertTrue(successFirst, successFirst.contains("order = Order.Idle"))
    }

    @Test
    fun `builds a subtype of a sealed class`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "ScreenCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    sealed class Screen {
                        data object Home : Screen()
                    }

                    @Prev
                    @Composable
                    fun ScreenCard(screen: Screen) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("ScreenCardPreview.kt"))
        assertTrue(generated, generated.contains("screen = Screen.Home"))
        assertFalse(generated, generated.contains("mockk<Screen>"))
    }

    @Test
    fun `builds a top-level sealed subtype declared outside its parent`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "RouteCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    sealed interface Route
                    data object Splash : Route

                    @Prev
                    @Composable
                    fun RouteCard(route: Route) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("RouteCardPreview.kt"))
        assertTrue(generated, generated.contains("route = Splash"))
    }

    @Test
    fun `falls back to a MockK mock for a sealed type with no usable subtype`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "EmptyCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    sealed interface Marker

                    @Prev
                    @Composable
                    fun EmptyCard(marker: Marker) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("EmptyCardPreview.kt"))
        assertTrue(generated, generated.contains("mockk<Marker>(relaxed = true)"))
    }
}
