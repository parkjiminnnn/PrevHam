package io.github.parkjiminnnn.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.CodeBlock
import io.github.parkjiminnnn.compiler.codegen.PreviewFileGenerator
import io.github.parkjiminnnn.compiler.mock.MockGeneratorRegistry

class PrevSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    private val mockGenerators = MockGeneratorRegistry()

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

        val arguments = buildMockArguments(function) ?: return
        val fileSpec = PreviewFileGenerator.generate(function, arguments)

        codeGenerator
            .createNewFile(
                dependencies = Dependencies(aggregating = false, function.containingFile!!),
                packageName = fileSpec.packageName,
                fileName = fileSpec.name,
            ).bufferedWriter()
            .use { writer -> fileSpec.writeTo(writer) }
    }

    private fun buildMockArguments(function: KSFunctionDeclaration): Map<String, CodeBlock>? {
        val arguments = LinkedHashMap<String, CodeBlock>()
        for (parameter in function.parameters) {
            val name = parameter.name?.asString() ?: continue
            val type = parameter.type.resolve()
            when {
                mockGenerators.supports(type) -> {
                    arguments[name] = mockGenerators.generate(type)
                }

                parameter.hasDefault -> {
                    Unit
                }

                else -> {
                    logger.warn(
                        "[PrevHam] skipping @Prev on '${function.simpleName.asString()}': " +
                            "no mock generator available for parameter '$name'",
                        function,
                    )
                    return null
                }
            }
        }
        return arguments
    }

    private fun KSFunctionDeclaration.isComposable(): Boolean =
        annotations.any {
            it.annotationType
                .resolve()
                .declaration.qualifiedName
                ?.asString() == COMPOSABLE_ANNOTATION_NAME
        }

    private companion object {
        const val PREV_ANNOTATION_NAME = "io.github.parkjiminnnn.runtime.Prev"
        const val COMPOSABLE_ANNOTATION_NAME = "androidx.compose.runtime.Composable"
    }
}
