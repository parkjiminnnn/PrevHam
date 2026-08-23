package io.github.parkjiminnnn.compiler.mock

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// A typealias is a second name for a type, not a type of its own, so a parameter declared with one
// has to reach the same generator the underlying type would (issue #81). KSType.declaration is a
// KSTypeAlias in that case, and every generator narrows to KSClassDeclaration - so before this was
// handled, nothing matched and the whole Preview was skipped.
@OptIn(ExperimentalCompilerApi::class)
class TypeAliasTest {
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
                    import kotlinx.coroutines.flow.StateFlow

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
    fun `resolves an alias for a data class`() {
        val generated =
            generate(
                "Aliased",
                """
                data class Item(val title: String)
                typealias MyItem = Item
                """,
                "item: MyItem",
            )

        assertTrue(generated, generated.contains("item = Item("))
    }

    @Test
    fun `resolves an alias for an interface`() {
        val generated =
            generate(
                "AliasedRepo",
                """
                interface Repo { val name: String }
                typealias MyRepo = Repo
                """,
                "repo: MyRepo",
            )

        assertTrue(generated, generated.contains("mockk<Repo>(relaxed = true)"))
    }

    @Test
    fun `resolves an alias for a function type`() {
        // The idiom this is most likely to be hit by - typealias OnClick = () -> Unit is everywhere
        // in Compose code.
        val generated =
            generate(
                "AliasedLambda",
                "typealias OnClick = () -> Unit",
                "onClick: OnClick",
            )

        assertTrue(generated, generated.contains("onClick = { }"))
    }

    @Test
    fun `resolves an alias for a sealed type`() {
        val generated =
            generate(
                "AliasedState",
                """
                sealed interface UiState {
                    data object Loading : UiState
                    data class Success(val title: String) : UiState
                }
                typealias State = UiState
                """,
                "state: State",
            )

        assertTrue(generated, generated.contains("state = UiState.Loading"))
    }

    @Test
    fun `resolves an alias declared by the standard library`() {
        // kotlin.Comparator is a typealias for java.util.Comparator, so this is reachable without
        // the user writing an alias at all.
        val generated =
            generate(
                "AliasedComparator",
                "data class Item(val title: String)",
                "comparator: Comparator<Item>",
            )

        assertTrue(generated, generated.contains("mockk<Comparator<Item>>(relaxed = true)"))
    }

    @Test
    fun `follows a chain of aliases`() {
        val generated =
            generate(
                "Chained",
                """
                data class Item(val title: String)
                typealias Inner = Item
                typealias Outer = Inner
                """,
                "item: Outer",
            )

        assertTrue(generated, generated.contains("item = Item("))
    }

    @Test
    fun `carries the use site's type arguments through a parameterized alias`() {
        // typealias MyBox<T> = Box<T> declares its own type parameter, so the argument at the use
        // site has to be mapped onto it. Resolving the alias's own type reference alone yields
        // Box<T> with T unsubstituted.
        val generated =
            generate(
                "AliasedBox",
                """
                data class Item(val title: String)
                data class Box<T>(val value: T)
                typealias MyBox<T> = Box<T>
                """,
                "box: MyBox<Item>",
            )

        assertTrue(generated, generated.contains("value = Item("))
    }

    @Test
    fun `resolves an alias behind a data class field`() {
        // Not just parameters: a field declared with an alias reaches the generators by a different
        // path, through the constructor's parameter list.
        val generated =
            generate(
                "FieldAlias",
                """
                data class Item(val title: String)
                typealias MyItem = Item
                data class Holder(val item: MyItem)
                """,
                "holder: Holder",
            )

        assertTrue(generated, generated.contains("item = Item("))
    }

    @Test
    fun `resolves an alias behind a stubbed interface member`() {
        // And a third path: a mocked member's declared type.
        val generated =
            generate(
                "MemberAlias",
                """
                data class Item(val title: String)
                typealias Items = StateFlow<Item>
                interface MemberAliasViewModel { val items: Items }
                """,
                "viewModel: MemberAliasViewModel",
            )

        assertTrue(generated, generated.contains("every { this@mockk.items } returns MutableStateFlow(Item("))
    }

    @Test
    fun `resolves an alias used with a nullable marker`() {
        val generated =
            generate(
                "NullableAlias",
                """
                data class Item(val title: String)
                typealias MyItem = Item
                """,
                "item: MyItem?",
            )

        assertTrue(generated, generated.contains("item = Item("))
    }

    @Test
    fun `skips an alias that reorders its type parameters rather than guessing`() {
        // KSType.replace is positional, so it is only exact when the right-hand side applies the
        // alias's parameters in declaration order. Pair<B, A> does not, and substituting positionally
        // would produce Pair<Int, String> where Pair<String, Int> was written - code that compiles
        // and mocks the wrong types. Left unsupported instead, which is the behaviour every alias
        // had before issue #81.
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "Swapped.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    typealias Swapped<A, B> = Pair<B, A>

                    @Prev
                    @Composable
                    fun Swapped(pair: Swapped<Int, String>) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        assertTrue(result.messages, result.messages.contains("no mock generator available for parameter 'pair'"))
    }

    @Test
    fun `skips an alias whose right-hand side nests its type parameter`() {
        // Same reason: Box<Box<T>>'s own argument is Box<T>, not T, so there is no position to
        // substitute the use site's argument into.
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "NestedAlias.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class Item(val title: String)
                    data class Box<T>(val value: T)
                    typealias Nested<T> = Box<Box<T>>

                    @Prev
                    @Composable
                    fun NestedAlias(box: Nested<Item>) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        assertTrue(result.messages, result.messages.contains("no mock generator available for parameter 'box'"))
    }
}
