package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

internal class FunctionTypeMockGenerator(
    private val returnTypeRegistry: MockGeneratorRegistry,
) : MockGenerator {
    override fun supports(type: KSType): Boolean {
        val returnType = type.functionReturnType() ?: return false
        return returnType.isUnit() || returnTypeRegistry.supports(returnType)
    }

    // A lambda literal doesn't need to reference (or even name) its parameters, so the same
    // `{}`/`{ <value> }` body is valid regardless of the function type's parameter list.
    override fun generate(type: KSType): CodeBlock {
        val returnType = type.functionReturnType()!!
        return if (returnType.isUnit()) CodeBlock.of("{ }") else CodeBlock.of("{ %L }", returnTypeRegistry.generate(returnType))
    }

    // The last type argument of a kotlin.FunctionN<P1, ..., PN, R> is always its return type.
    private fun KSType.functionReturnType(): KSType? {
        if (declaration.qualifiedName?.asString()?.startsWith(KOTLIN_FUNCTION_TYPE_PREFIX) != true) return null
        return arguments.lastOrNull()?.type?.resolve()
    }

    private fun KSType.isUnit(): Boolean = declaration.qualifiedName?.asString() == UNIT_QUALIFIED_NAME

    private companion object {
        const val UNIT_QUALIFIED_NAME = "kotlin.Unit"
    }
}
