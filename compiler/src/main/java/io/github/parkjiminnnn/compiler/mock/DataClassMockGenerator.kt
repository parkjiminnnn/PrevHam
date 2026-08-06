package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.CodeBlock
import io.github.parkjiminnnn.compiler.codegen.buildNamedArgumentsCall

internal class DataClassMockGenerator(
    private val fieldRegistry: MockGeneratorRegistry,
) : MockGenerator {
    override fun supports(type: KSType): Boolean {
        val parameters = type.substitutedConstructorParameters() ?: return false
        return firstUnsupportedParameter(parameters, fieldRegistry) == null
    }

    override fun generate(type: KSType): CodeBlock {
        val declaration = type.declaration as KSClassDeclaration
        val parameters = type.substitutedConstructorParameters().orEmpty()
        val arguments = buildMockArguments(parameters, fieldRegistry)
        return buildNamedArgumentsCall(declaration.toClassName(), arguments)
    }

    // For a generic data class (e.g. Box<T>), a constructor parameter's own declared type
    // (`value: T`) is just the type parameter T, not the actual type argument (e.g. String) used
    // at this call site. asMemberOf() resolves each parameter's type as seen from `type` (e.g.
    // Box<String>), substituting type parameters with their actual arguments; for a non-generic
    // data class this is a no-op and returns the declared types unchanged.
    private fun KSType.substitutedConstructorParameters(): List<MockParameter>? {
        val declaration = declaration as? KSClassDeclaration ?: return null
        if (Modifier.DATA !in declaration.modifiers) return null
        val constructor = declaration.primaryConstructor ?: return null
        val substitutedTypes = constructor.asMemberOf(this).parameterTypes
        if (substitutedTypes.size != constructor.parameters.size) return null
        return constructor.parameters.zip(substitutedTypes).map { (parameter, type) ->
            parameter.toMockParameter(type ?: return null) ?: return null
        }
    }
}
