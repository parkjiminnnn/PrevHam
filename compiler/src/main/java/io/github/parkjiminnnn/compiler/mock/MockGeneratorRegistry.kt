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

        // Builds a chain of registries indexed by depth: a generator that recurses into another
        // type at depth N does so through a registry built for depth N + 1, and the generators
        // that need one are dropped once MAX_DEPTH is reached. This bounds nested/recursive mock
        // generation so a self-referential or deeply nested type is treated as unsupported instead
        // of causing a StackOverflowError.
        private fun build(depth: Int): MockGeneratorRegistry {
            val nested = if (depth >= MAX_DEPTH) null else build(depth + 1)
            val generators =
                buildList {
                    add(PrimitiveMockGenerator())
                    add(StringMockGenerator())
                    add(EnumMockGenerator())
                    // Ahead of InterfaceMockGenerator, which would otherwise claim sealed
                    // interfaces and sealed classes as ordinary mockable types.
                    add(SealedTypeMockGenerator(nested))
                    if (nested != null) {
                        add(DataClassMockGenerator(nested))
                        add(CollectionMockGenerator(nested))
                        add(FunctionTypeMockGenerator(nested))
                    }
                    // Stays available at every depth - it needs a nested registry only to stub the
                    // mock's members, and falls back to a bare relaxed mock without one.
                    add(InterfaceMockGenerator(nested))
                    add(NullableFallbackMockGenerator())
                }
            return MockGeneratorRegistry(generators)
        }
    }
}
