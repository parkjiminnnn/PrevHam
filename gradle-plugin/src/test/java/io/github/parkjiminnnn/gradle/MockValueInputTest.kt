package io.github.parkjiminnnn.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// The value file reaches the processor as a string KSP option, which Gradle sees as a string: the
// path never changes, so a build after the file changed found every input identical and skipped
// KSP. The Preview kept its old values - including straight after prevhamGenerateMockValues had
// written new ones (issue #133).
//
// Every earlier check of the value pipeline forced KSP with --rerun-tasks and so never saw this.
// These run the consumer's own sequence and force nothing: build, change the file, build again.
class MockValueInputTest {
    @get:Rule
    val projectDir = TemporaryFolder()

    private val valueFile get() = File(projectDir.root, "src/main/prevham/mock-values.json")
    private val generatedPreview get() = File(projectDir.root, "build/generated/ksp/main/kotlin/demo/CardPreview.kt")

    @Before
    fun consumer() {
        projectDir.newFile("settings.gradle.kts").writeText("""rootProject.name = "consumer"""")
        projectDir.newFile("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "2.2.10"
                id("com.google.devtools.ksp") version "$KSP_VERSION"
                id("io.github.parkjiminnnn.prevham")
            }
            repositories { mavenCentral() }

            // What is under test is the plugin's wiring, not the processor, so the processor is a
            // released one. The plugin asks for its own version, which is not on Maven Central
            // until it is released - pinning keeps this test from depending on that.
            configurations.all {
                resolutionStrategy.force(
                    "io.github.parkjiminnnn:prevham-runtime:$RELEASED_PROCESSOR",
                    "io.github.parkjiminnnn:prevham-compiler:$RELEASED_PROCESSOR",
                )
            }
            """.trimIndent(),
        )
        source("androidx/compose/runtime/Composable.kt", "package androidx.compose.runtime\nannotation class Composable")
        source("androidx/compose/ui/tooling/preview/Preview.kt", "package androidx.compose.ui.tooling.preview\nannotation class Preview")
        source(
            "demo/Card.kt",
            """
            package demo

            import androidx.compose.runtime.Composable
            import io.github.parkjiminnnn.runtime.Prev

            data class Festival(val festivalName: String)

            @Prev
            @Composable
            fun Card(festival: Festival) {}
            """.trimIndent(),
        )
    }

    private fun source(
        path: String,
        text: String,
    ) {
        File(projectDir.root, "src/main/kotlin/$path").apply { parentFile.mkdirs() }.writeText(text)
    }

    private fun writeValue(festivalName: String) {
        valueFile.parentFile.mkdirs()
        valueFile.writeText("""{ "demo.Festival.festivalName": "$festivalName" }""")
    }

    private fun ksp(): BuildResult =
        GradleRunner
            .create()
            .withProjectDir(projectDir.root)
            .withPluginClasspath()
            .withArguments("kspKotlin", "--stacktrace")
            .build()

    private fun BuildResult.kspOutcome() = task(":kspKotlin")?.outcome

    @Test
    fun `builds without a value file`() {
        // The file does not exist until values are first generated, and an absent input must not
        // fail the build of a project that never uses the feature.
        val result = ksp()

        assertEquals(result.output, TaskOutcome.SUCCESS, result.kspOutcome())
        assertTrue(generatedPreview.readText(), generatedPreview.readText().contains("""festivalName = "mock""""))
    }

    @Test
    fun `re-runs KSP once the value file is created`() {
        // The first run of prevhamGenerateMockValues: the file appears where there was none.
        ksp()
        writeValue("2026 대동제")

        val result = ksp()

        assertEquals(result.output, TaskOutcome.SUCCESS, result.kspOutcome())
        assertTrue(generatedPreview.readText(), generatedPreview.readText().contains("""festivalName = "2026 대동제""""))
    }

    @Test
    fun `re-runs KSP when a value changes`() {
        // A later run, or a hand edit - the case reported from a real app.
        writeValue("2026 대동제")
        ksp()
        writeValue("제 1회 대학 음악제")

        val result = ksp()

        assertEquals(result.output, TaskOutcome.SUCCESS, result.kspOutcome())
        assertTrue(generatedPreview.readText(), generatedPreview.readText().contains("""festivalName = "제 1회 대학 음악제""""))
    }

    @Test
    fun `stays up to date when nothing changed`() {
        // The other half of the fix. Making KSP run on every build would also make the values
        // arrive, and would slow down every build of every consumer to do it.
        writeValue("2026 대동제")
        ksp()

        val result = ksp()

        assertEquals(result.output, TaskOutcome.UP_TO_DATE, result.kspOutcome())
    }

    private companion object {
        // Any release that reads the value file. The fix is entirely in the plugin.
        const val RELEASED_PROCESSOR = "1.4.0"
    }
}
