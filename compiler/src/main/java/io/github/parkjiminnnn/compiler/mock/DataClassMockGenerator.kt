package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.CodeBlock
import io.github.parkjiminnnn.compiler.codegen.buildNamedArgumentsCall

internal class DataClassMockGenerator : MockGenerator {
    override fun supports(
        type: KSType,
        context: MockContext,
    ): Boolean {
        val parameters = type.substitutedConstructorParameters() ?: return false
        return firstUnsupportedParameter(parameters, context) == null
    }

    override fun generate(
        type: KSType,
        context: MockContext,
    ): CodeBlock {
        val declaration = type.declaration as KSClassDeclaration
        val parameters = type.substitutedConstructorParameters().orEmpty()
        val arguments = buildMockArguments(parameters, context)
        return buildNamedArgumentsCall(declaration.toClassName(), arguments)
    }

    // For a generic data class (e.g. Box<T>), a constructor parameter's own declared type
    // (`value: T`) is just the type parameter T, not the actual type argument (e.g. String) used
    // at this call site. asMemberOf() resolves each parameter's type as seen from `type` (e.g.
    // Box<String>), substituting type parameters with their actual arguments; for a non-generic
    // data class this is a no-op and returns the declared types unchanged.
    private fun KSType.substitutedConstructorParameters(): List<MockParameter>? {
        val declaration = declaration as? KSClassDeclaration ?: return null
        // A `data object` carries Modifier.DATA as well, and its synthesised zero-parameter
        // constructor makes the check below vacuously true - emitting `Loading()`, which does not
        // compile. ObjectMockGenerator is registered first so this is unreachable, but the kind is
        // checked here too: reordering the registry should not be able to bring that back.
        if (declaration.classKind != ClassKind.CLASS) return null
        if (Modifier.DATA !in declaration.modifiers) return null
        val constructor = declaration.primaryConstructor ?: return null
        // asMemberOf() rejects a nullable containing type outright ("Item? is not a sub type of
        // the class/interface that contains <init>"), which would fail the whole KSP round rather
        // than this one type. A nullable data class should still get a real instance where one can
        // be built - null is NullableFallbackMockGenerator's last resort, not the first answer -
        // so ask about the non-null form.
        val substitutedTypes = constructor.asMemberOf(makeNotNullable()).parameterTypes
        if (substitutedTypes.size != constructor.parameters.size) return null
        val owner = declaration.qualifiedName?.asString()
        return constructor.parameters.zip(substitutedTypes).map { (parameter, type) ->
            parameter.toMockParameter(type ?: return null, owner) ?: return null
        }
    }
}
