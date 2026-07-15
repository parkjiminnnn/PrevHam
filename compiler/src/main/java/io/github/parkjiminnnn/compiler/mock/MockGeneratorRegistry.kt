package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

internal class MockGeneratorRegistry(
    private val generators: List<MockGenerator>,
) {
    fun supports(type: KSType): Boolean = generators.any { it.supports(type) }

    fun generate(type: KSType): CodeBlock = generators.first { it.supports(type) }.generate(type)

    companion object {
        private const val MAX_DEPTH = 3

        fun default(): MockGeneratorRegistry = build(depth = 0)

        // Builds a chain of registries indexed by depth: DataClass/Collection generators at
        // depth N recurse into a registry built for depth N + 1, and are dropped once MAX_DEPTH
        // is reached. This bounds nested/recursive mock generation so a self-referential or
        // deeply nested type is treated as unsupported instead of causing a StackOverflowError.
        private fun build(depth: Int): MockGeneratorRegistry {
            val leafGenerators = listOf(PrimitiveMockGenerator(), StringMockGenerator(), EnumMockGenerator())
            if (depth >= MAX_DEPTH) {
                return MockGeneratorRegistry(leafGenerators + NullableFallbackMockGenerator())
            }

            val nested = build(depth + 1)
            val recursiveGenerators = leafGenerators + DataClassMockGenerator(nested) + CollectionMockGenerator(nested)
            return MockGeneratorRegistry(recursiveGenerators + NullableFallbackMockGenerator())
        }
    }
}
