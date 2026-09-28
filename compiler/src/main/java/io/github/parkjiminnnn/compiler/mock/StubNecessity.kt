package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
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

    /**
     * Whether a member declaring [type] has to be stubbed rather than left to relaxed mode.
     *
     * [declaredOnSource] says whether the member holding it was declared in the sources being
     * compiled, which is what decides how far a compiled type may be opened. See
     * [opensCompiledContainer].
     */
    fun isNeededFor(
        declaredType: KSType,
        declaredOnSource: Boolean,
    ): Boolean {
        // A member declared with a typealias reaches here as the alias, which knows nothing about
        // what it stands for - a `typealias Items = StateFlow<Item>` would not be recognised as a
        // Flow and the member would be left to relaxed mode (issue #81).
        val type = declaredType.resolveTypeAliases()
        if (type.needsStubItself()) return true
        // Checked here as well as inside the search: a literal is the whole answer, and searching
        // into one finds the standard library's generic members and reports back a false positive.
        if (type.isLiteral()) return false
        val declaration = type.declaration as? KSClassDeclaration ?: return false
        if (!declaration.isFromSource()) return declaredOnSource && declaration.opensCompiledContainer()
        val name = declaration.qualifiedName?.asString() ?: return false
        return cache.getOrPut(name) { type.reachesErasedMember() }
    }

    /**
     * Whether a compiled type may be opened, having been named by a member of the consumer's own.
     *
     * A container like `State<T>`, `Lazy<T>` or `LiveData<T>` exists to be read through, and reading
     * through it is exactly what erases: `value` compiles to `Object getValue()`, relaxed mode
     * answers from that, and the caller's checkcast rejects it - issue #59's crash, one type removed.
     * The search never reached them, because it stops at anything not declared in the sources being
     * compiled (issue #80).
     *
     * Two limits make opening one safe, and #84 had neither:
     *
     * - **Only its own members are examined, and nothing below them.** No recursion, so this cannot
     *   branch. #84 let the search walk transitively through compiled types, and a `LocalDate` field
     *   reached `Stream` through `datesUntil()`, then `Optional` and `Iterator` from there - 61 mocks
     *   from one date (issue #87).
     * - **Only from a member the consumer wrote.** Every report of the crash came from a member
     *   declared on a consumer's own type; every explosion came from a compiled type's own members,
     *   which nobody asked for. `LocalDate.datesUntil()` fails this and stays shut.
     *
     * "Has a type argument" was #84's condition and is the wrong one: it says a type erases, not
     * that looking inside it is cheap.
     */
    private fun KSClassDeclaration.opensCompiledContainer(): Boolean {
        val name = qualifiedName?.asString() ?: return false
        return cache.getOrPut("compiled:$name") {
            getAllProperties().filter { it.isStubbable() }.any {
                it.type
                    .resolve()
                    .resolveTypeAliases()
                    .needsStubItself()
            } ||
                getAllFunctions().filter { it.isStubbable() }.any {
                    it.returnType
                        ?.resolve()
                        ?.resolveTypeAliases()
                        ?.needsStubItself() == true
                }
        }
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
     * A type relaxed mode can't produce a usable value for, whatever it is asked.
     *
     * Two reasons, and neither depends on where the type came from. A type that erases reads back
     * as a bare `Object` and the caller's checkcast rejects it. An `object` has exactly one
     * instance and relaxed mode does not return it - MockK builds a fresh one through Objenesis, so
     * `holder.state === Loading` is false and `when (holder.state) { Loading -> ... }` matches
     * nothing. Stubbing an object costs nothing either way: the value is a reference, not another
     * mock, so it adds no recursion (issue #77).
     */
    private fun KSType.needsStubItself(): Boolean = isErased() || (declaration as? KSClassDeclaration)?.classKind == ClassKind.OBJECT

    /** A type whose own reference erases to `Object` when read back through a mock. */
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

            // Paired with where each member was declared, because an inherited one can come from a
            // compiled supertype even while the type being walked is from source.
            val memberTypes =
                declaration.getAllProperties().filter { it.isStubbable() }.map {
                    it.type
                        .resolve()
                        .resolveTypeAliases() to it.declaredOnSource()
                } +
                    declaration.getAllFunctions().filter { it.isStubbable() }.mapNotNull {
                        it.returnType
                            ?.resolve()
                            ?.resolveTypeAliases()
                            ?.to(it.declaredOnSource())
                    }

            for ((memberType, declaredOnSource) in memberTypes) {
                if (memberType.needsStubItself()) return true
                if (memberType.isLiteral()) continue
                val memberDeclaration = memberType.declaration as? KSClassDeclaration ?: continue
                if (!memberDeclaration.isFromSource()) {
                    if (declaredOnSource && memberDeclaration.opensCompiledContainer()) return true
                    continue
                }
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

/**
 * Whether a type is declared in the sources being compiled, rather than a compiled dependency.
 *
 * [StubNecessity]'s search stops at anything else. Walking into the platform finds erased members
 * everywhere - `Throwable` exposes `Array<StackTraceElement>`, whose `get` returns a type parameter -
 * so practically every type would be marked as needing a stub, which is the state generation exploded
 * from in issue #75.
 *
 * The cost is that a type from another module or a library isn't searched through, so an erased
 * member behind one isn't found. `Flow` is unaffected, being recognised directly rather than by
 * searching.
 *
 * The same gate decides which members can take a configured value, for the same reason on a different
 * axis: opening `java.time.LocalDate` would put its 56 stubbable members in the slot manifest and ask
 * a model about every one of them.
 */
internal fun KSClassDeclaration.isFromSource(): Boolean = origin == Origin.KOTLIN || origin == Origin.JAVA

/**
 * Whether the type that declares this member is part of the sources being compiled.
 *
 * Asked of the declaring type rather than the one being mocked, since `getAllProperties()` and
 * `getAllFunctions()` reach through supertypes: a consumer's own ViewModel inherits members from
 * `androidx.lifecycle.ViewModel`, and those are not members the consumer wrote.
 */
internal fun KSDeclaration.declaredOnSource(): Boolean = (parentDeclaration as? KSClassDeclaration)?.isFromSource() == true
