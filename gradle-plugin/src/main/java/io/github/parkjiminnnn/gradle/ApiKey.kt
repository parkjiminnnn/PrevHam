package io.github.parkjiminnnn.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import java.io.File
import java.util.Properties

/**
 * Where the API key is read from, and where it must not be.
 *
 * Three sources, none of them the build script. In order:
 *
 * 1. `local.properties` - the Android convention, and already in the default `.gitignore`
 * 2. the `PREVHAM_API_KEY` environment variable - the one that touches no file, for CI
 * 3. the `prevham.apiKey` Gradle property, from `~/.gradle/gradle.properties`
 *
 * The third is genuinely useful - it is where credentials for publishing already live - but it is
 * also readable from the *project's* `gradle.properties`, which is conventionally committed. Getting
 * that wrong works perfectly, which is what makes it dangerous: a key in a tracked file leaks
 * silently and keeps working, so there is nothing to notice. [rejectIfCommitted] looks for exactly
 * that and refuses rather than warns, because by the time anyone reads a warning the commit has
 * usually happened.
 */
internal object ApiKey {
    fun provider(project: Project): Provider<String> =
        project.providers
            .provider { localProperties(project) }
            .orElse(project.providers.environmentVariable(ENVIRONMENT))
            .orElse(project.providers.gradleProperty(GRADLE_PROPERTY))

    /** The message to fail with when the key sits in a file that is normally committed, or null. */
    fun rejectIfCommitted(project: Project): String? {
        val offender =
            listOf(project.projectDir, project.rootDir)
                .distinct()
                .map { File(it, "gradle.properties") }
                .firstOrNull { it.isFile && it.declaresKey() }
                ?: return null

        return """
            [PrevHam] '$GRADLE_PROPERTY' is set in ${offender.absolutePath}.

            That file is normally committed, so the key would be published with the project. Move it
            to one of:

              local.properties           $GRADLE_PROPERTY=…   (gitignored by default)
              ~/.gradle/gradle.properties $GRADLE_PROPERTY=…   (outside the project)
              $ENVIRONMENT=…   (an environment variable, nothing on disk)

            Treat the key as compromised if this file has already been pushed.
            """.trimIndent()
    }

    // Matches the key as a declaration rather than anywhere in the text, so a comment explaining
    // where not to put it doesn't trip this.
    private fun File.declaresKey(): Boolean =
        readLines().any { line ->
            line.trimStart().substringBefore('=').trim() == GRADLE_PROPERTY
        }

    private fun localProperties(project: Project): String? {
        val file = File(project.rootDir, "local.properties")
        if (!file.isFile) return null
        return runCatching {
            Properties().apply { file.inputStream().use(::load) }.getProperty(GRADLE_PROPERTY)
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    const val GRADLE_PROPERTY = "prevham.apiKey"
    const val ENVIRONMENT = "PREVHAM_API_KEY"
}
