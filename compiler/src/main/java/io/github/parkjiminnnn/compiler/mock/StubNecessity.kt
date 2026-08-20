package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.Origin

/**
 * Decides which members of a mocked type have to be stubbed, so the rest can be left to relaxed
 * mode.
 *
 * Relaxed mode builds its answer from the **erased** return type, which is enough for anything whose
 * type survives erasure - `String`, `Int`, a concrete class, a sealed type. What it can't answer is a
 * type that erases away: a type parameter becomes `Object`, and so does whatever is read back out of
 * a `Flow` (`StateFlow<T>.value`). The caller's checkcast rejects that, which is the crash reported
 * in issue #59.
 *
 * Stubbing every member instead of only those made every member a branch, and the generated output
 * grew as the product of member counts across the graph - issue #75.
 *
 * A member also has to be stubbed when it merely *leads* to one that does. Left out, the mock in
 * between comes from relaxed mode and nothing below it can be reached:
 *
 * ```kotlin
 * interface Outer  { val middle: Middle }              // not erased itself...
 * interface Middle { val inner: Inner }
 * interface Inner  { val items: StateFlow<Item> }      // ...but this is
 * ```
 *
 * So the question asked of each member is "does anything reachable from here need a stub". Only
 * paths that reach something erased get built, which is what keeps a graph with nothing erased in it
 * from being expanded at all.
 */
internal class StubNecessity {
    private val cache = mutableMapOf<String, Boolean>()

    /** Whether a member declaring [type] has to be stubbed rather than left to relaxed mode. */
    fun isNeededFor(type: KSType): Boolean {
        if (type.isErased()) return true
        // Checked here as well as inside the search: a literal is the whole answer, and searching
        // into one finds the standard library's generic members and reports back a false positive.
        if (type.isLiteral()) return false
        // The search can't say anything useful about a type it won't look inside.
        if (!type.isSearchable()) return false
        val name = type.declaration.qualifiedName?.asString() ?: return false
        // Keyed by declaration alone: whether a type reaches an erased member is the same question
        // for Box<Item> and Box<Other>, since the member that erases is declared as T either way.
        return cache.getOrPut(name) { type.reachesErasedMember() }
    }

    /**
     * Whether the search may look inside a type.
     *
     * Anything declared in the sources being compiled is fair game. A compiled dependency is entered
     * only when it is **generic**, and that condition is doing real work in both directions.
     *
     * Without it, walking into the platform finds erased members everywhere - `Throwable` exposes
     * `Array<StackTraceElement>`, whose `get` returns a type parameter - so practically every type
     * would be marked as needing a stub. That was the state that let the generated output explode in
     * issue #75; `java.time.LocalDate`, with 56 stubbable members over 16 mutually-referencing return
     * types, is what actually exhausted the heap. Neither `Throwable` nor `LocalDate` has a type
     * argument, so neither is entered.
     *
     * But refusing every compiled type is too blunt, and that was issue #80: `Lazy<T>.value`,
     * `Iterator<T>.next()` and their kind erase exactly the way a source-declared generic does, and
     * missing them puts back the `ClassCastException` from issue #59. A type with no type argument
     * has no type parameter to erase, so the condition keeps the ones that matter and drops the ones
     * that caused the explosion.
     *
     * `Flow` is unaffected either way, being recognised by [isErased] rather than by searching.
     */
    private fun KSType.isSearchable(): Boolean {
        val declaration = declaration as? KSClassDeclaration ?: return false
        if (declaration.origin == Origin.KOTLIN || declaration.origin == Origin.JAVA) return true
        // An array is the one generic the search must not enter. `Array<T>.get` returns a type
        // parameter, so any member holding one would be marked as needing a stub - and that stub
        // does not compile, because `every { get(any()) }` resolves against MockKMatcherScope's
        // own `get`. Throwable reaches two arrays this way, through stackTrace and suppressed.
        if (declaration.qualifiedName?.asString() == KOTLIN_ARRAY_QUALIFIED_NAME) return false
        return arguments.isNotEmpty()
    }

    /**
     * A type that becomes a literal rather than a mock, so nothing is read out of it through one.
     *
     * The search has to stop at these. The standard library is full of generic members - walk into
     * `String` and two hops later `Iterator<T>.next()` says "erased", which would mark practically
     * every type as needing a stub and put the explosion straight back.
     */
    private fun KSType.isLiteral(): Boolean {
        val declaration = declaration
        return declaration.qualifiedName?.asString() in KOTLIN_LITERAL_QUALIFIED_NAMES ||
            (declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS
    }

    /**
     * A type relaxed mode can't produce a usable value for on its own.
     *
     * `Flow` and `SharedFlow` are named rather than searched because there is nothing to find: their
     * type parameter never appears in a return type, arriving through `collect`'s collector instead.
     * A member walk only inspects return types, so it reports them as safe however far it is allowed
     * to look.
     */
    private fun KSType.isErased(): Boolean =
        declaration is KSTypeParameter ||
            declaration.qualifiedName?.asString() in KOTLINX_FLOW_QUALIFIED_NAMES

    /**
     * Whether any type reachable through this one declares a member that erases.
     *
     * Reachability, not path-walking: whether a type can reach an erased member doesn't depend on
     * how it was reached, so one visited set across the whole search is enough. Tracking it per path
     * instead re-explores every branch and costs the product of the member counts - which is the
     * shape of the problem this exists to avoid in the first place.
     */
    private fun KSType.reachesErasedMember(): Boolean {
        val visited = mutableSetOf<String>()
        val pending = ArrayDeque<KSType>()
        pending += this

        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            val declaration = current.declaration as? KSClassDeclaration ?: continue
            val name = declaration.qualifiedName?.asString() ?: continue
            if (!visited.add(name)) continue

            val memberTypes =
                declaration.getAllProperties().filter { it.isStubbable() }.map { it.type.resolve() } +
                    declaration.getAllFunctions().filter { it.isStubbable() }.mapNotNull { it.returnType?.resolve() }

            for (memberType in memberTypes) {
                if (memberType.isErased()) return true
                if (memberType.isLiteral()) continue
                if (!memberType.isSearchable()) continue
                pending += memberType
            }
        }
        return false
    }
}

internal fun KSPropertyDeclaration.isStubbable(): Boolean = isPublic() && extensionReceiver == null

internal fun KSFunctionDeclaration.isStubbable(): Boolean {
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
