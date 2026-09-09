package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSType

internal const val KOTLIN_STRING_QUALIFIED_NAME = "kotlin.String"

/**
 * Whether a configured mock value can reach this type.
 *
 * A value file holds strings, so a slot is only useful where a string can become source: a `String`
 * literal, or a number parsed out of one. `Boolean` and `Char` are deliberately absent - `true` is no
 * better an answer than `false`, and one character carries nothing worth configuring - and so is
 * everything whose value is not a literal at all. Turning `"2026-05-20"` into `LocalDate.of(2026, 5,
 * 20)` needs a design per type, and that is the same open question for a constructed value as for a
 * mocked one.
 *
 * Derived from [PrimitiveMockGenerator.NUMERIC_LITERAL_FORMATTERS] rather than restated, so what a
 * member of a mock may be stubbed with cannot drift from what a constructed value may be built with.
 */
internal fun KSType.takesConfiguredValue(): Boolean {
    val name = resolveTypeAliases().declaration.qualifiedName?.asString() ?: return false
    return name == KOTLIN_STRING_QUALIFIED_NAME || name in PrimitiveMockGenerator.NUMERIC_LITERAL_FORMATTERS
}
