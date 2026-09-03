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
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        PrevSymbolProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            mockValues = environment.mockValues(),
            slotManifest = environment.options[SLOT_MANIFEST_OPTION]?.let(::File),
        )

    /**
     * The configured mock values, or none.
     *
     * The path arrives as a KSP option:
     *
     * ```kotlin
     * ksp { arg("prevham.mockValues", "$projectDir/src/main/prevham/mock-values.json") }
     * ```
     *
     * A configured file that can't be read is a warning, never an error - the Previews it would
     * have improved still generate with their default values. Silence would be worse: a typo in the
     * path would look like the values simply having no effect.
     */
    private fun SymbolProcessorEnvironment.mockValues(): MockValues {
        val path = options[MOCK_VALUES_OPTION] ?: return MockValues.EMPTY
        return MockValues
            .from(File(path))
            .onFailure { failure ->
                logger.warn("[PrevHam] could not read mock values from '$path': ${failure.message}")
            }.getOrDefault(MockValues.EMPTY)
    }

    private companion object {
        const val MOCK_VALUES_OPTION = "prevham.mockValues"

        // Opt-in: without it nothing is written, so a build that has no use for a manifest is not
        // surprised by a new file appearing.
        const val SLOT_MANIFEST_OPTION = "prevham.slotManifest"
    }
}
