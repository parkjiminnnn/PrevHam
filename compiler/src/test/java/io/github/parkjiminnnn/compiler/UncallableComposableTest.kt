package io.github.parkjiminnnn.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The Preview always goes into a new file - KSP's CodeGenerator can't add to an existing one - so a
// composable that a separate top-level file can't call has no Preview available to it at all. That
// is reported before anything is written, rather than emitting a file that fails to compile.
@OptIn(ExperimentalCompilerApi::class)
class UncallableComposableTest {
    private fun compile(
        name: String,
        declaration: String,
    ) = compilePrev(
        SourceFile.kotlin(
            "$name.kt",
            """
            package test
            import androidx.compose.runtime.Composable
            import io.github.parkjiminnnn.runtime.Prev

            $declaration
            """,
        ),
    )

    @Test
    fun `generates for a public top-level composable`() {
        val result = compile("PublicCard", "@Prev @Composable fun PublicCard(name: String) {}")

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        assertTrue(result.messages, result.generatedFile("PublicCardPreview.kt") != null)
    }

    @Test
    fun `generates for an internal top-level composable`() {
        // internal is the narrowest visibility that still works: it is module-scoped, and the
        // generated file lands in the same module.
        val result = compile("InternalCard", "@Prev @Composable internal fun InternalCard(name: String) {}")

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        assertTrue(result.messages, result.generatedFile("InternalCardPreview.kt") != null)
    }

    @Test
    fun `rejects a private top-level composable`() {
        val result = compile("PrivateCard", "@Prev @Composable private fun PrivateCard(name: String) {}")

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages, result.messages.contains("cannot generate a Preview for 'PrivateCard'"))
        assertTrue(result.messages, result.messages.contains("visible only inside its own file"))
        assertTrue(result.messages, result.messages.contains("Widen it to internal or public"))
        // The point of the check: no file is written, so the build fails on PrevHam's own message
        // rather than on "Cannot access 'fun PrivateCard'" from a file the user never wrote.
        assertNull(result.generatedFile("PrivateCardPreview.kt"))
    }

    @Test
    fun `rejects a composable declared in an object`() {
        val result =
            compile("ObjectCard", "object Holder { @Prev @Composable fun ObjectCard(name: String) {} }")

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages, result.messages.contains("cannot generate a Preview for 'ObjectCard'"))
        assertTrue(result.messages, result.messages.contains("declared inside 'Holder'"))
        assertNull(result.generatedFile("ObjectCardPreview.kt"))
    }

    @Test
    fun `rejects a composable declared in a class`() {
        val result =
            compile("ClassCard", "class Holder { @Prev @Composable fun ClassCard(name: String) {} }")

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages, result.messages.contains("cannot generate a Preview for 'ClassCard'"))
        assertTrue(result.messages, result.messages.contains("needs an instance"))
        assertNull(result.generatedFile("ClassCardPreview.kt"))
    }

    @Test
    fun `rejects a protected composable`() {
        val result =
            compile("ProtectedCard", "open class Holder { @Prev @Composable protected fun ProtectedCard(name: String) {} }")

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages, result.messages.contains("cannot generate a Preview for 'ProtectedCard'"))
        assertNull(result.generatedFile("ProtectedCardPreview.kt"))
    }

    @Test
    fun `still generates for the other composables in the same file`() {
        // One rejected composable must not cost the file its other Previews.
        val result =
            compile(
                "MixedCard",
                """
                @Prev @Composable private fun HiddenCard(name: String) {}
                @Prev @Composable fun VisibleCard(name: String) {}
                """,
            )

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(result.messages, result.messages.contains("cannot generate a Preview for 'HiddenCard'"))
        assertTrue(result.messages, result.generatedFile("VisibleCardPreview.kt") != null)
    }
}
