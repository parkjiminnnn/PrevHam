package io.github.parkjiminnnn.compiler.mock

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCompilerApi::class)
class NullableFallbackMockGeneratorTest {
    @Test
    fun `falls back to a real mock instead of null when a nullable type is otherwise supported`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "GreetingCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    @Prev
                    @Composable
                    fun GreetingCard(name: String?) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("GreetingCardPreview.kt"))
        assertTrue(generated.contains("name = \"mock\""))
    }

    @Test
    fun `falls back to null when a nullable type has no other supporting generator`() {
        // Repository<*> has an unresolvable star-projected type argument, so
        // InterfaceMockGenerator.toTypeName() returns null and it's left unsupported -
        // only the nullable fallback can produce a value for it.
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "RepoCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    interface Repository<T> { fun get(): T }

                    @Prev
                    @Composable
                    fun RepoCard(repository: Repository<*>?) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("RepoCardPreview.kt"))
        assertTrue(generated.contains("repository = null"))
    }
}
