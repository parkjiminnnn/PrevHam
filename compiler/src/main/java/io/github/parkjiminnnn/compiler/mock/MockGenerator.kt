package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

internal interface MockGenerator {
    fun supports(type: KSType): Boolean

    fun generate(type: KSType): CodeBlock
}
