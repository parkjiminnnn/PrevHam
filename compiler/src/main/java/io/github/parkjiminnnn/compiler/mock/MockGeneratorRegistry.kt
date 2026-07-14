package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

internal class MockGeneratorRegistry(
    private val generators: List<MockGenerator>,
) {
    fun supports(type: KSType): Boolean = generators.any { it.supports(type) }

    fun generate(type: KSType): CodeBlock = generators.first { it.supports(type) }.generate(type)

    companion object {
        // scalarRegistry excludes DataClassMockGenerator/CollectionMockGenerator so nested data
        // classes and collection-typed data class fields stay unsupported for now.
        // compositeRegistry backs CollectionMockGenerator's elements, so List<Int>/List<Status>/
        // List<User> all work, without supporting nested collections (List<List<T>>).
        // NullableFallbackMockGenerator is always last, so a real mock is preferred whenever one is available.
        fun default(): MockGeneratorRegistry {
            val scalarGenerators = listOf(PrimitiveMockGenerator(), StringMockGenerator(), EnumMockGenerator())
            val scalarRegistry = MockGeneratorRegistry(scalarGenerators + NullableFallbackMockGenerator())
            val compositeGenerators = scalarGenerators + DataClassMockGenerator(scalarRegistry)
            val compositeRegistry = MockGeneratorRegistry(compositeGenerators + NullableFallbackMockGenerator())
            val fullGenerators = compositeGenerators + CollectionMockGenerator(compositeRegistry) + NullableFallbackMockGenerator()
            return MockGeneratorRegistry(fullGenerators)
        }
    }
}
