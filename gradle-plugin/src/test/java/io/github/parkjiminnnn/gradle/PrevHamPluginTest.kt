package io.github.parkjiminnnn.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// Runs real Gradle builds through TestKit, so what's asserted is what a consumer's build actually
// resolves - not what the plugin code appears to do.
class PrevHamPluginTest {
    @get:Rule
    val projectDir = TemporaryFolder()

    private fun build(
        buildScript: String,
        vararg arguments: String,
        expectFailure: Boolean = false,
    ): String {
        projectDir.newFile("settings.gradle.kts").writeText("""rootProject.name = "consumer"""")
        projectDir.newFile("build.gradle.kts").writeText(buildScript)
        val runner =
            GradleRunner
                .create()
                .withProjectDir(projectDir.root)
                .withPluginClasspath()
                .withArguments(*arguments, "--stacktrace")
        return if (expectFailure) runner.buildAndFail().output else runner.build().output
    }

    // Prints the resolved coordinates rather than asserting on the configuration object, so the
    // test fails if the dependency is declared but doesn't resolve to what's expected.
    private val printDependencies =
        """
        tasks.register("printPrevHamDependencies") {
            val implementation = configurations.getByName("implementation").allDependencies
                .map { "implementation ${'$'}{it.group}:${'$'}{it.name}:${'$'}{it.version}" }
            val ksp = configurations.getByName("ksp").allDependencies
                .map { "ksp ${'$'}{it.group}:${'$'}{it.name}:${'$'}{it.version}" }
            doLast { (implementation + ksp).forEach(::println) }
        }
        """.trimIndent()

    @Test
    fun `declares runtime, compiler and MockK`() {
        val output =
            build(
                """
                plugins {
                    kotlin("jvm") version "2.2.10"
                    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
                    id("io.github.parkjiminnnn.prevham")
                }
                repositories { mavenCentral() }
                $printDependencies
                """.trimIndent(),
                "printPrevHamDependencies",
            )

        assertTrue(output, output.contains("implementation io.github.parkjiminnnn:prevham-runtime:$PREVHAM_VERSION"))
        assertTrue(output, output.contains("ksp io.github.parkjiminnnn:prevham-compiler:$PREVHAM_VERSION"))
        assertTrue(output, output.contains("implementation io.mockk:mockk:$MOCKK_VERSION"))
    }

    @Test
    fun `puts runtime and compiler on the same version`() {
        val output =
            build(
                """
                plugins {
                    kotlin("jvm") version "2.2.10"
                    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
                    id("io.github.parkjiminnnn.prevham")
                }
                repositories { mavenCentral() }
                $printDependencies
                """.trimIndent(),
                "printPrevHamDependencies",
            )

        // The whole point of the plugin: the two artifacts can't drift apart, because neither
        // version is written by the consumer.
        val versions =
            Regex("""prevham-(?:runtime|compiler):(\S+)""").findAll(output).map { it.groupValues[1] }.toSet()
        assertTrue(output, versions == setOf(PREVHAM_VERSION))
    }

    @Test
    fun `does not apply KSP itself`() {
        // Applying KSP here would pin the consumer's Kotlin version to PrevHam's, so its absence is
        // a deliberate part of the contract rather than an oversight.
        val output =
            build(
                """
                plugins {
                    kotlin("jvm") version "2.2.10"
                    id("io.github.parkjiminnnn.prevham")
                }
                repositories { mavenCentral() }
                """.trimIndent(),
                "help",
                expectFailure = true,
            )

        assertTrue(output, output.contains("PrevHam needs the KSP plugin"))
        assertTrue(output, output.contains("""id("com.google.devtools.ksp")"""))
    }

    @Test
    fun `reports a missing Kotlin plugin before a missing KSP`() {
        // KSP can't be applied without Kotlin, so a project with neither should hear about Kotlin
        // first. This is also the message a Kotlin Multiplatform project gets, which is why it
        // names the supported project types rather than just listing plugin ids.
        val output =
            build(
                """
                plugins {
                    id("io.github.parkjiminnnn.prevham")
                }
                repositories { mavenCentral() }
                """.trimIndent(),
                "help",
                expectFailure = true,
            )

        assertTrue(output, output.contains("works on Kotlin Android and Kotlin JVM projects"))
        assertTrue(output, output.contains("Kotlin Multiplatform isn't supported"))
    }

    @Test
    fun `works regardless of where it sits in the plugins block`() {
        // Configurations the plugin adds to are created by Kotlin and KSP, so applying it first
        // must still work - it reacts to those plugins rather than assuming they ran already.
        val output =
            build(
                """
                plugins {
                    id("io.github.parkjiminnnn.prevham")
                    kotlin("jvm") version "2.2.10"
                    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
                }
                repositories { mavenCentral() }
                $printDependencies
                """.trimIndent(),
                "printPrevHamDependencies",
            )

        assertTrue(output, output.contains("implementation io.github.parkjiminnnn:prevham-runtime:$PREVHAM_VERSION"))
        assertTrue(output, output.contains("ksp io.github.parkjiminnnn:prevham-compiler:$PREVHAM_VERSION"))
    }

    private fun TemporaryFolder.newFile(name: String): File = File(root, name).apply { parentFile.mkdirs() }
}
