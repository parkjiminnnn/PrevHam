package io.github.parkjiminnnn.compiler.codegen

import com.squareup.kotlinpoet.CodeBlock

internal fun buildNamedArgumentsCall(
    calleeName: String,
    arguments: Map<String, CodeBlock>,
): CodeBlock {
    val call = CodeBlock.builder().add("%L(", calleeName)
    if (arguments.isNotEmpty()) {
        call.indent().add("\n")
        arguments.forEach { (name, value) -> call.add("%L = %L,\n", name, value) }
        call.unindent()
    }
    return call.add(")").build()
}
