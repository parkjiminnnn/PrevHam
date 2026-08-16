package io.github.parkjiminnnn.compiler.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock

internal fun buildNamedArgumentsCall(
    calleeName: String,
    arguments: Map<String, CodeBlock>,
): CodeBlock = buildCall(CodeBlock.of("%L", calleeName), arguments)

// Used when the callee is a type rather than a plain name, so KotlinPoet resolves nested types
// (e.g. a sealed subtype declared inside its parent, FestivalUiState.Success) and their imports
// through %T instead of emitting an unqualified simple name that may not resolve.
internal fun buildNamedArgumentsCall(
    callee: ClassName,
    arguments: Map<String, CodeBlock>,
): CodeBlock = buildCall(CodeBlock.of("%T", callee), arguments)

private fun buildCall(
    callee: CodeBlock,
    arguments: Map<String, CodeBlock>,
): CodeBlock {
    val call = CodeBlock.builder().add("%L(", callee)
    if (arguments.isNotEmpty()) {
        call.indent().add("\n")
        arguments.forEach { (name, value) -> call.add("%L = %L,\n", name, value) }
        call.unindent()
    }
    return call.add(")").build()
}
