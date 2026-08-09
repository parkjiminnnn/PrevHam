package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

internal class StringMockGenerator : MockGenerator {
    override fun supports(
        type: KSType,
        context: MockContext,
    ): Boolean = type.declaration.qualifiedName?.asString() == STRING_QUALIFIED_NAME

    override fun generate(
        type: KSType,
        context: MockContext,
    ): CodeBlock = CodeBlock.of("%S", "mock")

    private companion object {
        const val STRING_QUALIFIED_NAME = "kotlin.String"
    }
}
