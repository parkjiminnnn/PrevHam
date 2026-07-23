# Mock Generation

For every parameter of a `@Prev`-annotated function, `compiler` needs to produce a compile-time-safe
Kotlin expression — a `CodeBlock` — that evaluates to a plausible value of that parameter's type. This
document covers how that pipeline is structured and how each supported type is handled.

## `MockGenerator`: one strategy per type shape

```kotlin
internal interface MockGenerator {
    fun supports(type: KSType): Boolean
    fun generate(type: KSType): CodeBlock
}
```

Each implementation owns exactly one kind of type. `supports` answers "can I produce a mock for this
`KSType`?" without side effects; `generate` is only ever called after `supports` returned `true` for
the same type.

| Generator | Handles | Example output |
|---|---|---|
| `PrimitiveMockGenerator` | `Int`, `Long`, `Short`, `Byte`, `Double`, `Float`, `Boolean`, `Char` | `1`, `true`, `'a'` |
| `StringMockGenerator` | `String` | `"mock"` |
| `EnumMockGenerator` | Enum classes | `Status.ACTIVE` (first declared entry) |
| `InterfaceMockGenerator` | Interfaces, and non-data classes not owned by another generator | `mockk<ImageLoader>(relaxed = true)` |
| `DataClassMockGenerator` | Data classes | `User(id = 1, name = "mock", age = 1)` |
| `CollectionMockGenerator` | `List`, `Set`, `Map` | `listOf(1)`, `mapOf("mock" to 1)` |
| `FunctionTypeMockGenerator` | `() -> R` and other `kotlin.FunctionN` types | `{ }`, `{ 1 }` |
| `NullableFallbackMockGenerator` | Any nullable type no other generator supports | `null` |

## `MockGeneratorRegistry`: ordering matters

```kotlin
internal class MockGeneratorRegistry(private val generators: List<MockGenerator>) {
    fun supports(type: KSType): Boolean = generators.any { it.supports(type) }
    fun generate(type: KSType): CodeBlock = generators.first { it.supports(type) }.generate(type)
}
```

`supports`/`generate` are resolved by **list order**, via `any { }` / `first { }` short-circuiting —
the first generator in the list whose `supports` returns `true` wins. This makes generator order a
real design constraint, not cosmetic:

- **Leaf generators before recursive ones.** `Primitive`, `String`, `Enum`, `Interface` are checked
  first because they terminate immediately — they never call back into the registry.
- **`Interface` must exclude types owned by other generators.** `List`, `Set`, `Map`, and
  `kotlin.FunctionN` types are *also* declared as Kotlin interfaces, so without an explicit exclusion
  `InterfaceMockGenerator` would match them before `CollectionMockGenerator` or
  `FunctionTypeMockGenerator` ever got a chance — this was an actual regression, fixed by
  `isOwnedByAnotherGenerator()` checking against the qualified names in `KotlinTypeNames.kt`.
- **`NullableFallbackMockGenerator` is always last.** A nullable `String?` should still get a real
  `"mock"` value from `StringMockGenerator` when possible; `null` is only the fallback when nothing
  else in the registry supports the (nullable) type.

## Depth-limited recursion

`DataClassMockGenerator`, `CollectionMockGenerator`, and `FunctionTypeMockGenerator` are all
*recursive* — mocking a `data class Order(val address: Address)` requires mocking `Address` too, which
might itself contain further nested types. Left unchecked, a self-referential type
(`data class Node(val next: Node?)`) or a very deeply nested one would recurse forever and overflow the
stack.

`MockGeneratorRegistry.default()` bounds this by building a **chain of registries**, one per recursion
depth, rather than one flat registry:

```kotlin
companion object {
    private const val MAX_DEPTH = 3

    fun default(): MockGeneratorRegistry = build(depth = 0)

    private fun build(depth: Int): MockGeneratorRegistry {
        val leafGenerators =
            listOf(PrimitiveMockGenerator(), StringMockGenerator(), EnumMockGenerator(), InterfaceMockGenerator())
        if (depth >= MAX_DEPTH) {
            return MockGeneratorRegistry(leafGenerators + NullableFallbackMockGenerator())
        }
        val nested = build(depth + 1)
        val recursiveGenerators =
            leafGenerators + DataClassMockGenerator(nested) + CollectionMockGenerator(nested) + FunctionTypeMockGenerator(nested)
        return MockGeneratorRegistry(recursiveGenerators + NullableFallbackMockGenerator())
    }
}
```

Each recursive generator is constructed with a *nested* registry — one level deeper — as the source of
mocks for its inner elements (`DataClassMockGenerator`'s field types, `CollectionMockGenerator`'s
element type, `FunctionTypeMockGenerator`'s return type). At `depth >= MAX_DEPTH`, the registry built
only contains leaf generators — no `DataClassMockGenerator`/`CollectionMockGenerator`/
`FunctionTypeMockGenerator` — so a type that's still "container-shaped" at that depth simply isn't
supported by any generator in that registry, and `supports()` returns `false` all the way back up the
chain. The processor then treats the top-level parameter as unsupported and skips generating a Preview
for that function (with a logged warning), rather than crashing the build.

This bounds recursion depth structurally (by which registry a call reaches) rather than with an
explicit counter threaded through every generator — a flat type used at depth 0 never even reaches the
deeper registries, so the common case pays no extra cost.

## `MockParameter`: decoupling "what to mock" from "how its type was found"

```kotlin
internal data class MockParameter(val name: String, val type: KSType, val hasDefault: Boolean)

internal fun KSValueParameter.toMockParameter(type: KSType = this.type.resolve()): MockParameter? {
    val name = name?.asString() ?: return null
    return MockParameter(name, type, hasDefault)
}
```

Every consumer of the mock pipeline (`buildMockArguments`, `firstUnsupportedParameter`, each
`MockGenerator`) operates on `MockParameter`, not directly on `KSValueParameter`. The default
`type: KSType = this.type.resolve()` covers the ordinary case (a top-level function parameter, whose
type is fully known from its own declaration), while `DataClassMockGenerator` overrides it with an
`asMemberOf`-substituted type for constructor parameters of a generic data class — see
[ksp-processing.md](ksp-processing.md#generic-type-resolution-resolve-vs-asmemberof) for why plain
`resolve()` can't produce a correct type there. Parameters with a default value (`hasDefault`) are
allowed to be skipped when unsupported, since the generated call can simply omit them.

## Generator walkthroughs

**`DataClassMockGenerator`** requires the `Modifier.DATA` modifier (interfaces and plain classes are
explicitly out of scope here — they're `InterfaceMockGenerator`'s job) and a primary constructor. It
recurses into each constructor parameter via the nested registry, then emits a named-argument
constructor call built by the shared `buildNamedArgumentsCall` helper (also used for the generated
Preview function's own call to the original composable).

**`InterfaceMockGenerator`** covers interfaces and non-data classes. It prefers a real instance over a
mock when the type has a companion object that implements the type itself (`object Modifier : Modifier`
-style self-implementing companions, e.g. Compose's `Modifier`), emitting a plain reference to the
companion rather than a MockK mock. Otherwise it falls back to `mockk<T>(relaxed = true)`. For generic
interfaces (`Repository<String>`), `toTypeName()` recursively rebuilds a `ParameterizedTypeName` from
`KSType.arguments` so the emitted mock carries its full generic signature
(`mockk<Repository<String>>(relaxed = true)`, not a raw-type `mockk<Repository>(relaxed = true)` that
wouldn't compile). `relaxed = true` tells MockK to auto-stub unspecified member calls with default
values instead of throwing, so the mock doesn't need every member configured just to exist.

**`CollectionMockGenerator`** matches `List`/`Set`/`Map` by qualified name, resolves each type
argument via the nested registry, and picks `listOf`/`setOf`/`mapOf` accordingly. `Map`'s two type
arguments (`K`, `V`) are paired with Kotlin's `to` infix function into a single `Pair` argument.

**`FunctionTypeMockGenerator`** matches any `kotlin.FunctionN` type. A lambda literal doesn't need to
reference its parameters to satisfy a function-type signature, so the same `{ }` (for `Unit`-returning
functions) or `{ <mock> }` (recursing into the return type via the nested registry) body is valid
regardless of how many parameters the function type declares — `() -> Boolean` and
`(String, Int) -> Boolean` both just need *some* expression producing a `Boolean` as their last line.

**`NullableFallbackMockGenerator`** is the last resort: any `KSType` with `isMarkedNullable == true`
that no earlier generator claimed becomes a literal `null`.
