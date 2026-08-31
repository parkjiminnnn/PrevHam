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

// Which slots exist is decided by PrevHam's own generation - cycle detection, the stub budget,
// typealias resolution, which generator claims a type - none of which is readable from the source
// text. The manifest is how that leaves the compilation, so the generation task can ask about the
// slots that are actually filled rather than guessing at them.
@OptIn(ExperimentalCompilerApi::class)
class SlotManifestTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun manifestFor(
        name: String,
        declarations: String,
        parameter: String,
    ): String {
        val manifest = File(folder.root, "slots/mock-value-slots.json")
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "$name.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    $declarations

                    @Prev
                    @Composable
                    fun $name($parameter) {}
                    """,
                ),
                options = mapOf("prevham.slotManifest" to manifest.absolutePath),
            )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        assertTrue("manifest was not written", manifest.exists())
        return manifest.readText()
    }

    @Test
    fun `lists a data class field with its owner, name and type`() {
        val manifest = manifestFor("Festival", "data class Festival(val festivalName: String)", "festival: Festival")

        assertTrue(manifest, manifest.contains(""""slot": "test.Festival.festivalName""""))
        assertTrue(manifest, manifest.contains(""""owner": "test.Festival""""))
        assertTrue(manifest, manifest.contains(""""name": "festivalName""""))
        assertTrue(manifest, manifest.contains(""""type": "kotlin.String""""))
    }

    @Test
    fun `lists the composable's own parameter`() {
        val manifest = manifestFor("Greeting", "", "name: String")

        assertTrue(manifest, manifest.contains(""""slot": "test.Greeting.name""""))
        assertTrue(manifest, manifest.contains(""""owner": "test.Greeting""""))
    }

    @Test
    fun `keeps owner and name apart for a nested declaration`() {
        // com.example.Outer.Inner.name has dots on both sides of the split, so the boundary can't
        // be recovered from the joined path - which is why both are written.
        val manifest =
            manifestFor(
                "Nested",
                "class Outer { data class Inner(val title: String) }",
                "inner: Outer.Inner",
            )

        assertTrue(manifest, manifest.contains(""""owner": "test.Outer.Inner""""))
        assertTrue(manifest, manifest.contains(""""name": "title""""))
    }

    @Test
    fun `lists a slot reached through a nested data class`() {
        val manifest =
            manifestFor(
                "Screen",
                """
                data class Festival(val festivalName: String)
                data class Screen(val festival: Festival)
                """,
                "screen: Screen",
            )

        assertTrue(manifest, manifest.contains(""""slot": "test.Festival.festivalName""""))
    }

    @Test
    fun `omits a slot the recursion never fills`() {
        // Cycle detection stops at `next`, so nothing below it is ever filled - and a tool reading
        // the source alone would have no way to know that.
        val manifest =
            manifestFor("Node", "data class Node(val title: String, val next: Node?)", "node: Node")

        assertEquals(manifest, 1, manifest.split(""""slot":""").size - 1)
        assertTrue(manifest, manifest.contains(""""slot": "test.Node.title""""))
    }

    @Test
    fun `lists each slot once and in a stable order`() {
        val manifest =
            manifestFor(
                "Ordered",
                """
                data class B(val b: String)
                data class A(val a: String)
                data class Both(val first: A, val second: B, val third: A)
                """,
                "both: Both",
            )

        assertEquals(manifest, 2, manifest.split(""""slot":""").size - 1)
        assertTrue(manifest, manifest.indexOf("test.A.a") < manifest.indexOf("test.B.b"))
    }

    @Test
    fun `writes nothing when the option is absent`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "Plain.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    @Prev
                    @Composable
                    fun Plain(name: String) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        assertFalse(File(folder.root, "slots").exists())
    }

    @Test
    fun `records the resolved type for a slot declared with a typealias`() {
        // A consumer of the manifest has to know the slot takes a String; "UserName" doesn't say so.
        val manifest =
            manifestFor(
                "Aliased",
                """
                typealias UserName = String
                data class Profile(val name: UserName)
                """,
                "profile: Profile",
            )

        assertTrue(manifest, manifest.contains(""""type": "kotlin.String""""))
        assertFalse(manifest, manifest.contains("UserName"))
    }

    @Test
    fun `lists numeric slots but not boolean or char`() {
        val manifest =
            manifestFor(
                "Mixed",
                "data class Mixed(val visitors: Int, val budget: Long, val on: Boolean, val initial: Char)",
                "mixed: Mixed",
            )

        assertTrue(manifest, manifest.contains(""""type": "kotlin.Int""""))
        assertTrue(manifest, manifest.contains(""""type": "kotlin.Long""""))
        assertFalse(manifest, manifest.contains("kotlin.Boolean"))
        assertFalse(manifest, manifest.contains("kotlin.Char"))
    }
}
