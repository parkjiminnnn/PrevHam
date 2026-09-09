package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Visibility
import com.squareup.kotlinpoet.CodeBlock
import io.github.parkjiminnnn.compiler.codegen.buildNamedArgumentsCall

/**
 * Builds a real instance of anything whose constructor can be called.
 *
 * The dividing line is "can the constructor be called", not "is it a data class". The `data` keyword
 * changes nothing about whether a value can be built, and gating on it left an ordinary class mocked
 * for no reason (issue #78):
 *
 * ```kotlin
 * data class D(val x: String)   // D(x = "mock")
 * class      C(val x: String)   // mockk<C>(relaxed = true)
 * ```
 *
 * Constructing is what the rest of the registry already prefers - [SealedTypeMockGenerator] builds a
 * real subtype, [ObjectMockGenerator] references the singleton, [InterfaceMockGenerator] takes a
 * self-implementing companion over a mock. Plain classes were the one exception.
 *
 * ### What it buys, measured after #75
 *
 * Not output size. Once member stubbing was narrowed, a mocked plain class was already one line, so
 * this is about the values in it: a mock answers through MockK's relaxed mode, which invents `""` and
 * `0` from the return type, while a constructed instance carries PrevHam's own defaults and whatever
 * the value file says. A Preview showing `""` beside a data class showing `"제 1회 대학 음악제"` was the
 * `data` keyword leaking into what the reader sees.
 *
 * ### Why a constructor with no parameters is not called
 *
 * Running an arbitrary constructor during Preview rendering is a real risk - an `init` block runs,
 * and on Android the no-argument class is exactly the dangerous shape:
 *
 * ```kotlin
 * class HomeViewModel {                       // constructing this runs the init
 *     val uiState: StateFlow<UiState> = ...
 * }
 * ```
 *
 * It is also the shape with nothing to gain. Construction is worth doing because it puts values in -
 * and with no parameters there are none, so `HomeViewModel()` carries exactly as much as
 * `mockk<HomeViewModel>(relaxed = true)` while additionally running whatever the class does. All
 * risk, no benefit, so it stays mocked.
 *
 * With parameters the trade reverses, and the risk is not a new one: a data class may carry the same
 * `init` block and has always been constructed. The arguments themselves are inert - literals, or
 * relaxed mocks that absorb calls made on them - so what is left is a class that rejects its own mock
 * arguments, and it fails visibly at render rather than silently.
 */
internal class ConstructorMockGenerator : MockGenerator {
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

    // For a generic class (e.g. Box<T>), a constructor parameter's own declared type (`value: T`) is
    // just the type parameter T, not the actual type argument (e.g. String) used at this call site.
    // asMemberOf() resolves each parameter's type as seen from `type` (e.g. Box<String>),
    // substituting type parameters with their actual arguments; for a non-generic class this is a
    // no-op and returns the declared types unchanged.
    private fun KSType.substitutedConstructorParameters(): List<MockParameter>? {
        val declaration = declaration as? KSClassDeclaration ?: return null
        if (!declaration.isConstructible()) return null
        val constructor =
            declaration.primaryConstructor
                ?.takeIf { it.parameters.isNotEmpty() }
                ?.takeIf { it.isCallableFromGeneratedFile(declaration) } ?: return null
        // asMemberOf() rejects a nullable containing type outright ("Item? is not a sub type of
        // the class/interface that contains <init>"), which would fail the whole KSP round rather
        // than this one type. A nullable class should still get a real instance where one can be
        // built - null is NullableFallbackMockGenerator's last resort, not the first answer - so
        // ask about the non-null form.
        val substitutedTypes = constructor.asMemberOf(makeNotNullable()).parameterTypes
        if (substitutedTypes.size != constructor.parameters.size) return null
        val owner = declaration.qualifiedName?.asString()
        return constructor.parameters.zip(substitutedTypes).map { (parameter, type) ->
            parameter.toMockParameter(type ?: return null, owner) ?: return null
        }
    }

    /**
     * Whether an instance of this declaration can exist at all.
     *
     * `ClassKind.CLASS` alone is not enough. A `data object` carries `Modifier.DATA` and a
     * synthesised zero-parameter constructor, so it would be emitted as `Loading()`, which does not
     * compile - [ObjectMockGenerator] is registered first, but the kind is checked here too so that
     * reordering the registry cannot bring it back (issue #77).
     */
    private fun KSClassDeclaration.isConstructible(): Boolean {
        if (classKind != ClassKind.CLASS) return false
        // Abstract and sealed have no instances of their own; sealed is SealedTypeMockGenerator's,
        // and an abstract class is what MockK is genuinely for. An inner class needs an enclosing
        // instance the generated file has no way to produce.
        return NON_CONSTRUCTIBLE_MODIFIERS.none { it in modifiers }
    }

    /**
     * Whether the generated Preview file could write this constructor call.
     *
     * The Preview always goes into a new file in the consumer's own module, so `private` and
     * `protected` are out of reach. `internal` works only when the declaration is part of the
     * compilation being processed - an `internal` constructor in a dependency is a different module's
     * internal, and calling it would not compile.
     */
    private fun KSFunctionDeclaration.isCallableFromGeneratedFile(declaration: KSClassDeclaration): Boolean =
        when (getVisibility()) {
            Visibility.PUBLIC -> true
            Visibility.INTERNAL -> declaration.isFromSource()
            else -> false
        }

    private companion object {
        val NON_CONSTRUCTIBLE_MODIFIERS = listOf(Modifier.ABSTRACT, Modifier.SEALED, Modifier.INNER)
    }
}
