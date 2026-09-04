package io.github.parkjiminnnn.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Declares PrevHam's dependencies so a consumer doesn't have to.
 *
 * Applying this plugin is equivalent to writing:
 *
 * ```kotlin
 * dependencies {
 *     implementation("io.github.parkjiminnnn:prevham-runtime:<version>")
 *     ksp("io.github.parkjiminnnn:prevham-compiler:<version>")
 *     implementation("io.mockk:mockk:<version>")
 * }
 * ```
 *
 * with `<version>` fixed to the plugin's own, so `runtime` and `compiler` can't drift apart.
 *
 * ### Why it doesn't apply KSP
 *
 * A KSP version is pinned to a Kotlin version — `2.2.10-2.0.2` works with Kotlin 2.2.10 and nothing
 * else. Applying KSP here would put PrevHam's Kotlin version on every consumer, and one on a newer
 * Kotlin would be unable to use PrevHam at all until a matching release came out. So KSP stays the
 * consumer's to declare, at the version matching their Kotlin, and this plugin only checks that it's
 * there. It's also where KSP-based libraries have generally landed: Room, Moshi, Hilt and Koin
 * Annotations all leave the KSP declaration to the consumer.
 *
 * The KSP Gradle plugin is a `compileOnly` dependency of this module for the same reason — it must
 * not reach the consumer's buildscript classpath and force a version there.
 */
class PrevHamPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create(EXTENSION_NAME, PrevHamExtension::class.java)
        extension.mockValues.convention(target.layout.projectDirectory.file(DEFAULT_MOCK_VALUES))
        extension.slotManifest.convention(target.layout.buildDirectory.file(DEFAULT_SLOT_MANIFEST))
        extension.warnOnMissingValues.convention(true)

        target.tasks.register(GENERATE_TASK_NAME, GenerateMockValuesTask::class.java) { task ->
            task.slotManifest.set(extension.slotManifest)
            task.mockValues.set(extension.mockValues)
            task.baseUrl.set(extension.baseUrl)
            task.model.set(extension.model)
            task.language.set(extension.language)
            // Never from the build script: a key belongs where it can be kept out of the repository.
            task.apiKey.set(ApiKey.provider(target))
            task.rejectedApiKeyLocation.set(target.providers.provider { ApiKey.rejectIfCommitted(target) })
        }

        // The `implementation` and `ksp` configurations come from the Kotlin and KSP plugins, so
        // reacting to Kotlin being applied avoids depending on the order of the `plugins {}` block.
        // Both Kotlin plugin ids are covered: an Android consumer applies one, a plain JVM module
        // the other.
        KOTLIN_PLUGIN_IDS.forEach { kotlinPluginId ->
            target.pluginManager.withPlugin(kotlinPluginId) { target.addPrevHamDependencies() }
        }

        // Kotlin is checked first because KSP can't be applied without it - a project missing both
        // should be told about the one it needs first, and a Kotlin Multiplatform project (which
        // satisfies KSP but none of the ids above) should hear that rather than "KSP is missing".
        target.afterEvaluate { project ->
            // In afterEvaluate so a consumer's own `prevham { }` block has run first. KspExtension.arg
            // takes plain strings rather than providers, so the value has to be final when it is set.
            project.pluginManager.withPlugin(KSP_PLUGIN_ID) { KspOptions.set(project, extension) }
            check(KOTLIN_PLUGIN_IDS.any { project.pluginManager.hasPlugin(it) }) { MISSING_KOTLIN_MESSAGE }
            check(project.pluginManager.hasPlugin(KSP_PLUGIN_ID)) { MISSING_KSP_MESSAGE }
        }
    }

    private fun Project.addPrevHamDependencies() {
        // Guarded so the `ksp` configuration is only touched once KSP has created it. The
        // afterEvaluate check above is what reports its absence; this just avoids failing earlier
        // with a less useful error.
        pluginManager.withPlugin(KSP_PLUGIN_ID) {
            dependencies.add("implementation", "$PREVHAM_GROUP:$RUNTIME_ARTIFACT:$PREVHAM_VERSION")
            dependencies.add("ksp", "$PREVHAM_GROUP:$COMPILER_ARTIFACT:$PREVHAM_VERSION")
            // Not debugImplementation: KSP generates Previews for every variant, so the release
            // compilation needs MockK on its classpath too.
            dependencies.add("implementation", "$MOCKK_COORDINATES:$MOCKK_VERSION")
        }
    }

    private companion object {
        const val KSP_PLUGIN_ID = "com.google.devtools.ksp"
        const val EXTENSION_NAME = "prevham"
        const val GENERATE_TASK_NAME = "prevhamGenerateMockValues"
        const val DEFAULT_MOCK_VALUES = "src/main/prevham/mock-values.json"
        const val DEFAULT_SLOT_MANIFEST = "generated/prevham/mock-value-slots.json"

        val KOTLIN_PLUGIN_IDS =
            listOf(
                "org.jetbrains.kotlin.android",
                "org.jetbrains.kotlin.jvm",
            )

        const val PREVHAM_GROUP = "io.github.parkjiminnnn"
        const val RUNTIME_ARTIFACT = "prevham-runtime"
        const val COMPILER_ARTIFACT = "prevham-compiler"
        const val MOCKK_COORDINATES = "io.mockk:mockk"

        val MISSING_KSP_MESSAGE =
            """
            PrevHam needs the KSP plugin, which it deliberately doesn't apply for you: a KSP version
            is tied to a Kotlin version, so declaring it yourself keeps PrevHam from pinning your
            Kotlin version. Add it to your plugins block at the version matching your Kotlin:

                plugins {
                    id("$KSP_PLUGIN_ID") version "<version for your Kotlin>"
                    id("io.github.parkjiminnnn.prevham") version "$PREVHAM_VERSION"
                }
            """.trimIndent()

        val MISSING_KOTLIN_MESSAGE =
            """
            PrevHam's Gradle plugin works on Kotlin Android and Kotlin JVM projects, and this one has
            neither ${KOTLIN_PLUGIN_IDS.joinToString(" nor ")} applied.

            Kotlin Multiplatform isn't supported by the plugin - its source sets use different
            configuration names - so declare PrevHam's dependencies by hand there instead.
            """.trimIndent()
    }
}
