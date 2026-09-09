package io.github.parkjiminnnn.compiler

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import io.github.parkjiminnnn.compiler.mock.MockValues
import java.io.File

/**
 * Entry point KSP uses to create PrevHam's symbol processor.
 *
 * This is the only public type in the `compiler` artifact, and it is public solely because KSP
 * discovers it through the service loader — `@AutoService` registers it in `META-INF/services`, and
 * KSP instantiates it reflectively. Consumers never construct it themselves; they wire the artifact
 * in as a KSP processor instead:
 *
 * ```kotlin
 * dependencies {
 *     ksp("io.github.parkjiminnnn:prevham-compiler:<version>")
 * }
 * ```
 *
 * Everything else in the module is `internal` and free to change without breaking consumers.
 */
@AutoService(SymbolProcessorProvider::class)
class PrevSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val values = environment.mockValues()
        return PrevSymbolProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            mockValues = values ?: MockValues.EMPTY,
            slotManifest = environment.options[SLOT_MANIFEST_OPTION]?.let(::File),
            warnOnMissingValues = values != null && environment.options[WARN_OPTION] != "false",
        )
    }

    /**
     * The values to use, or null when there is no value file to hold this build to.
     *
     * The path arrives as a KSP option:
     *
     * ```kotlin
     * ksp { arg("prevham.mockValues", "$projectDir/src/main/prevham/mock-values.json") }
     * ```
     *
     * Null and empty are deliberately different things, and only the difference decides whether
     * missing slots are worth reporting:
     *
     * - **No option, or a file that isn't there yet.** The feature isn't in use. A file is absent
     *   for everyone who has applied the plugin and not yet run the generation task, which is the
     *   ordinary state and not a problem to report - so this stays silent rather than greeting every
     *   new consumer with a warning about a file they never asked for.
     * - **A file that is there but can't be read.** Something is wrong and saying so is the point:
     *   silence would make a malformed file look like the values simply having no effect. Reported
     *   as a warning and never an error, because the Previews it would have improved still generate.
     */
    private fun SymbolProcessorEnvironment.mockValues(): MockValues? {
        val path = options[MOCK_VALUES_OPTION] ?: return null
        val file = File(path)
        if (!file.isFile) return null
        return MockValues
            .from(file)
            .onFailure { failure ->
                logger.warn("[PrevHam] could not read mock values from '$path': ${failure.message}")
            }.getOrNull()
    }

    private companion object {
        const val MOCK_VALUES_OPTION = "prevham.mockValues"

        // Opt-in: without it nothing is written, so a build that has no use for a manifest is not
        // surprised by a new file appearing.
        const val SLOT_MANIFEST_OPTION = "prevham.slotManifest"

        // Opt-out, unlike the two above. The people it exists for are the ones already using a value
        // file, and they are exactly the ones who would never think to switch it on.
        const val WARN_OPTION = "prevham.warnOnMissingValues"
    }
}
