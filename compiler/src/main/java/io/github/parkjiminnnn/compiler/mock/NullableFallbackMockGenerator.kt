package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

internal class NullableFallbackMockGenerator : MockGenerator {
    override fun supports(
        type: KSType,
        context: MockContext,
    ): Boolean = type.isMarkedNullable

    override fun generate(
        type: KSType,
        context: MockContext,
    ): CodeBlock = CodeBlock.of("null")
}
