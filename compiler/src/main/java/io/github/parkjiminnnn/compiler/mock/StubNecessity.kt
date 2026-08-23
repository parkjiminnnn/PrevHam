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
        val declaration = type.declaration as? KSClassDeclaration ?: return false
        // The search can't say anything useful about a type it won't look inside, and looking
        // inside a compiled dependency is what produces the false positives described below.
        if (!declaration.isFromSource()) return false
        val name = declaration.qualifiedName?.asString() ?: return false
        return cache.getOrPut(name) { type.reachesErasedMember() }
    }

    /**
     * Whether a type is declared in the sources being compiled, rather than a compiled dependency.
     *
     * The search stops at anything else. Walking into the platform finds erased members everywhere -
     * `Throwable` exposes `Array<StackTraceElement>`, whose `get` returns a type parameter - so
     * practically every type would be marked as needing a stub, which is the state generation
     * exploded from in issue #75.
     *
     * The cost is that a type from another module or a library isn't searched through, so an erased
     * member behind one isn't found. `Flow` is unaffected, being recognised directly rather than by
     * searching.
     */
    private fun KSClassDeclaration.isFromSource(): Boolean = origin == Origin.KOTLIN || origin == Origin.JAVA

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

    /** A type relaxed mode can't produce a usable value for on its own. */
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
        val pending = ArrayDeque<KSClassDeclaration>()

        (declaration as? KSClassDeclaration)?.let { pending += it }
        while (pending.isNotEmpty()) {
            val declaration = pending.removeFirst()
            val name = declaration.qualifiedName?.asString() ?: continue
            if (!visited.add(name)) continue

            val memberTypes =
                declaration.getAllProperties().filter { it.isStubbable() }.map { it.type.resolve() } +
                    declaration.getAllFunctions().filter { it.isStubbable() }.mapNotNull { it.returnType?.resolve() }

            for (memberType in memberTypes) {
                if (memberType.isErased()) return true
                if (memberType.isLiteral()) continue
                val memberDeclaration = memberType.declaration as? KSClassDeclaration ?: continue
                if (!memberDeclaration.isFromSource()) continue
                pending += memberDeclaration
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
