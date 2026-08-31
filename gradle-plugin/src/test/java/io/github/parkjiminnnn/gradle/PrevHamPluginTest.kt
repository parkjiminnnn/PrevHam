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

    @Test
    fun `registers the generation task`() {
        val output =
            build(
                """
                plugins {
                    kotlin("jvm") version "2.2.10"
                    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
                    id("io.github.parkjiminnnn.prevham")
                }
                repositories { mavenCentral() }
                """.trimIndent(),
                "tasks",
                "--group",
                "prevham",
            )

        assertTrue(output, output.contains("prevhamGenerateMockValues"))
    }

    @Test
    fun `sets both KSP options so the two sides agree on a path`() {
        // The compiler writes the manifest and reads the values; the task reads the manifest and
        // writes the values. Left to the consumer that is the same path written twice, with nothing
        // to notice when they drift - so the plugin owns both.
        val output =
            build(
                """
                plugins {
                    kotlin("jvm") version "2.2.10"
                    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
                    id("io.github.parkjiminnnn.prevham")
                }
                repositories { mavenCentral() }
                val kspExtension = extensions.getByType(com.google.devtools.ksp.gradle.KspExtension::class.java)
                tasks.register("printKspArgs") {
                    // Read in doLast: the plugin sets the options in afterEvaluate, so a consumer's
                    // own prevham { } block has run first.
                    doLast { kspExtension.arguments.forEach { println("kspArg " + it.key + "=" + it.value) } }
                }
                """.trimIndent(),
                "printKspArgs",
            )

        assertTrue(output, output.contains("kspArg prevham.mockValues="))
        assertTrue(output, output.contains("kspArg prevham.slotManifest="))
        assertTrue(output, output.contains("src/main/prevham/mock-values.json"))
        assertTrue(output, output.contains("generated/prevham/mock-value-slots.json"))
    }

    @Test
    fun `honours a configured path`() {
        val output =
            build(
                """
                plugins {
                    kotlin("jvm") version "2.2.10"
                    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
                    id("io.github.parkjiminnnn.prevham")
                }
                repositories { mavenCentral() }
                prevham {
                    mockValues.set(layout.projectDirectory.file("custom/values.json"))
                }
                val kspExtension = extensions.getByType(com.google.devtools.ksp.gradle.KspExtension::class.java)
                tasks.register("printKspArgs") {
                    doLast { kspExtension.arguments.forEach { println("kspArg " + it.key + "=" + it.value) } }
                }
                """.trimIndent(),
                "printKspArgs",
            )

        assertTrue(output, output.contains("custom/values.json"))
    }

    @Test
    fun `says to build first when no manifest has been written`() {
        // Running the task before ever building is the one case where the manifest is missing, and
        // it deserves this rather than a Gradle error about a declared input.
        val output =
            build(
                """
                plugins {
                    kotlin("jvm") version "2.2.10"
                    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
                    id("io.github.parkjiminnnn.prevham")
                }
                repositories { mavenCentral() }
                """.trimIndent(),
                "prevhamGenerateMockValues",
            )

        assertTrue(output, output.contains("no slots found"))
        assertTrue(output, output.contains("Build the project first"))
    }

    // Not named newFile: TemporaryFolder has a member by that name, members win over extensions, and
    // JUnit's cannot create a nested path.
    private fun TemporaryFolder.fileAt(path: String): File = File(root, path).apply { parentFile.mkdirs() }

    @Test
    fun `scaffolds the value file from a manifest`() {
        // The paths are the part a person can't reasonably produce - fully qualified, silently
        // ignored when wrong, hundreds of them in a real model. Filling in the values from there is
        // ordinary work; finding them is not.
        File(projectDir.root, "slots.json").writeText(
            """
            {"slots": [
              {"slot": "com.a.Festival.name", "owner": "com.a.Festival",
               "name": "name", "type": "kotlin.String"}
            ]}
            """.trimIndent(),
        )

        val output =
            build(
                """
                plugins {
                    kotlin("jvm") version "2.2.10"
                    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
                    id("io.github.parkjiminnnn.prevham")
                }
                repositories { mavenCentral() }
                prevham {
                    slotManifest.set(layout.projectDirectory.file("slots.json"))
                    mockValues.set(layout.projectDirectory.file("values.json"))
                }
                """.trimIndent(),
                "prevhamGenerateMockValues",
            )

        assertTrue(output, output.contains("1 slot(s), 1 without a value"))
        val values = File(projectDir.root, "values.json").readText()
        assertTrue(values, values.contains(""""com.a.Festival.name": """""))
    }

    @Test
    fun `leaves a decided value alone on a second run`() {
        // A hand-written value surviving every later run is the whole reason values live in a file
        // rather than in generated code.
        File(projectDir.root, "slots.json").writeText(
            """
            {"slots": [
              {"slot": "com.a.Festival.name", "owner": "com.a.Festival",
               "name": "name", "type": "kotlin.String"}
            ]}
            """.trimIndent(),
        )
        File(projectDir.root, "values.json").writeText("""{"com.a.Festival.name": "2026 대동제"}""")

        val output =
            build(
                """
                plugins {
                    kotlin("jvm") version "2.2.10"
                    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
                    id("io.github.parkjiminnnn.prevham")
                }
                repositories { mavenCentral() }
                prevham {
                    slotManifest.set(layout.projectDirectory.file("slots.json"))
                    mockValues.set(layout.projectDirectory.file("values.json"))
                }
                """.trimIndent(),
                "prevhamGenerateMockValues",
            )

        assertTrue(output, output.contains("1 slot(s), 0 without a value"))
        assertTrue(output, output.contains("nothing to do"))
        val values = File(projectDir.root, "values.json").readText()
        assertTrue(values, values.contains("2026 대동제"))
    }

    @Test
    fun `scaffolds the paths when no endpoint is configured`() {
        // Writing the values by hand should not require naming a model: the paths are the part
        // nobody can reasonably produce, and they are useful on their own.
        projectDir.fileAt("build/generated/prevham/mock-value-slots.json").writeText(
            """
            {"slots": [{"slot": "com.a.Festival.name", "owner": "com.a.Festival",
                        "name": "name", "type": "kotlin.String"}]}
            """.trimIndent(),
        )

        val output =
            build(
                """
                plugins {
                    kotlin("jvm") version "2.2.10"
                    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
                    id("io.github.parkjiminnnn.prevham")
                }
                repositories { mavenCentral() }
                """.trimIndent(),
                "prevhamGenerateMockValues",
            )

        assertTrue(output, output.contains("no baseUrl/model configured"))
        val written = File(projectDir.root, "src/main/prevham/mock-values.json").readText()
        assertTrue(written, written.contains("com.a.Festival.name"))
    }

    @Test
    fun `keeps a value that has already been decided`() {
        projectDir.fileAt("build/generated/prevham/mock-value-slots.json").writeText(
            """
            {"slots": [{"slot": "com.a.Festival.name", "owner": "com.a.Festival",
                        "name": "name", "type": "kotlin.String"}]}
            """.trimIndent(),
        )
        projectDir.fileAt("src/main/prevham/mock-values.json").writeText(
            """{"com.a.Festival.name": "written by hand"}""",
        )

        val output =
            build(
                """
                plugins {
                    kotlin("jvm") version "2.2.10"
                    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
                    id("io.github.parkjiminnnn.prevham")
                }
                repositories { mavenCentral() }
                """.trimIndent(),
                "prevhamGenerateMockValues",
            )

        assertTrue(output, output.contains("nothing to do"))
        val written = File(projectDir.root, "src/main/prevham/mock-values.json").readText()
        assertTrue(written, written.contains("written by hand"))
    }

    @Test
    fun `refuses a key set in the project's gradle properties`() {
        // That file is normally committed, and setting the key there works perfectly - which is what
        // makes it dangerous. Nothing would go wrong until the key was public.
        projectDir.fileAt("gradle.properties").writeText("prevham.apiKey=leaked-key\n")
        projectDir.fileAt("build/generated/prevham/mock-value-slots.json").writeText(
            """
            {"slots": [{"slot": "com.a.Festival.name", "owner": "com.a.Festival",
                        "name": "name", "type": "kotlin.String"}]}
            """.trimIndent(),
        )

        val output =
            build(
                """
                plugins {
                    kotlin("jvm") version "2.2.10"
                    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
                    id("io.github.parkjiminnnn.prevham")
                }
                repositories { mavenCentral() }
                """.trimIndent(),
                "prevhamGenerateMockValues",
                expectFailure = true,
            )

        assertTrue(output, output.contains("normally committed"))
        assertTrue(output, output.contains("local.properties"))
        assertTrue(output, output.contains("PREVHAM_API_KEY"))
        assertTrue(output, output.contains("compromised"))
    }

    @Test
    fun `does not mistake a comment for a declaration`() {
        // A gradle.properties explaining where the key should not go must not itself be refused.
        projectDir.fileAt("gradle.properties").writeText("# do not put prevham.apiKey here\n")
        projectDir.fileAt("build/generated/prevham/mock-value-slots.json").writeText(
            """
            {"slots": [{"slot": "com.a.Festival.name", "owner": "com.a.Festival",
                        "name": "name", "type": "kotlin.String"}]}
            """.trimIndent(),
        )

        val output =
            build(
                """
                plugins {
                    kotlin("jvm") version "2.2.10"
                    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
                    id("io.github.parkjiminnnn.prevham")
                }
                repositories { mavenCentral() }
                """.trimIndent(),
                "prevhamGenerateMockValues",
            )

        assertTrue(output, output.contains("no baseUrl/model configured"))
    }
}
