package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

internal interface MockGenerator {
    /** Whether this generator can mock [type]. Must not have side effects. */
    fun supports(
        type: KSType,
        context: MockContext,
    ): Boolean

    /** The mock for [type]. Only called after [supports] returned true for the same arguments. */
    fun generate(
        type: KSType,
        context: MockContext,
    ): CodeBlock
}
