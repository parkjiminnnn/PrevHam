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

// The same declaration used to behave differently depending on how PrevHam built it: a data class
// field is constructed and flows through a slot, an interface member is replaced by a mock and got
// nothing. In Android that gap covered an ordinary shape - a composable taking a ViewModel.
@OptIn(ExperimentalCompilerApi::class)
class MockedMemberValueTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun compile(
        declarations: String,
        parameter: String,
        values: String? = null,
        name: String = "Home",
    ): Pair<String, String> {
        val options =
            buildMap {
                values?.let { put("prevham.mockValues", folder.newFile("values.json").apply { writeText(it) }.absolutePath) }
                put("prevham.slotManifest", folder.root.resolve("slots.json").absolutePath)
            }
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
                options = options,
            )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("${name}Preview.kt")) { result.messages }
        val manifest =
            folder.root
                .resolve("slots.json")
                .takeIf { it.isFile }
                ?.readText()
                .orEmpty()
        return generated to manifest
    }

    @Test
    fun `stubs an interface property with the configured value`() {
        val (generated, _) =
            compile(
                "interface HomeViewModel { val title: String }",
                "viewModel: HomeViewModel",
                values = """{"test.HomeViewModel.title": "제 1회 대학 음악제"}""",
            )

        assertTrue(generated, generated.contains("""every { this@mockk.title } returns "제 1회 대학 음악제""""))
    }

    @Test
    fun `qualifies the receiver so a colliding name still compiles`() {
        // The stub goes through the same this@mockk. qualification as every other one (issue #83):
        // inside every {} the mock sits outside MockK's matcher scope, and a member called get loses
        // an unqualified lookup. Compiling is the assertion.
        val (generated, _) =
            compile(
                "interface Cache { val get: String }",
                "cache: Cache",
                values = """{"test.Cache.get": "hit"}""",
            )

        assertTrue(generated, generated.contains("""every { this@mockk.get } returns "hit""""))
    }

    @Test
    fun `stubs a numeric member with the parsed literal`() {
        val (generated, _) =
            compile(
                "interface Counts { val visitors: Int; val budget: Long }",
                "counts: Counts",
                values = """{"test.Counts.visitors": "12000", "test.Counts.budget": "45000000"}""",
            )

        assertTrue(generated, generated.contains("every { this@mockk.visitors } returns 12000"))
        assertTrue(generated, generated.contains("every { this@mockk.budget } returns 45000000L"))
    }

    @Test
    fun `stubs a member function too`() {
        val (generated, _) =
            compile(
                "interface Titles { fun titleFor(id: Int): String }",
                "titles: Titles",
                values = """{"test.Titles.titleFor": "제 1회 대학 음악제"}""",
            )

        assertTrue(generated, generated.contains("""every { this@mockk.titleFor(any()) } returns "제 1회 대학 음악제""""))
    }

    @Test
    fun `stubs nothing when no value was configured`() {
        // The bare relaxed mock is still the right answer for an undecided member. This is what
        // keeps the stub count a sum bounded by a curated file rather than by the type's shape.
        val (generated, _) = compile("interface HomeViewModel { val title: String }", "viewModel: HomeViewModel")

        assertTrue(generated, generated.contains("mockk<HomeViewModel>(relaxed = true)"))
        assertFalse(generated, generated.contains("every {"))
    }

    @Test
    fun `records the member in the manifest even with nothing to stub`() {
        // The chicken and egg this has to break: a value cannot be written for a path the manifest
        // never lists, so the slot is recorded whether or not anything is stubbed with it.
        val (_, manifest) = compile("interface HomeViewModel { val title: String }", "viewModel: HomeViewModel")

        assertTrue(manifest, manifest.contains("test.HomeViewModel.title"))
    }

    @Test
    fun `leaves a data class field exactly as it was`() {
        val (generated, _) =
            compile(
                "data class Screen(val title: String)",
                "screen: Screen",
                values = """{"test.Screen.title": "제 1회 대학 음악제"}""",
            )

        assertTrue(generated, generated.contains("""title = "제 1회 대학 음악제""""))
        assertFalse(generated, generated.contains("every {"))
    }

    @Test
    fun `does not open a compiled dependency`() {
        // The gate that fixed #75, on a different axis. java.time.LocalDate has 56 stubbable
        // members; listing them would send every one to a model to be answered.
        val (generated, manifest) =
            compile("import java.time.LocalDate", "date: LocalDate", values = """{"java.time.LocalDate.year": "2026"}""")

        assertFalse(manifest, manifest.contains("java.time.LocalDate"))
        assertFalse(generated, generated.contains("every {"))
    }

    @Test
    fun `skips a member inherited from a compiled supertype`() {
        // getAllProperties() reaches through supertypes, so the gate is applied to whichever type
        // declares the member rather than to the one being mocked.
        val (_, manifest) =
            compile(
                """
                import java.io.Closeable
                interface Session : Closeable { val token: String }
                """,
                "session: Session",
            )

        assertTrue(manifest, manifest.contains("test.Session.token"))
        assertFalse(manifest, manifest.contains("java.io"))
    }

    @Test
    fun `skips a generic member whose type is only known at the use site`() {
        // Repository<String>.id and Repository<Int>.id are one declaring path and cannot hold two
        // different values, so the declared type - the type parameter - is what decides.
        val (generated, manifest) =
            compile(
                """
                interface Repository<T> { val id: T }
                """,
                "repository: Repository<String>",
                values = """{"test.Repository.id": "42"}""",
            )

        assertFalse(manifest, manifest.contains("test.Repository.id"))
        assertFalse(generated, generated.contains("""returns "42""""))
    }

    @Test
    fun `leaves Boolean and Char alone`() {
        val (generated, manifest) =
            compile(
                "interface Flags { val enabled: Boolean; val initial: Char }",
                "flags: Flags",
                values = """{"test.Flags.enabled": "false", "test.Flags.initial": "z"}""",
            )

        assertFalse(manifest, manifest.contains("test.Flags.enabled"))
        assertFalse(generated, generated.contains("every {"))
    }

    @Test
    fun `resolves a typealias member to what it stands for`() {
        val (generated, _) =
            compile(
                """
                typealias UserName = String
                interface Profile { val name: UserName }
                """,
                "profile: Profile",
                values = """{"test.Profile.name": "김지민"}""",
            )

        assertTrue(generated, generated.contains("""every { this@mockk.name } returns "김지민""""))
    }

    @Test
    fun `keeps stubbing what has to be stubbed`() {
        // A configured value must not displace the stub an erased member needs (issue #59).
        val (generated, _) =
            compile(
                """
                import kotlinx.coroutines.flow.StateFlow
                data class UiState(val label: String)
                interface HomeViewModel {
                    val title: String
                    val uiState: StateFlow<UiState>
                }
                """,
                "viewModel: HomeViewModel",
                values = """{"test.HomeViewModel.title": "제 1회 대학 음악제"}""",
            )

        assertTrue(generated, generated.contains("""every { this@mockk.title } returns "제 1회 대학 음악제""""))
        assertTrue(generated, generated.contains("every { this@mockk.uiState } returns MutableStateFlow("))
    }

    @Test
    fun `a wide interface stays within the stub budget`() {
        val members = (1..600).joinToString("; ") { "val field$it: String" }
        val values = (1..600).joinToString(", ") { """"test.Wide.field$it": "v$it"""" }
        val (generated, _) =
            compile("interface Wide { $members }", "wide: Wide", values = "{$values}", name = "WideCard")

        // MAX_STUBS caps the output; the rest degrade to relaxed mode rather than to a build that
        // cannot compile.
        assertTrue(generated, generated.contains("every { this@mockk.field1 } returns"))
        val stubs = generated.split("every {").size - 1
        assertTrue("$stubs stubs", stubs in 1..MockContext.MAX_STUBS)
    }
}
