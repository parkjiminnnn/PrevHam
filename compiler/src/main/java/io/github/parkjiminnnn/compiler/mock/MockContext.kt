package io.github.parkjiminnnn.compiler.mock

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.CodeBlock

/**
 * The path a mock generator is currently on, and the way to descend one step further along it.
 *
 * Recursive generators don't hold a registry of their own; they ask their context to mock an inner
 * type ([canMock] / [mock]), and the context hands the work back to the registry with the path
 * extended by the type it just entered. That path is what bounds recursion.
 *
 * Descending into a type already on the path produces a *blocked* context instead of a deeper one.
 * A blocked context answers [canMock] with `false` for everything, without consulting the registry,
 * so a generator that needs to expand something reports the type as unsupported and the recursion
 * ends there. Generators that don't expand anything still work at that point: a nullable field
 * falls back to `null`, a primitive to its literal, an interface to a bare relaxed mock. That
 * matters - stopping the recursion should cost as little as possible, not fail the whole parameter.
 *
 * Every value here is immutable and copied on descent, so [canMock] stays free of side effects and
 * always agrees with what [mock] will do for the same type.
 */
internal class MockContext private constructor(
    private val registry: MockGeneratorRegistry,
    private val expanding: Set<String>,
    private val remainingSteps: Int,
    private val isBlocked: Boolean,
    private val stubBudget: StubBudget,
    private val values: MockValues,
    private val slot: String?,
) {
    /**
     * Whether another member stub can still be afforded for this composable, consuming one if so.
     *
     * The path bound above limits how *deep* generation goes, not how *wide*. A graph whose every
     * branch leads to something that must be stubbed produces stubs as the product of its member
     * counts, which is what exhausted the heap in issue #75; narrowing what gets stubbed made that
     * rare rather than impossible.
     *
     * Deliberately consumed only while generating, never while deciding: [canMock] stays free of
     * side effects, and running out degrades a mock to a bare one rather than making a type look
     * unsupported.
     */
    fun canAffordStub(): Boolean = stubBudget.tryConsume()

    /**
     * The value configured for the slot being filled, or null when there is none.
     *
     * A slot names where a value is declared rather than its type - `com.example.Festival.name` -
     * because type alone cannot choose between `Festival.name` and `User.name`.
     */
    fun slotValue(): String? = slot?.let { values[it] }

    /** Whether an inner type can be mocked from here, without expanding it. */
    fun canMock(
        type: KSType,
        slot: String? = null,
    ): Boolean {
        if (isBlocked) return false
        val resolved = type.resolveTypeAliases()
        return registry.supports(resolved, descend(resolved, slot))
    }

    /** The mock for an inner type. Only valid when [canMock] returned true for the same type. */
    fun mock(
        type: KSType,
        slot: String? = null,
    ): CodeBlock {
        val resolved = type.resolveTypeAliases()
        return registry.generate(resolved, descend(resolved, slot))
    }

    // The slot travels with the descent rather than being remembered: it names the one place being
    // filled right now, and anything deeper is a different place with a slot of its own (or none).
    private fun descend(
        type: KSType,
        slot: String?,
    ): MockContext {
        val key = type.pathKey()
        // Entering a type already being expanded on this path would recurse forever - this is the
        // `data class Node(val next: Node)` case. Anything finite, however deeply nested, never
        // repeats a key and runs to completion.
        if (remainingSteps == 0 || key in expanding) {
            return MockContext(registry, expanding, 0, isBlocked = true, stubBudget, values, slot)
        }
        // The budget is shared rather than copied - it bounds the whole composable's output, not
        // one path through it.
        return MockContext(registry, expanding + key, remainingSteps - 1, isBlocked = false, stubBudget, values, slot)
    }

    // Type arguments belong in the key: keyed on the declaration alone, the outer and inner Box of
    // a perfectly finite Box<Box<Item>> would look like the same type and be rejected. Nullability
    // is deliberately left out - Node and Node? are the same type to recurse into, and treating
    // them as distinct would let `data class Node(val next: Node?)` alternate between them forever.
    private fun KSType.pathKey(): String {
        val name = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
        if (arguments.isEmpty()) return name
        return arguments.joinToString(prefix = "$name<", postfix = ">") {
            it.type
                ?.resolve()
                ?.pathKey() ?: "*"
        }
    }

    companion object {
        // Cycle detection covers self-reference, but not a generic type whose argument grows on
        // every step - `data class Wrapper<T>(val inner: Wrapper<Wrapper<T>>)` produces a type
        // never seen before each time, so no key ever repeats. Such a type can't be instantiated in
        // ordinary code either, but the declaration is legal and must not hang the build. This is
        // the safety net for that, set far beyond anything a real model reaches; it is not a
        // supported depth limit.
        const val MAX_PATH_LENGTH = 64

        // Far more stubs than any real dependency graph produces, so it never shapes ordinary
        // output - it exists so a pathological graph degrades to bare mocks instead of a heap dump
        // or a "Method too large" from the Kotlin backend.
        const val MAX_STUBS = 500

        fun root(
            registry: MockGeneratorRegistry,
            values: MockValues = MockValues.EMPTY,
        ): MockContext =
            MockContext(
                registry,
                expanding = emptySet(),
                remainingSteps = MAX_PATH_LENGTH,
                isBlocked = false,
                stubBudget = StubBudget(MAX_STUBS),
                values = values,
                slot = null,
            )
    }
}

/** Counts member stubs across one composable's generation, so width can be bounded as well as depth. */
internal class StubBudget(
    private var remaining: Int,
) {
    fun tryConsume(): Boolean {
        if (remaining <= 0) return false
        remaining--
        return true
    }
}
