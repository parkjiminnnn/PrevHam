package io.github.parkjiminnnn.compiler.mock

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// MockGeneratorRegistry.default() bounds recursion at MAX_DEPTH = 3: the top-level function
// parameter is checked at depth 0, and each level of data class field nesting moves one registry
// deeper. A registry built for depth >= MAX_DEPTH only contains leaf generators (no
// DataClassMockGenerator), so a data class checked at that depth has no supporting generator.
@OptIn(ExperimentalCompilerApi::class)
class DepthLimitedRecursionTest {
    @Test
    fun `three levels of data class nesting stay within the depth limit`() {
        // Card(a: A) -> depth 0; A.b: B -> depth 1; B.c: C -> depth 2; C.value: Int -> depth 3
        // (leaf-only, but Int is a leaf-supported primitive) - every level resolves.
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "NestedCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class C(val value: Int)
                    data class B(val c: C)
                    data class A(val b: B)

                    @Prev
                    @Composable
                    fun NestedCard(a: A) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("NestedCardPreview.kt"))
        assertTrue(generated.contains("A("))
        assertTrue(generated.contains("b = B("))
        assertTrue(generated.contains("c = C("))
        assertTrue(generated.contains("value = 1"))
    }

    @Test
    fun `a fourth level of data class nesting exceeds the depth limit and is skipped`() {
        // Card(a: A) -> depth 0; A.b: B -> depth 1; B.c: C -> depth 2; C.d: D -> depth 3
        // (leaf-only) - D is itself a data class, which depth 3's leaf-only registry can't
        // support, so the whole chain becomes unsupported and no Preview is generated.
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "TooDeepCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    data class D(val value: Int)
                    data class C(val d: D)
                    data class B(val c: C)
                    data class A(val b: B)

                    @Prev
                    @Composable
                    fun TooDeepCard(a: A) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        assertTrue(result.messages.contains("no mock generator available for parameter 'a'"))
        assertNull(result.generatedFile("TooDeepCardPreview.kt"))
    }
}
