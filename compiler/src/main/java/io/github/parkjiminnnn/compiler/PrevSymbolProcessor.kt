package io.github.parkjiminnnn.compiler

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated

class PrevSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(PREV_ANNOTATION_NAME).toList()
        logger.info("[PrevHam] found ${symbols.size} symbol(s) annotated with @Prev")
        return emptyList()
    }

    private companion object {
        const val PREV_ANNOTATION_NAME = "io.github.parkjiminnnn.runtime.Prev"
    }
}
