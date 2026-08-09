package io.github.parkjiminnnn.compiler.mock

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Relaxed mode answers an unstubbed call from the erased return type, so a generic member like
// StateFlow<T>.value - erased to Object - yields something the caller's checkcast rejects (issue
// #59). Stubbing a member explicitly keeps MockK off that path. GeneratedMockValueTest in `sample`
// covers the runtime half of this; these tests cover what gets emitted.
@OptIn(ExperimentalCompilerApi::class)
class MockMemberStubbingTest {
    @Test
    fun `stubs a StateFlow property with a real flow holding a real state`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "HomeScreen.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev
                    import kotlinx.coroutines.flow.StateFlow

                    sealed interface FestivalUiState {
                        data object Loading : FestivalUiState
                        data class Success(val title: String) : FestivalUiState
                    }

                    class HomeViewModel {
                        val festivalUiState: StateFlow<FestivalUiState> get() = throw NotImplementedError()
                    }

                    @Prev
                    @Composable
                    fun HomeScreen(viewModel: HomeViewModel) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("HomeScreenPreview.kt"))
        assertTrue(generated, generated.contains("mockk<HomeViewModel>(relaxed = true) {"))
        assertTrue(
            generated,
            generated.contains("every { festivalUiState } returns MutableStateFlow(FestivalUiState.Loading)"),
        )
    }

    @Test
    fun `stubs a plain property that is not a flow`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "ProfileScreen.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class User(val name: String)

                    class ProfileViewModel {
                        val user: User get() = throw NotImplementedError()
                    }

                    @Prev
                    @Composable
                    fun ProfileScreen(viewModel: ProfileViewModel) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("ProfileScreenPreview.kt"))
        assertTrue(generated, generated.contains("""every { user } returns User("""))
    }

    @Test
    fun `stubs a function with one argument matcher per parameter`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "SearchScreen.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class Hit(val title: String)

                    class SearchViewModel {
                        fun findAll(): Hit = throw NotImplementedError()
                        fun find(query: String, limit: Int): Hit = throw NotImplementedError()
                    }

                    @Prev
                    @Composable
                    fun SearchScreen(viewModel: SearchViewModel) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("SearchScreenPreview.kt"))
        assertTrue(generated, generated.contains("every { findAll() } returns Hit("))
        assertTrue(generated, generated.contains("every { find(any(), any()) } returns Hit("))
    }

    @Test
    fun `stubs a suspend function with coEvery`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "FeedScreen.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class Post(val title: String)

                    class FeedViewModel {
                        suspend fun load(id: Int): Post = throw NotImplementedError()
                    }

                    @Prev
                    @Composable
                    fun FeedScreen(viewModel: FeedViewModel) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("FeedScreenPreview.kt"))
        assertTrue(generated, generated.contains("coEvery { load(any()) } returns Post("))
    }

    @Test
    fun `leaves Unit-returning functions and Any members to relaxed mode`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "ActionScreen.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    class ActionViewModel {
                        fun refresh() {}
                    }

                    @Prev
                    @Composable
                    fun ActionScreen(viewModel: ActionViewModel) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("ActionScreenPreview.kt"))
        assertFalse(generated, generated.contains("refresh"))
        assertFalse(generated, generated.contains("toString"))
        assertFalse(generated, generated.contains("hashCode"))
        // Nothing worth stubbing, so the mock stays in its original bare form.
        assertTrue(generated, generated.contains("mockk<ActionViewModel>(relaxed = true)"))
        assertFalse(generated, generated.contains("mockk<ActionViewModel>(relaxed = true) {"))
    }

    @Test
    fun `skips members that no generator can build a value for`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "StarScreen.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    interface Repository<T>

                    class StarViewModel {
                        val repository: Repository<*> get() = throw NotImplementedError()
                        val title: String get() = throw NotImplementedError()
                    }

                    @Prev
                    @Composable
                    fun StarScreen(viewModel: StarViewModel) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("StarScreenPreview.kt"))
        assertTrue(generated, generated.contains("every { title } returns"))
        // A star projection has no resolvable type argument, so it stays with relaxed mode rather
        // than being stubbed with something that wouldn't compile.
        assertFalse(generated, generated.contains("every { repository }"))
    }

    @Test
    fun `does not stub non-public or generic members`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "HiddenScreen.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class Item(val title: String)

                    class HiddenViewModel {
                        private val secret: Item get() = throw NotImplementedError()
                        internal val shared: Item get() = throw NotImplementedError()
                        fun <T> convert(value: T): T = throw NotImplementedError()
                        val visible: Item get() = throw NotImplementedError()
                    }

                    @Prev
                    @Composable
                    fun HiddenScreen(viewModel: HiddenViewModel) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("HiddenScreenPreview.kt"))
        assertTrue(generated, generated.contains("every { visible } returns Item("))
        assertFalse(generated, generated.contains("secret"))
        assertFalse(generated, generated.contains("shared"))
        assertFalse(generated, generated.contains("convert"))
    }

    @Test
    fun `does not stub a function with a vararg parameter`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "PickerScreen.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class Row(val id: Int)

                    class PickerViewModel {
                        fun pick(vararg ids: Long): Row = throw NotImplementedError()
                    }

                    @Prev
                    @Composable
                    fun PickerScreen(viewModel: PickerViewModel) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("PickerScreenPreview.kt"))
        assertFalse(generated, generated.contains("pick"))
    }

    @Test
    fun `substitutes type arguments when stubbing a generic type's members`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "HolderScreen.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    interface Holder<T> {
                        val value: T
                        fun get(): T
                    }

                    @Prev
                    @Composable
                    fun HolderScreen(holder: Holder<String>) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("HolderScreenPreview.kt"))
        assertTrue(generated, generated.contains("""every { value } returns "mock""""))
        assertTrue(generated, generated.contains("""every { get() } returns "mock""""))
    }

    @Test
    fun `bounds stubbing of a self-referential type`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "NodeScreen.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    interface Node { val next: Node }

                    @Prev
                    @Composable
                    fun NodeScreen(node: Node) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("NodeScreenPreview.kt"))
        // Stubbing `next` re-enters Node, which is already being expanded, so the chain stops one
        // level down with a mock that stubs nothing - otherwise generation would run forever.
        assertEquals(generated, 2, generated.split("mockk<Node>(relaxed = true)").size - 1)
        assertEquals(generated, 1, generated.split("every { next }").size - 1)
    }

    @Test
    fun `stubs any generic member, not just flows`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "GenericScreen.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class Item(val title: String)
                    interface Box<T> { val item: T }
                    interface Repository<T> { fun get(): T }

                    class GenericViewModel {
                        val box: Box<Item> get() = throw NotImplementedError()
                        val repo: Repository<Item> get() = throw NotImplementedError()
                    }

                    @Prev
                    @Composable
                    fun GenericScreen(viewModel: GenericViewModel) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("GenericScreenPreview.kt"))
        // The erased-return-type problem belongs to any member whose type is a type parameter, so
        // the nested mocks get their own stubs rather than being left bare. StateFlow is only the
        // shape it was first reported through.
        assertTrue(generated, generated.contains("every { box } returns mockk<Box<Item>>(relaxed = true) {"))
        assertTrue(generated, generated.contains("every { item } returns Item("))
        assertTrue(generated, generated.contains("every { repo } returns mockk<Repository<Item>>(relaxed = true) {"))
        assertTrue(generated, generated.contains("every { get() } returns Item("))
    }

    @Test
    fun `stubs a nullable data class member with a real instance`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "OptionalScreen.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class Item(val title: String)

                    class OptionalViewModel {
                        val item: Item? get() = null
                    }

                    @Prev
                    @Composable
                    fun OptionalScreen(viewModel: OptionalViewModel) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("OptionalScreenPreview.kt"))
        assertTrue(generated, generated.contains("every { item } returns Item("))
    }

    @Test
    fun `keeps stubbing all the way down a long interface chain`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "DeepScreen.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev
                    import kotlinx.coroutines.flow.StateFlow

                    data class Item(val title: String)
                    interface Inner { val items: StateFlow<Item> }
                    interface Middle { val inner: Inner }
                    interface Outer { val middle: Middle }

                    @Prev
                    @Composable
                    fun DeepScreen(outer: Outer) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("DeepScreenPreview.kt"))
        assertTrue(generated, generated.contains("every { middle } returns"))
        assertTrue(generated, generated.contains("every { inner } returns"))
        // The innermost member used to be left bare because the chain had spent the depth budget,
        // which put the issue #59 crash back within reach. Nothing here revisits a type, so there
        // is no longer anything to run out of.
        assertTrue(generated, generated.contains("every { items } returns MutableStateFlow(Item("))
    }

    @Test
    fun `stubs members of an interface parameter too`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "LoaderScreen.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev
                    import kotlinx.coroutines.flow.Flow

                    data class Image(val url: String)

                    interface ImageLoader {
                        val images: Flow<Image>
                    }

                    @Prev
                    @Composable
                    fun LoaderScreen(loader: ImageLoader) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("LoaderScreenPreview.kt"))
        assertTrue(generated, generated.contains("every { images } returns MutableStateFlow(Image("))
    }
}
