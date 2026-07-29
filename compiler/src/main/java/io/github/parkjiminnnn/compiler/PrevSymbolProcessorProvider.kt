package io.github.parkjiminnnn.compiler

import com.google.auto.service.AutoService
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

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
        )
}
