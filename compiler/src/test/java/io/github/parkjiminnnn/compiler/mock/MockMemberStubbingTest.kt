package io.github.parkjiminnnn.compiler.mock

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Relaxed mode answers an unstubbed call from the erased return type, so a member whose type erases
// away - a type parameter, or anything read out of a Flow - yields something the caller's checkcast
// rejects (issue #59). Those members are stubbed; the rest are left alone, because stubbing every
// member made every member a branch and the output grew as the product of member counts (issue #75).
@OptIn(ExperimentalCompilerApi::class)
class MockMemberStubbingTest {
    private fun generate(
        name: String,
        declarations: String,
        parameter: String = "viewModel: $name" + "ViewModel",
    ): String {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "$name.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev
                    import kotlinx.coroutines.flow.StateFlow

                    $declarations

                    @Prev
                    @Composable
                    fun $name($parameter) {}
                    """,
                ),
            )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        return requireNotNull(result.generatedFile("${name}Preview.kt"))
    }

    @Test
    fun `stubs a StateFlow member with a real flow holding a real state`() {
        val generated =
            generate(
                "HomeScreen",
                """
                sealed interface UiState {
                    data object Loading : UiState
                    data class Success(val title: String) : UiState
                }
                class HomeScreenViewModel {
                    val uiState: StateFlow<UiState> get() = throw NotImplementedError()
                }
                """,
            )

        assertTrue(generated, generated.contains("mockk<HomeScreenViewModel>(relaxed = true) {"))
        assertTrue(generated, generated.contains("every { uiState } returns MutableStateFlow(UiState.Loading)"))
    }

    @Test
    fun `leaves a member relaxed mode can answer alone`() {
        // A concrete return type survives erasure, so relaxed mode produces a usable value for it.
        // Stubbing it anyway is what turned every member into a branch.
        val generated =
            generate(
                "Profile",
                """
                data class User(val name: String)
                sealed interface Tab { data object Home : Tab }
                class ProfileViewModel {
                    val user: User get() = throw NotImplementedError()
                    val title: String get() = ""
                    val tab: Tab get() = Tab.Home
                    fun refresh(): User = throw NotImplementedError()
                }
                """,
            )

        assertTrue(generated, generated.contains("mockk<ProfileViewModel>(relaxed = true),"))
        assertFalse(generated, generated.contains("every {"))
    }

    @Test
    fun `stubs a member whose own type is a type parameter`() {
        val generated =
            generate(
                "Holder",
                """
                interface Box<T> {
                    val value: T
                    fun get(): T
                }
                """,
                parameter = "box: Box<String>",
            )

        // asMemberOf substitutes the type argument, so the stub gets String rather than T.
        assertTrue(generated, generated.contains("""every { value } returns "mock""""))
        assertTrue(generated, generated.contains("""every { get() } returns "mock""""))
    }

    @Test
    fun `stubs a member that only leads to an erased one`() {
        // `middle` and `inner` erase to nothing on their own. Skipping them would leave relaxed mode
        // to invent the mocks in between, and `items` further down could never be reached.
        val generated =
            generate(
                "Deep",
                """
                data class Item(val title: String)
                interface Inner { val items: StateFlow<Item> }
                interface Middle { val inner: Inner }
                interface DeepViewModel { val middle: Middle }
                """,
            )

        assertTrue(generated, generated.contains("every { middle } returns"))
        assertTrue(generated, generated.contains("every { inner } returns"))
        assertTrue(generated, generated.contains("every { items } returns MutableStateFlow(Item("))
    }

    @Test
    fun `expands nothing when a graph has no erased member anywhere`() {
        // The issue #75 shape: every member is a branch, and before narrowing this produced mocks as
        // the product of the member counts.
        val generated =
            generate(
                "Wide",
                """
                interface Api { val name: String }
                interface Dao { val x: Api; val y: Api }
                interface WideViewModel { val a: Dao; val b: Dao }
                """,
            )

        assertEquals(generated, 1, generated.split("mockk<").size - 1)
        assertFalse(generated, generated.contains("every {"))
    }

    @Test
    fun `does not search through a type from a compiled dependency`() {
        // Throwable reaches an erased member via Array<StackTraceElement>.get, so searching into it
        // would mark it as needing stubs - and stubbing that far in emits `every { get(any()) }`,
        // which collides with MockK's matcher scope and doesn't compile.
        val generated = generate("Error", "", parameter = "t: Throwable")

        assertTrue(generated, generated.contains("mockk<Throwable>(relaxed = true),"))
        assertFalse(generated, generated.contains("every {"))
    }

    @Test
    fun `stubs a function with one argument matcher per parameter`() {
        val generated =
            generate(
                "Search",
                """
                data class Hit(val title: String)
                class SearchViewModel {
                    fun findAll(): StateFlow<Hit> = throw NotImplementedError()
                    fun find(query: String, limit: Int): StateFlow<Hit> = throw NotImplementedError()
                }
                """,
            )

        assertTrue(generated, generated.contains("every { findAll() } returns MutableStateFlow(Hit("))
        assertTrue(generated, generated.contains("every { find(any(), any()) } returns MutableStateFlow(Hit("))
    }

    @Test
    fun `stubs a suspend function with coEvery`() {
        val generated =
            generate(
                "Feed",
                """
                data class Post(val title: String)
                class FeedViewModel {
                    suspend fun load(id: Int): StateFlow<Post> = throw NotImplementedError()
                }
                """,
            )

        assertTrue(generated, generated.contains("coEvery { load(any()) } returns MutableStateFlow(Post("))
    }

    @Test
    fun `does not stub vararg, generic, or non-public members`() {
        val generated =
            generate(
                "Hidden",
                """
                data class Item(val title: String)
                class HiddenViewModel {
                    private val secret: StateFlow<Item> get() = throw NotImplementedError()
                    internal val shared: StateFlow<Item> get() = throw NotImplementedError()
                    fun <T> convert(value: T): T = throw NotImplementedError()
                    fun pick(vararg ids: Long): StateFlow<Item> = throw NotImplementedError()
                    val visible: StateFlow<Item> get() = throw NotImplementedError()
                }
                """,
            )

        assertTrue(generated, generated.contains("every { visible } returns MutableStateFlow(Item("))
        assertFalse(generated, generated.contains("secret"))
        assertFalse(generated, generated.contains("shared"))
        assertFalse(generated, generated.contains("convert"))
        // Matching a vararg takes a spread of the matcher for its exact element type, and guessing
        // wrong emits a stub that doesn't compile.
        assertFalse(generated, generated.contains("pick"))
    }

    @Test
    fun `stubs a nullable parameter type without failing the round`() {
        // asMemberOf rejects a nullable containing type, and the exception used to take the whole
        // KSP round with it (issue #74).
        val generated =
            generate(
                "Optional",
                """
                data class Item(val title: String)
                interface OptionalViewModel { val items: StateFlow<Item> }
                """,
                parameter = "viewModel: OptionalViewModel?",
            )

        assertTrue(generated, generated.contains("every { items } returns MutableStateFlow(Item("))
    }

    @Test
    fun `bounds the total number of stubs`() {
        // Narrowing makes the product-of-member-counts blow-up rare rather than impossible: a graph
        // where every branch leads to something erased still expands along all of them. The budget
        // is what keeps that from exhausting the heap or overflowing a method.
        val levels = 5
        val width = 4
        val declarations =
            buildString {
                append("data class Item(val title: String)\n")
                repeat(levels) { level ->
                    append("interface L$level {\n")
                    (1..width).forEach { append("  val p$it: L${level + 1}\n") }
                    append("}\n")
                }
                append("interface L$levels { val f: StateFlow<Item> }\n")
                append("interface BoundedViewModel { val root: L0 }\n")
            }

        val generated = generate("Bounded", declarations)

        val stubs = generated.split("every {").size - 1
        assertTrue("$stubs stubs", stubs <= MockContext.MAX_STUBS)
    }

    @Test
    fun `does not expand a compiled type held as a data class field`() {
        // The shape issue #75 was reported with, and the one that actually triggers the explosion.
        // A data class field is a constructor argument, so the mock for its type is built directly
        // instead of being gated by StubNecessity - and all 56 of LocalDate's members are then
        // enumerated, with anything the search is willing to enter becoming a branch.
        //
        // The equivalent interface member does not reach that path at all, which is how issue #87
        // shipped: the regression test written for #75 used a synthetic interface graph, which
        // reproduced the size of the explosion but not its trigger.
        //
        // Asserted as a count plus a successful compile rather than on the generated text: the
        // failure mode is hundreds of mocks that don't compile, and a text assertion reads that as
        // a pass.
        val generated =
            generate(
                "Festival",
                "data class Festival(val name: String, val startDate: java.time.LocalDate)",
                parameter = "festival: Festival",
            )

        val mocks = generated.split("mockk<").size - 1
        assertTrue("$mocks mocks:\n$generated", mocks <= 2)
        assertFalse(generated, generated.contains("Stream<"))
    }

    @Test
    fun `does not expand a compiled type passed directly`() {
        // Same path as above without the data class: a parameter's type is mocked directly too.
        val generated = generate("Day", "", parameter = "date: java.time.LocalDate")

        val mocks = generated.split("mockk<").size - 1
        assertTrue("$mocks mocks:\n$generated", mocks <= 2)
    }
}
