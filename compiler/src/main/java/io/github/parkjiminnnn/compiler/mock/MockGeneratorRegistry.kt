package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

internal class MockGeneratorRegistry(
    private val generators: List<MockGenerator> = listOf(PrimitiveMockGenerator(), StringMockGenerator()),
) {
    fun supports(type: KSType): Boolean = generators.any { it.supports(type) }

    fun generate(type: KSType): CodeBlock = generators.first { it.supports(type) }.generate(type)
}
