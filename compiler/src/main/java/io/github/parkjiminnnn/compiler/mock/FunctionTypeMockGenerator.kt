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

    // Kotlin infers a 0- or 1-parameter lambda without a declared parameter list (the single
    // parameter is available as the implicit `it` even when unused), but requires 2+ parameter
    // function types to at least name every parameter, even if unused - otherwise the lambda is
    // inferred as Function0 and fails to satisfy the actual function type. See issue #37.
    override fun generate(type: KSType): CodeBlock {
        val returnType = type.functionReturnType()!!
        val parameterNames = type.lambdaParameterNames()
        return if (returnType.isUnit()) {
            CodeBlock.of("{ %L}", parameterNames)
        } else {
            CodeBlock.of("{ %L%L }", parameterNames, returnTypeRegistry.generate(returnType))
        }
    }

    // The last type argument of a kotlin.FunctionN<P1, ..., PN, R> is always its return type.
    private fun KSType.functionReturnType(): KSType? {
        if (declaration.qualifiedName?.asString()?.startsWith(KOTLIN_FUNCTION_TYPE_PREFIX) != true) return null
        return arguments.lastOrNull()?.type?.resolve()
    }

    // "_, _ -> " for 2+ parameters, or "" for 0/1 parameters where Kotlin's implicit inference
    // already applies.
    private fun KSType.lambdaParameterNames(): String {
        val parameterCount = arguments.size - 1
        if (parameterCount < 2) return ""
        return List(parameterCount) { "_" }.joinToString(", ", postfix = " -> ")
    }

    private fun KSType.isUnit(): Boolean = declaration.qualifiedName?.asString() == KOTLIN_UNIT_QUALIFIED_NAME
}
