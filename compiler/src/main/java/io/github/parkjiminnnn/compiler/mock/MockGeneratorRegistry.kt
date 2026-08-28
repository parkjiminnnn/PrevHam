package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

internal class MockGeneratorRegistry(
    private val generators: List<MockGenerator>,
) {
    fun supports(
        type: KSType,
        context: MockContext,
    ): Boolean = generators.any { it.supports(type, context) }

    fun generate(
        type: KSType,
        context: MockContext,
    ): CodeBlock = generators.first { it.supports(type, context) }.generate(type, context)

    companion object {
        // Resolved by list order, via any {} / first {} short-circuiting: the first generator whose
        // supports() returns true wins. Recursion is bounded by MockContext, not by this list, so
        // every generator is available at every step - a deeply nested but finite model reaches the
        // same generators as a flat one.
        fun default(): MockGeneratorRegistry =
            MockGeneratorRegistry(
                listOf(
                    PrimitiveMockGenerator(),
                    StringMockGenerator(),
                    EnumMockGenerator(),
                    // Alongside EnumMockGenerator: both reference a value that already exists rather
                    // than building one. Ahead of DataClassMockGenerator, which claims a `data
                    // object` on its DATA modifier and emits an uncallable constructor.
                    ObjectMockGenerator(),
                    // Ahead of InterfaceMockGenerator, which would otherwise claim sealed
                    // interfaces and sealed classes as ordinary mockable types.
                    SealedTypeMockGenerator(),
                    DataClassMockGenerator(),
                    CollectionMockGenerator(),
                    FunctionTypeMockGenerator(),
                    InterfaceMockGenerator(),
                    // Last: a nullable String? should still get a real "mock" from
                    // StringMockGenerator, with null only as the fallback when nothing else serves.
                    NullableFallbackMockGenerator(),
                ),
            )
    }
}
