package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName

/**
 * Mocks interfaces and non-data classes with MockK, stubbing every member it can build a real value
 * for.
 *
 * `mockk<T>(relaxed = true)` alone is not enough for a type with a generic member. Relaxed mode
 * answers an unstubbed call by inventing a value for its return type, but it only sees the *erased*
 * type - and `StateFlow<T>.value` erases to `Object`. So reading `viewModel.uiState.value` hands
 * back a bare `java.lang.Object`, and the caller's checkcast to the declared type throws:
 *
 * ```
 * java.lang.ClassCastException: class java.lang.Object cannot be cast to class FestivalUiState
 * ```
 *
 * That was issue #59, reproduced by `GeneratedMockValueTest` in `sample`. Stubbing a member takes it
 * off that path entirely: MockK returns the value it was given and never reaches its relaxed
 * fallback, so nothing has to be recovered from an erased type. `relaxed = true` stays on to cover
 * whatever is left - the members whose types no generator supports, and anything inherited that
 * isn't enumerated here.
 */
internal class InterfaceMockGenerator : MockGenerator {
    override fun supports(
        type: KSType,
        context: MockContext,
    ): Boolean {
        val declaration = type.declaration as? KSClassDeclaration ?: return false
        if (declaration.isOwnedByAnotherGenerator()) return false
        val isMockableKind =
            declaration.classKind == ClassKind.INTERFACE ||
                (declaration.classKind == ClassKind.CLASS && Modifier.DATA !in declaration.modifiers)
        return isMockableKind && type.toTypeName() != null
    }

    override fun generate(
        type: KSType,
        context: MockContext,
    ): CodeBlock {
        val declaration = type.declaration as KSClassDeclaration
        val rawClassName = ClassName(declaration.packageName.asString(), declaration.simpleName.asString())
        declaration.selfImplementingCompanionMock(rawClassName)?.let { return it }

        val mockCall = CodeBlock.of("%M<%T>(relaxed = true)", MOCKK_FUNCTION, type.toTypeName())
        val stubs = declaration.memberStubs(type, context)
        if (stubs.isEmpty()) return mockCall

        // mockk()'s trailing lambda is applied to the new mock, so the stubs read as
        // `every { property } returns ...`: inside every {}, the innermost receiver is MockK's
        // matcher scope, and the member resolves against the mock as the enclosing receiver.
        return CodeBlock
            .builder()
            .add("%L {\n", mockCall)
            .indent()
            .apply { stubs.forEach(::add) }
            .unindent()
            .add("}")
            .build()
    }

    private fun KSClassDeclaration.memberStubs(
        containing: KSType,
        context: MockContext,
    ): List<CodeBlock> = propertyStubs(containing, context) + functionStubs(containing, context)

    private fun KSClassDeclaration.propertyStubs(
        containing: KSType,
        context: MockContext,
    ): List<CodeBlock> =
        getAllProperties()
            .filter { it.isStubbable() }
            .mapNotNull { property ->
                val value = context.stubValue(property.asMemberOf(containing)) ?: return@mapNotNull null
                CodeBlock.of("%M { %L } returns %L\n", EVERY_FUNCTION, property.simpleName.asString(), value)
            }.toList()

    private fun KSClassDeclaration.functionStubs(
        containing: KSType,
        context: MockContext,
    ): List<CodeBlock> =
        getAllFunctions()
            .filter { it.isStubbable() }
            .mapNotNull { function ->
                val returnType = function.asMemberOf(containing).returnType ?: return@mapNotNull null
                val value = context.stubValue(returnType) ?: return@mapNotNull null
                // An argument matcher per parameter: PrevHam can't know which arguments the
                // previewed code will pass, so every call gets the same stubbed value.
                val matchers = function.parameters.joinToString { "any()" }
                val every = if (Modifier.SUSPEND in function.modifiers) CO_EVERY_FUNCTION else EVERY_FUNCTION
                CodeBlock.of(
                    "%M { %L(%L) } returns %L\n",
                    every,
                    function.simpleName.asString(),
                    matchers,
                    value,
                )
            }.toList()

    private fun KSPropertyDeclaration.isStubbable(): Boolean = isPublic() && extensionReceiver == null

    private fun KSFunctionDeclaration.isStubbable(): Boolean {
        if (!isPublic() || isConstructor() || extensionReceiver != null) return false
        // Matching a vararg parameter takes a spread of the matcher for its exact element type
        // (*anyLongVararg(), *anyVararg(), ...). Guessing that wrong emits a stub that doesn't
        // compile, which is worse than leaving a rare member to relaxed mode.
        if (parameters.any { it.isVararg }) return false
        // equals/hashCode/toString come from Any on every type. MockK relies on its own answers for
        // those, and stubbing them would break how it identifies and prints the mock.
        if (parentDeclaration?.qualifiedName?.asString() == KOTLIN_ANY_QUALIFIED_NAME) return false
        // A generic function's return type still mentions its own type parameters here, so there is
        // no concrete type to build a value for.
        return typeParameters.isEmpty()
    }

    // The value to stub a member with, or null to leave that member to relaxed mode - either
    // because there is nothing worth stubbing (Unit) or because no generator can build its type.
    private fun MockContext.stubValue(type: KSType): CodeBlock? {
        val qualifiedName = type.declaration.qualifiedName?.asString()
        if (qualifiedName == KOTLIN_UNIT_QUALIFIED_NAME) return null
        if (qualifiedName !in KOTLINX_FLOW_QUALIFIED_NAMES) {
            return if (canMock(type)) mock(type) else null
        }
        // A Flow is only ever read for the values it emits, so stub it with a real flow holding a
        // mock element rather than mocking the flow itself.
        val element =
            type.arguments
                .singleOrNull()
                ?.type
                ?.resolve() ?: return null
        if (!canMock(element)) return null
        return CodeBlock.of("%M(%L)", MUTABLE_STATE_FLOW_FUNCTION, mock(element))
    }

    // Some interfaces (e.g. androidx.compose.ui.Modifier) declare a companion object that
    // implements the interface itself, as a ready-made "empty" instance. Prefer that real
    // instance over a MockK mock when it's available.
    private fun KSClassDeclaration.selfImplementingCompanionMock(className: ClassName): CodeBlock? {
        val companion = declarations.filterIsInstance<KSClassDeclaration>().firstOrNull { it.isCompanionObject }
        val implementsSelf =
            companion?.superTypes?.any {
                it
                    .resolve()
                    .declaration.qualifiedName
                    ?.asString() == qualifiedName?.asString()
            } == true
        return if (implementsSelf) CodeBlock.of("%T", className) else null
    }

    // List/Set/Map and function types (kotlin.FunctionN) are also declared as `interface` in
    // Kotlin, but they already have dedicated generators (CollectionMockGenerator,
    // FunctionTypeMockGenerator) that produce more useful mocks than a MockK relaxed mock.
    private fun KSClassDeclaration.isOwnedByAnotherGenerator(): Boolean {
        val name = qualifiedName?.asString() ?: return false
        return name in KOTLIN_COLLECTION_QUALIFIED_NAMES || name.startsWith(KOTLIN_FUNCTION_TYPE_PREFIX)
    }

    // Builds the type name to write inside mockk<...>(), including generic type arguments (e.g.
    // Repository<String>). Returns null for anything we can't fully resolve (e.g. a star
    // projection like Repository<*>), so that case stays unsupported rather than emitting broken
    // code.
    private fun KSType.toTypeName(): TypeName? {
        val declaration = declaration as? KSClassDeclaration ?: return null
        val className = ClassName(declaration.packageName.asString(), declaration.simpleName.asString())
        if (arguments.isEmpty()) return className
        val typeArgumentNames =
            arguments.map { argument -> argument.type?.resolve()?.toTypeName() ?: return null }
        return className.parameterizedBy(typeArgumentNames)
    }

    private companion object {
        val MOCKK_FUNCTION = MemberName("io.mockk", "mockk")
        val EVERY_FUNCTION = MemberName("io.mockk", "every")
        val CO_EVERY_FUNCTION = MemberName("io.mockk", "coEvery")
        val MUTABLE_STATE_FLOW_FUNCTION = MemberName("kotlinx.coroutines.flow", "MutableStateFlow")
    }
}
