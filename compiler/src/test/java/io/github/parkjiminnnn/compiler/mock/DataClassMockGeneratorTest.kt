package io.github.parkjiminnnn.compiler.mock

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCompilerApi::class)
class DataClassMockGeneratorTest {
    @Test
    fun `generates a named-argument constructor call for a flat data class`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "UserCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class User(val id: Int, val name: String, val age: Int)

                    @Prev
                    @Composable
                    fun UserCard(user: User) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("UserCardPreview.kt"))
        assertTrue(generated.contains("User("))
        assertTrue(generated.contains("id = 1"))
        assertTrue(generated.contains("name = \"mock\""))
        assertTrue(generated.contains("age = 1"))
    }

    @Test
    fun `recurses into nested data class fields`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "OrderCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class Address(val city: String)
                    data class Order(val id: Int, val address: Address)

                    @Prev
                    @Composable
                    fun OrderCard(order: Order) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("OrderCardPreview.kt"))
        assertTrue(generated.contains("Order("))
        assertTrue(generated.contains("address = Address("))
        assertTrue(generated.contains("city = \"mock\""))
    }

    @Test
    fun `substitutes generic type arguments via asMemberOf`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "BoxCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class Box<T>(val value: T)

                    @Prev
                    @Composable
                    fun BoxCard(box: Box<String>) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("BoxCardPreview.kt"))
        assertTrue(generated.contains("Box("))
        assertTrue(generated.contains("value = \"mock\""))
    }

    @Test
    fun `skips Preview generation when a required field has no default and no generator supports it`() {
        // A non-data class field has no supporting generator combination here because
        // DataClassMockGenerator requires Modifier.DATA - "owner" is a plain class with no
        // no-arg constructor for InterfaceMockGenerator's mock either... instead we use a
        // required parameter of a type nested past the depth limit; see DepthLimitedRecursionTest
        // for the dedicated depth-limit case. This test covers the simpler "field type category
        // that maps to nothing" case: a raw function-type field with an unsupported return type.
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "ListenerCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    interface Repository<T> { fun get(): T }
                    data class Holder(val repository: Repository<*>)

                    @Prev
                    @Composable
                    fun ListenerCard(holder: Holder) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        assertTrue(result.messages.contains("no mock generator available for parameter 'holder'"))
        assertEquals(null, result.generatedFile("ListenerCardPreview.kt"))
    }

    @Test
    fun `builds a real instance for a nullable data class`() {
        // asMemberOf() throws on a nullable containing type rather than returning nothing, which
        // failed the whole KSP round with an IllegalArgumentException instead of skipping one
        // parameter. Nullable types are meant to reach NullableFallbackMockGenerator only when no
        // other generator can serve them, so this has to produce a real Item.
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "OptionalCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class Item(val title: String)

                    @Prev
                    @Composable
                    fun OptionalCard(item: Item?) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("OptionalCardPreview.kt"))
        assertTrue(generated, generated.contains("item = Item("))
        assertTrue(generated, generated.contains("""title = "mock""""))
    }
}
