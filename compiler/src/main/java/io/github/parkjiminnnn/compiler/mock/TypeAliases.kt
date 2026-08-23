package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter

/**
 * The type an alias stands for, or the type itself when it isn't one.
 *
 * A typealias is a second name for a type rather than a type of its own, but KSP reports it as a
 * [KSTypeAlias] declaration. Every generator narrows to `KSClassDeclaration`, so an aliased type
 * matched nothing and the whole Preview was skipped with "no mock generator available" - issue #81.
 * This is not limited to aliases a user writes: `kotlin.Comparator` is an alias for
 * `java.util.Comparator`, so the same parameter was supported or not depending on which name it was
 * written with.
 *
 * Resolving here rather than in each generator means the rest of the pipeline only ever sees real
 * class declarations, as it did before aliases were handled at all.
 */
internal fun KSType.resolveTypeAliases(): KSType {
    var current = this
    // Kotlin rejects a recursive alias, so a chain is always finite. The bound is only here so a
    // malformed or synthetic declaration can't hang the compiler round.
    repeat(MAX_ALIAS_CHAIN) {
        val alias = current.declaration as? KSTypeAlias ?: return current
        val expanded = alias.type.resolve().substitute(alias, current.arguments) ?: return current
        // An alias carries the nullability written at the use site, not the aliased type's:
        // `MyItem?` has to stay nullable after expanding to `Item`.
        current = if (current.isMarkedNullable) expanded.makeNullable() else expanded
    }
    return current
}

/**
 * The aliased type with the use site's type arguments applied to the alias's own parameters.
 *
 * `typealias MyBox<T> = Box<T>` declares `T` itself, so resolving its right-hand side alone yields
 * `Box<T>` with nothing substituted - `MyBox<Item>` has to become `Box<Item>`.
 *
 * [KSType.replace] is positional, which is exact only when the right-hand side applies the alias's
 * parameters in declaration order. That covers the shapes aliases are actually written in;
 * `typealias Swapped<A, B> = Pair<B, A>` and `typealias Nested<T> = Box<Box<T>>` are not matched and
 * return null, which leaves the parameter unsupported rather than silently mocking the wrong type.
 */
private fun KSType.substitute(
    alias: KSTypeAlias,
    useSiteArguments: List<KSTypeArgument>,
): KSType? {
    if (alias.typeParameters.isEmpty()) return this
    if (useSiteArguments.size != alias.typeParameters.size) return null
    val appliedInOrder =
        arguments.map { (it.type?.resolve()?.declaration as? KSTypeParameter)?.name?.asString() } ==
            alias.typeParameters.map { it.name.asString() }
    return if (appliedInOrder) replace(useSiteArguments) else null
}

private const val MAX_ALIAS_CHAIN = 32
