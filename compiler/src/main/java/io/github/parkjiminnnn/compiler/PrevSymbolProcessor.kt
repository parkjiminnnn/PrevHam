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
import io.github.parkjiminnnn.compiler.mock.MockContext
import io.github.parkjiminnnn.compiler.mock.MockGeneratorRegistry
import io.github.parkjiminnnn.compiler.mock.buildMockArguments
import io.github.parkjiminnnn.compiler.mock.firstUnsupportedParameter
import io.github.parkjiminnnn.compiler.mock.toMockParameter

internal class PrevSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    private val mockGenerators = MockGeneratorRegistry.default()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(PREV_ANNOTATION_NAME).toList()
        logger.info("[PrevHam] found ${symbols.size} symbol(s) annotated with @Prev")

        symbols.filterIsInstance<KSFunctionDeclaration>().forEach(::processFunction)

        return emptyList()
    }

    private fun processFunction(function: KSFunctionDeclaration) {
        if (!function.isComposable()) {
            logger.error(
                "[PrevHam] @Prev can only be applied to a @Composable function, " +
                    "but '${function.simpleName.asString()}' is not annotated with @Composable",
                function,
            )
            return
        }

        val uncallable = function.uncallableFromGeneratedFileReason()
        if (uncallable != null) {
            logger.error(
                "[PrevHam] cannot generate a Preview for '${function.simpleName.asString()}': $uncallable " +
                    "Alternatively, drop @Prev and write a @Preview function by hand in the same file.",
                function,
            )
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
    }

    private fun buildMockArguments(function: KSFunctionDeclaration): Map<String, CodeBlock>? {
        val parameters = function.parameters.mapNotNull { it.toMockParameter() }
        val context = MockContext.root(mockGenerators)
        val unsupported = firstUnsupportedParameter(parameters, context)
        if (unsupported != null) {
            logger.warn(
                "[PrevHam] skipping @Prev on '${function.simpleName.asString()}': " +
                    "no mock generator available for parameter '${unsupported.name}'",
                function,
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
