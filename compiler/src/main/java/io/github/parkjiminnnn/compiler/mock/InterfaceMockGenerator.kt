package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
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
    private val stubNecessity = StubNecessity()

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
        declaration.selfImplementingCompanionMock(declaration.toClassName())?.let { return it }

        val mockCall = CodeBlock.of("%M<%T>(relaxed = true)", MOCKK_FUNCTION, type.toTypeName())
        val stubs = declaration.memberStubs(type, context)
        if (stubs.isEmpty()) return mockCall
        // mockk()'s trailing lambda is applied to the new mock, so the stubs sit inside it. Each
        // one names its receiver - `every { this@mockk.property } returns ...` - because inside
        // every {} the receivers nest, with MockK's matcher scope innermost and the mock outside
        // it, and an unqualified name resolves against the matcher scope first. Members called
        // get, invoke, less or hint lose that way and the generated code doesn't compile (issue
        // #83). Whether a name collides depends on its arity and parameter types as well, so the
        // receiver is named rather than a list of names avoided. `this@mockk` labels the lambda
        // passed to mockk(), whose receiver is declared T.() -> Unit, so it is the mock itself -
        // and in a nested mock it binds to the nearest enclosing one.
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
                val declaredType = property.type.resolve()
                // Asked first, and it is the only chance: a configurable member's type is a literal,
                // so StubNecessity answers "no stub needed" and the branch below never runs.
                context.configuredValue(property.parentDeclaration, property.simpleName.asString(), declaredType)?.let {
                    return@mapNotNull CodeBlock.of(
                        "%M { this@mockk.%L } returns %L\n",
                        EVERY_FUNCTION,
                        property.simpleName.asString(),
                        it,
                    )
                }
                if (!stubNecessity.isNeededFor(declaredType)) return@mapNotNull null
                if (!context.canAffordStub()) return@mapNotNull null
                // asMemberOf rejects a nullable containing type outright ("Logger? is not a sub
                // type of the class/interface that contains `name`"), and the exception would fail
                // the whole KSP round. Nullability of the reference doesn't change a member's type,
                // so the question is asked in the form KSP accepts (issue #74).
                val value =
                    context.stubValue(property.asMemberOf(containing.makeNotNullable()))
                        ?: return@mapNotNull null
                CodeBlock.of("%M { this@mockk.%L } returns %L\n", EVERY_FUNCTION, property.simpleName.asString(), value)
            }.toList()

    private fun KSClassDeclaration.functionStubs(
        containing: KSType,
        context: MockContext,
    ): List<CodeBlock> =
        getAllFunctions()
            .filter { it.isStubbable() }
            .mapNotNull { function ->
                val declaredReturn = function.returnType?.resolve() ?: return@mapNotNull null
                val every = if (Modifier.SUSPEND in function.modifiers) CO_EVERY_FUNCTION else EVERY_FUNCTION
                // An argument matcher per parameter: PrevHam can't know which arguments the
                // previewed code will pass, so every call gets the same stubbed value.
                val matchers = function.parameters.joinToString { "any()" }
                context.configuredValue(function.parentDeclaration, function.simpleName.asString(), declaredReturn)?.let {
                    return@mapNotNull CodeBlock.of(
                        "%M { this@mockk.%L(%L) } returns %L\n",
                        every,
                        function.simpleName.asString(),
                        matchers,
                        it,
                    )
                }
                if (!stubNecessity.isNeededFor(declaredReturn)) return@mapNotNull null
                if (!context.canAffordStub()) return@mapNotNull null
                val returnType = function.asMemberOf(containing.makeNotNullable()).returnType ?: return@mapNotNull null
                val value = context.stubValue(returnType) ?: return@mapNotNull null
                CodeBlock.of(
                    "%M { this@mockk.%L(%L) } returns %L\n",
                    every,
                    function.simpleName.asString(),
                    matchers,
                    value,
                )
            }.toList()

    /**
     * The value someone configured for a member, or null when it takes none.
     *
     * A data class field and the identical property on an interface used to behave differently: the
     * field is **constructed**, so it flows through StringMockGenerator where a slot is consulted,
     * while the interface member is **replaced by a mock** and a mock only says what `every { } returns`
     * tells it to. In Android that gap covered an ordinary shape - a composable taking a ViewModel
     * could be given no values at all (issue #103).
     *
     * ### Why this does not bring back #75
     *
     * #75 stubbed every member, and each stub's value was another mock whose members were stubbed in
     * turn, so output grew as the product of member counts across the graph. Here a stub is only ever
     * a literal, nothing recurses through it, and one is emitted only where a person wrote a value -
     * so the count is a sum bounded by a curated file rather than by the shape of a dependency graph.
     *
     * ### Why the owner has to come from source
     *
     * The slot is recorded whether or not anything is stubbed, because a value cannot be written for
     * a path the manifest never lists. That makes recording the thing to be careful with: without
     * this gate, mocking a `java.time.LocalDate` would list its 56 stubbable members and send every
     * one of them to a model to be answered - #75 by another route. The gate is the same one
     * [isFromSource] draws for the stub search, applied to whichever type **declares** the member, so
     * an inherited member of a compiled supertype is excluded too.
     */
    private fun MockContext.configuredValue(
        declaring: KSDeclaration?,
        name: String,
        declaredType: KSType,
    ): CodeBlock? {
        // The declared type, never the one asMemberOf substitutes. A generic member reads as its
        // type parameter here and is skipped, which is the answer wanted: Repository<String>.id and
        // Repository<Int>.id are one declaring path and cannot hold two different values.
        val type = declaredType.resolveTypeAliases()
        if (!type.takesConfiguredValue()) return null
        val owner =
            (declaring as? KSClassDeclaration)
                ?.takeIf { it.isFromSource() }
                ?.qualifiedName
                ?.asString() ?: return null
        val slot = MockSlot(owner, name, type.declaration.qualifiedName?.asString() ?: return null)
        if (!canMock(type, slot)) return null
        // Generated even when nothing will be stubbed with it: the leaf generator is what records
        // the slot, and an unlisted path is one nobody can write a value for.
        val value = mock(type, slot)
        if (!hasValueFor(slot)) return null
        if (!canAffordStub()) return null
        return value
    }

    // The value to stub a member with, or null to leave that member to relaxed mode - either
    // because there is nothing worth stubbing (Unit) or because no generator can build its type.
    private fun MockContext.stubValue(declaredType: KSType): CodeBlock? {
        // Resolved before the Flow check: an alias for a Flow has to take the real-flow branch
        // below rather than being mocked like any other type (issue #81).
        val type = declaredType.resolveTypeAliases()
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
    //
    // Through toClassName() rather than packageName plus simpleName, which drops every enclosing
    // declaration: a nested Screen.Listener came out as mockk<Listener>, which does not resolve, and
    // since this recurses into type arguments a nested data class did too once it sat inside a
    // generic - mockk<Repository<Item>> for Repository<Screen.Item> (issue #118).
    private fun KSType.toTypeName(): TypeName? {
        val declaration = declaration as? KSClassDeclaration ?: return null
        val className = declaration.toClassName()
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
