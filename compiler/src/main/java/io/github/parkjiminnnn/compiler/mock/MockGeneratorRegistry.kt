package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

internal class MockGeneratorRegistry(
    private val generators: List<MockGenerator>,
) {
    fun supports(type: KSType): Boolean = generators.any { it.supports(type) }

    fun generate(type: KSType): CodeBlock = generators.first { it.supports(type) }.generate(type)

    companion object {
        // scalarRegistry excludes DataClassMockGenerator so nested data classes stay unsupported for now.
        // NullableFallbackMockGenerator is always last, so a real mock is preferred whenever one is available.
        fun default(): MockGeneratorRegistry {
            val scalarGenerators = listOf(PrimitiveMockGenerator(), StringMockGenerator())
            val scalarRegistry = MockGeneratorRegistry(scalarGenerators + NullableFallbackMockGenerator())
            val fullGenerators = scalarGenerators + DataClassMockGenerator(scalarRegistry) + NullableFallbackMockGenerator()
            return MockGeneratorRegistry(fullGenerators)
        }
    }
}
