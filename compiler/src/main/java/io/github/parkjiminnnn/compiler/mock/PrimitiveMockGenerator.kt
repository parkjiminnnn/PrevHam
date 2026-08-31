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
    ): CodeBlock {
        val qualifiedName = type.qualifiedName()!!
        if (qualifiedName in NUMERIC_LITERAL_FORMATTERS) {
            context.recordSlot()
            context.slotValue()?.let { configured ->
                literalFor(qualifiedName, configured)?.let { return CodeBlock.of(it) }
            }
        }
        return CodeBlock.of(MOCK_LITERALS.getValue(qualifiedName))
    }

    /**
     * The configured value as a literal of this type, or null when it isn't one.
     *
     * Parsed rather than trusted. A value file is hand-editable and a generated one is a model's
     * guess, so `"about 400"` or `"1,200"` has to end up as the default rather than as source that
     * doesn't compile. Non-finite doubles are rejected for the same reason: `toDoubleOrNull` accepts
     * `NaN` and `1e400`, neither of which is a Kotlin literal.
     *
     * Boolean and Char take no configured value. `true` is already as good an answer as `false`, and
     * a single character carries no meaning worth generating.
     */
    private fun literalFor(
        qualifiedName: String,
        value: String,
    ): String? = NUMERIC_LITERAL_FORMATTERS[qualifiedName]?.invoke(value.trim())

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

        val NUMERIC_LITERAL_FORMATTERS: Map<String, (String) -> String?> =
            mapOf(
                "kotlin.Int" to { it.toIntOrNull()?.toString() },
                "kotlin.Long" to { it.toLongOrNull()?.let { parsed -> "${parsed}L" } },
                "kotlin.Short" to { it.toShortOrNull()?.toString() },
                "kotlin.Byte" to { it.toByteOrNull()?.toString() },
                "kotlin.Double" to { it.toDoubleOrNull()?.takeIf(Double::isFinite)?.toString() },
                "kotlin.Float" to { it.toFloatOrNull()?.takeIf(Float::isFinite)?.let { parsed -> "${parsed}f" } },
            )
    }
}
