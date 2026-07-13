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
        val parameters = type.dataClassConstructorParameters() ?: return false
        return firstUnsupportedParameter(parameters, fieldRegistry) == null
    }

    override fun generate(type: KSType): CodeBlock {
        val declaration = type.declaration as KSClassDeclaration
        val parameters = type.dataClassConstructorParameters().orEmpty()
        val arguments = buildMockArguments(parameters, fieldRegistry)
        return buildNamedArgumentsCall(declaration.simpleName.asString(), arguments)
    }

    private fun KSType.dataClassConstructorParameters() =
        (declaration as? KSClassDeclaration)
            ?.takeIf { Modifier.DATA in it.modifiers }
            ?.primaryConstructor
            ?.parameters
}
