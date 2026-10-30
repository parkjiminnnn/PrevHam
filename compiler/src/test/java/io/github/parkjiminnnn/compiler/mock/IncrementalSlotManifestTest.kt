package io.github.parkjiminnnn.compiler.mock

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// KSP processes incrementally: on a change it hands the processor only the files affected by it. The
// manifest was written from what the run met, so an ordinary build after touching one unrelated file
// shrank it to that run's share - 36 slots down to 12 on :sample, with every Preview intact and
// nothing to say so (issue #139).
//
// kctfork compiles every file every time and so can never see this. These are real Gradle builds of
// a consumer, running the processor as built in this module, and none of them forces anything: they
// build, change a file the way a developer would, and build again.
class IncrementalSlotManifestTest {
    @get:Rule
    val projectDir = TemporaryFolder()

    private val manifest get() = File(projectDir.root, "build/prevham/mock-value-slots.json")

    @Before
    fun consumer() {
        projectDir.newFile("settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
            rootProject.name = "consumer"
            """.trimIndent(),
        )
        projectDir.newFile("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "${property("kotlinVersion")}"
                id("com.google.devtools.ksp") version "${property("kspVersion")}"
            }
            repositories { mavenCentral() }

            dependencies {
                implementation(files(${paths("runtimeClasspath")}))
                ksp(files(${paths("processorClasspath")}))
            }

            ksp { arg("prevham.slotManifest", "${'$'}{layout.buildDirectory.get()}/prevham/mock-value-slots.json") }
            """.trimIndent(),
        )
        source("androidx/compose/runtime/Composable.kt", "package androidx.compose.runtime\nannotation class Composable")
        source("androidx/compose/ui/tooling/preview/Preview.kt", "package androidx.compose.ui.tooling.preview\nannotation class Preview")
        source(
            "demo/Festival.kt",
            """
            package demo

            import androidx.compose.runtime.Composable
            import io.github.parkjiminnnn.runtime.Prev

            data class Festival(val festivalName: String, val slogan: String)

            @Prev
            @Composable
            fun FestivalCard(festival: Festival) {}
            """.trimIndent(),
        )
        source("demo/Profile.kt", profile(prev = true))
        source("demo/Plain.kt", "package demo\n\nclass Plain(val a: Int)")
    }

    @Test
    fun `keeps every slot when a file without Prev changes`() {
        val before = build()
        assertEquals(ALL, before)

        source("demo/Plain.kt", "package demo\n\nclass Plain(val a: Int, val b: Int)")

        assertEquals(before, build())
    }

    @Test
    fun `drops only the slots of a function whose Prev is removed`() {
        assertEquals(ALL, build())

        source("demo/Profile.kt", profile(prev = false))

        assertEquals(listOf("demo.Festival.festivalName", "demo.Festival.slogan"), build())
    }

    @Test
    fun `drops the slots of a deleted file`() {
        assertEquals(ALL, build())

        File(projectDir.root, "src/main/kotlin/demo/Profile.kt").delete()

        assertEquals(listOf("demo.Festival.festivalName", "demo.Festival.slogan"), build())
    }

    @Test
    fun `keeps a slot that another file still fills`() {
        // The slot is recorded per file, so the file that still reaches it keeps it in the manifest
        // however the other one changes.
        source("demo/Banner.kt", banner(prev = true))
        val before = build()
        assertEquals(ALL, before)

        source("demo/Banner.kt", banner(prev = false))

        assertEquals(before, build())
    }

    @Test
    fun `comes out the same as a clean build`() {
        build()
        source("demo/Plain.kt", "package demo\n\nclass Plain(val a: Int, val b: Int)")
        source("demo/Profile.kt", profile(prev = false))
        build()
        val incremental = manifest.readText()

        run("clean", "kspKotlin")

        assertEquals(incremental, manifest.readText())
    }

    /** Builds without forcing anything, and returns the slots the manifest lists. */
    private fun build(): List<String> {
        run("kspKotlin")
        return SLOT.findAll(manifest.readText()).map { it.groupValues[1] }.toList()
    }

    private fun run(vararg tasks: String): BuildResult =
        GradleRunner
            .create()
            .withProjectDir(projectDir.root)
            .withArguments(*tasks, "--stacktrace")
            .build()

    private fun source(
        path: String,
        text: String,
    ) {
        File(projectDir.root, "src/main/kotlin/$path").apply { parentFile.mkdirs() }.writeText(text)
    }

    private fun profile(prev: Boolean) =
        """
        package demo

        import androidx.compose.runtime.Composable
        import io.github.parkjiminnnn.runtime.Prev

        data class Profile(val nickname: String)

        ${if (prev) "@Prev" else ""}
        @Composable
        fun ProfileCard(profile: Profile) {}
        """.trimIndent()

    private fun banner(prev: Boolean) =
        """
        package demo

        import androidx.compose.runtime.Composable
        import io.github.parkjiminnnn.runtime.Prev

        ${if (prev) "@Prev" else ""}
        @Composable
        fun FestivalBanner(festival: Festival) {}
        """.trimIndent()

    private fun property(name: String): String = checkNotNull(System.getProperty("prevham.test.$name")) { "prevham.test.$name is not set" }

    /** A classpath property as Kotlin string literals, for `files(...)` in the consumer's build script. */
    private fun paths(name: String): String =
        property(name).split(File.pathSeparator).joinToString { "\"" + it.replace("\\", "\\\\") + "\"" }

    private companion object {
        val ALL = listOf("demo.Festival.festivalName", "demo.Festival.slogan", "demo.Profile.nickname")
        val SLOT = Regex(""""slot": "([^"]+)"""")
    }
}
