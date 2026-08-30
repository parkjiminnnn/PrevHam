package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

internal class StringMockGenerator : MockGenerator {
    override fun supports(
        type: KSType,
        context: MockContext,
    ): Boolean = type.declaration.qualifiedName?.asString() == STRING_QUALIFIED_NAME

    // A configured value wins over the default. KotlinPoet's %S escapes it, so quotes, newlines and
    // backslashes in a hand-edited file can't produce a string literal that doesn't compile.
    override fun generate(
        type: KSType,
        context: MockContext,
    ): CodeBlock = CodeBlock.of("%S", context.slotValue() ?: "mock")

    private companion object {
        const val STRING_QUALIFIED_NAME = "kotlin.String"
    }
}
