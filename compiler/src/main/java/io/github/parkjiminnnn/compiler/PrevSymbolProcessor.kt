package io.github.parkjiminnnn.compiler

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.FunctionKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Visibility
import com.squareup.kotlinpoet.CodeBlock
import io.github.parkjiminnnn.compiler.codegen.PreviewFileGenerator
import io.github.parkjiminnnn.compiler.mock.MissingValueReport
import io.github.parkjiminnnn.compiler.mock.MockContext
import io.github.parkjiminnnn.compiler.mock.MockGeneratorRegistry
import io.github.parkjiminnnn.compiler.mock.MockValues
import io.github.parkjiminnnn.compiler.mock.SlotManifest
import io.github.parkjiminnnn.compiler.mock.SlotRecorder
import io.github.parkjiminnnn.compiler.mock.buildMockArguments
import io.github.parkjiminnnn.compiler.mock.firstUnsupportedParameter
import io.github.parkjiminnnn.compiler.mock.toMockParameter
import java.io.File

internal class PrevSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val mockValues: MockValues = MockValues.EMPTY,
    private val slotManifest: File? = null,
    private val warnOnMissingValues: Boolean = false,
) : SymbolProcessor {
    private val mockGenerators = MockGeneratorRegistry.default()

    // One recorder for the whole compilation, not one per function: KSP calls process() once per
    // round, and the manifest describes everything the compilation met.
    private val slots = SlotRecorder()

    // Same reason, and the counts are only worth anything once they are complete: a per-round
    // summary would split one project across several lines that each look like the whole picture.
    private val tally = RoundTally()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        resolver
            .getSymbolsWithAnnotation(PREV_ANNOTATION_NAME)
            .filterIsInstance<KSFunctionDeclaration>()
            .forEach(::processFunction)

        return emptyList()
    }

    /**
     * Reports on the round as a whole, once every round has run.
     *
     * In finish() rather than process() because a slot found in a later round still counts: a
     * manifest rewritten per round would be incomplete until the last one, and a warning raised per
     * round would name slots that a later round goes on to decide.
     */
    override fun finish() {
        writeSlotManifest()
        warnAboutUndecidedSlots()
        reportRound()
    }

    /**
     * KSP calls this instead of finish() when the round reported an error.
     *
     * Only the summary is repeated here. The manifest and the missing-value warning describe a
     * compilation that produced Previews, and this one did not - but the counts are exactly what
     * says whether the error is one bad @Prev among forty working ones or the shape of the whole
     * project.
     */
    override fun onError() {
        reportRound()
    }

    private fun writeSlotManifest() {
        val file = slotManifest ?: return
        runCatching { SlotManifest.write(file, slots.recorded()) }
            .onFailure { failure ->
                // A manifest is a convenience for the generation task; failing to write one must
                // not fail a compilation that has otherwise produced every Preview it was asked for.
                logger.warn("[PrevHam] could not write the slot manifest to '$file': ${failure.message}")
            }
    }

    /**
     * Says which slots the value file has nothing for.
     *
     * A warning rather than an error, and nothing is fetched to fix it. Falling back to the default
     * is the correct behaviour for an undecided slot - the only thing missing is anyone being told,
     * and telling them is the whole of this. A build that went and asked for the values instead
     * would need credentials on every machine that compiles and would rewrite a committed file
     * behind whoever ran it.
     */
    private fun warnAboutUndecidedSlots() {
        if (!warnOnMissingValues) return
        val undecided =
            slots
                .recorded()
                .map { it.path }
                .distinct()
                .filter { mockValues[it] == null }
        MissingValueReport.message(undecided)?.let(logger::warn)
    }

    /**
     * Says what @Prev produced, and which ones it did not.
     *
     * Warned when something was skipped or failed and logged at info otherwise, because KSP has no
     * level between the two: a build missing a third of its Previews is worth seeing by default, and
     * a build that produced every one of them has nothing wrong with it to warn about.
     */
    private fun reportRound() {
        val message = RoundReport.message(tally) ?: return
        if (tally.hasProblems) logger.warn(message) else logger.info(message)
    }

    private fun processFunction(function: KSFunctionDeclaration) {
        tally.found()
        if (!function.isComposable()) {
            logger.error(
                "[PrevHam] @Prev can only be applied to a @Composable function, " +
                    "but '${function.simpleName.asString()}' is not annotated with @Composable",
                function,
            )
            tally.failed()
            return
        }

        val uncallable = function.uncallableFromGeneratedFileReason()
        if (uncallable != null) {
            logger.error(
                "[PrevHam] cannot generate a Preview for '${function.simpleName.asString()}': $uncallable " +
                    "Alternatively, drop @Prev and write a @Preview function by hand in the same file.",
                function,
            )
            tally.failed()
            return
        }

        val arguments = buildMockArguments(function) ?: return
        val options = function.previewOptions()
        val fileSpec = PreviewFileGenerator.generate(function, arguments, options)

        codeGenerator
            .createNewFile(
                dependencies = Dependencies(aggregating = false, function.containingFile!!),
                packageName = fileSpec.packageName,
                fileName = fileSpec.name,
            ).bufferedWriter()
            .use { writer -> fileSpec.writeTo(writer) }
        // Counted after the write, so the number says what is on disk rather than what was attempted.
        tally.generated()
    }

    private fun buildMockArguments(function: KSFunctionDeclaration): Map<String, CodeBlock>? {
        val parameters = function.parameters.mapNotNull { it.toMockParameter(owner = function.qualifiedName?.asString()) }
        val context = MockContext.root(mockGenerators, mockValues, slots)
        val unsupported = firstUnsupportedParameter(parameters, context)
        if (unsupported != null) {
            // Recorded rather than warned about here. One warning per skip, scattered through a
            // build's output, is easy to miss and impossible to count - RoundReport gathers them.
            tally.skipped(
                name = function.simpleName.asString(),
                reason = "no mock generator available for parameter '${unsupported.name}'",
            )
            return null
        }
        return buildMockArguments(parameters, context)
    }

    /**
     * Why the generated Preview file wouldn't be able to call this function, or null if it can.
     *
     * PrevHam always writes the Preview into a *new* file - KSP's `CodeGenerator` has no way to add
     * to an existing one - so anything the composable's declaration makes unreachable from a
     * separate top-level file makes the whole Preview impossible.
     *
     * This is reported as an error rather than a skipped Preview, for the same reason `@Prev` on a
     * non-`@Composable` function is: no future version of PrevHam can make it work, so silently
     * generating nothing would leave `@Prev` looking applied while doing nothing. It is also not a
     * new build failure - without the check, the file is generated and then fails to compile with
     * an error that says nothing about PrevHam.
     */
    private fun KSFunctionDeclaration.uncallableFromGeneratedFileReason(): String? {
        if (functionKind != FunctionKind.TOP_LEVEL) {
            val owner =
                parentDeclaration
                    ?.simpleName
                    ?.asString()
                    ?.let { " '$it'" }
                    .orEmpty()
            return "it is declared inside$owner, and the generated file has no way to reach it - " +
                "a member needs its declaring type, and a class member needs an instance of it. " +
                "Move the composable to the top level to have a Preview generated."
        }
        // A top-level `private` is scoped to the literal source file, not the module, so no other
        // file can reach it - `internal` is the narrowest visibility that still works.
        if (getVisibility() == Visibility.PRIVATE) {
            return "it is private, and a private top-level function is visible only inside its own " +
                "file. Widen it to internal or public to have a Preview generated."
        }
        return null
    }

    private fun KSFunctionDeclaration.isComposable(): Boolean =
        annotations.any {
            it.annotationType
                .resolve()
                .declaration.qualifiedName
                ?.asString() == COMPOSABLE_ANNOTATION_NAME
        }

    private companion object {
        const val COMPOSABLE_ANNOTATION_NAME = "androidx.compose.runtime.Composable"
    }
}
