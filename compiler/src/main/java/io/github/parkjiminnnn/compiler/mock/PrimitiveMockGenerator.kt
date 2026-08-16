package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

internal class PrimitiveMockGenerator : MockGenerator {
    override fun supports(
        type: KSType,
        context: MockContext,
    ): Boolean = type.qualifiedName() in MOCK_LITERALS

    override fun generate(
        type: KSType,
        context: MockContext,
    ): CodeBlock = CodeBlock.of(MOCK_LITERALS.getValue(type.qualifiedName()!!))

    private fun KSType.qualifiedName(): String? = declaration.qualifiedName?.asString()

    private companion object {
        val MOCK_LITERALS =
            mapOf(
                "kotlin.Int" to "1",
                "kotlin.Long" to "1L",
                "kotlin.Short" to "1",
                "kotlin.Byte" to "1",
                "kotlin.Double" to "1.0",
                "kotlin.Float" to "1f",
                "kotlin.Boolean" to "true",
                "kotlin.Char" to "'a'",
            )
    }
}
