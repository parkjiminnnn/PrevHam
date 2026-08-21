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
| `SealedTypeMockGenerator` | Sealed interfaces and sealed classes | `UiState.Loading` |
| `InterfaceMockGenerator` | Interfaces, and non-data classes not owned by another generator | `mockk<ImageLoader>(relaxed = true)`, with stubs only for members relaxed mode can't answer |
| `DataClassMockGenerator` | Data classes | `User(id = 1, name = "mock", age = 1)` |
| `CollectionMockGenerator` | `List`, `Set`, `Map` | `listOf(1)`, `mapOf("mock" to 1)` |
| `FunctionTypeMockGenerator` | `() -> R` and other `kotlin.FunctionN` types | `{ }`, `{ 1 }` |
| `NullableFallbackMockGenerator` | Any nullable type no other generator supports | `null` |

## `MockGeneratorRegistry`: ordering matters

```kotlin
internal class MockGeneratorRegistry(private val generators: List<MockGenerator>) {
    fun supports(type: KSType, context: MockContext): Boolean =
        generators.any { it.supports(type, context) }

    fun generate(type: KSType, context: MockContext): CodeBlock =
        generators.first { it.supports(type, context) }.generate(type, context)
}
```

`supports`/`generate` are resolved by **list order**, via `any { }` / `first { }` short-circuiting —
the first generator in the list whose `supports` returns `true` wins. This makes generator order a
real design constraint, not cosmetic:

- **Generators that terminate immediately come first.** `Primitive`, `String`, and `Enum` never call
  back into the registry, so checking them first keeps the common case cheap.
- **`SealedType` must precede `Interface`.** A sealed interface is still an interface, and a sealed
  class is still a non-data class, so `InterfaceMockGenerator` would claim both and hand them to
  MockK — exactly what `SealedTypeMockGenerator` exists to avoid.
- **`Interface` comes after the container generators.** It's the broadest matcher in the registry, so
  anything with a more specific generator has to be checked before it.
- **`Interface` must exclude types owned by other generators.** `List`, `Set`, `Map`, and
  `kotlin.FunctionN` types are *also* declared as Kotlin interfaces, so without an explicit exclusion
  `InterfaceMockGenerator` would match them before `CollectionMockGenerator` or
  `FunctionTypeMockGenerator` ever got a chance — this was an actual regression, fixed by
  `isOwnedByAnotherGenerator()` checking against the qualified names in `KotlinTypeNames.kt`.
- **`NullableFallbackMockGenerator` is always last.** A nullable `String?` should still get a real
  `"mock"` value from `StringMockGenerator` when possible; `null` is only the fallback when nothing
  else in the registry supports the (nullable) type.

## Bounding recursion: cycle detection, not depth

`DataClassMockGenerator`, `CollectionMockGenerator`, `FunctionTypeMockGenerator`, and
`InterfaceMockGenerator`'s member stubbing are all *recursive* — mocking a
`data class Order(val address: Address)` requires mocking `Address` too, which might itself contain
further nested types. Left unchecked, a self-referential type (`data class Node(val next: Node)`)
would recurse forever and overflow the stack.

This used to be bounded by a depth counter, `MAX_DEPTH = 3`. The trouble with counting depth is that
it can't tell *infinite* from merely *deep*: a perfectly ordinary
`Success → Organization → Festival → List<Poster>` was rejected at the fourth level for having no
cycle in it whatsoever (issue #60). Whatever number is chosen, some real model exceeds it.

Recursion is bounded by the path instead. `MockContext` carries the set of types currently being
expanded, and descending into one already on that set produces a **blocked** context rather than a
deeper one:

```kotlin
internal class MockContext private constructor(
    private val registry: MockGeneratorRegistry,
    private val expanding: Set<String>,
    private val remainingSteps: Int,
    private val isBlocked: Boolean,
) {
    fun canMock(type: KSType): Boolean {
        if (isBlocked) return false
        return registry.supports(type, descend(type))
    }

    fun mock(type: KSType): CodeBlock = registry.generate(type, descend(type))
}
```

Recursive generators hold no registry of their own; they call `context.canMock(inner)` /
`context.mock(inner)`, and the context passes the work back to the registry with the path extended.
So the registry is a single flat list — no depth-indexed chain, no "this generator is a leaf so it
survives to the bottom" special cases.

A blocked context answers `canMock` with `false` for everything, **without consulting the
registry** — that is what stops the recursion, and what stops `supports()` from recursing while
deciding. It is deliberately not the same as failing outright: generators that expand nothing still
answer at that point, so a nullable field falls back to `null`, a primitive to its literal, an
interface to a bare relaxed mock. Stopping should cost one member, not the whole parameter:

```kotlin
data class Node(val value: Int, val next: Node?)   // Node(value = 1, next = null)
data class Node(val next: Node)                    // skipped - no such value exists in any code
interface Node { val next: Node }                  // mockk<Node>(relaxed = true)
```

The interface case reaches the bound only when `next` is stubbed at all, which takes an erased member
somewhere below it — see [which members get stubbed](#which-members-get-stubbed).

The path key carries type arguments, not just the declaration. Keyed on the declaration alone, the
outer and inner `Box` of a finite `Box<Box<Item>>` would look like the same type and be rejected.
Nullability is left out on purpose: `Node` and `Node?` are the same type to recurse into, and
treating them as distinct would let `data class Node(val next: Node?)` alternate between them
forever.

One case cycle detection can't catch is a generic type whose argument grows on every step:

```kotlin
data class Wrapper<T>(val inner: Wrapper<Wrapper<T>>)
```

Each step produces a type never seen before, so no key ever repeats. No value of that type can be
constructed in ordinary code either, but the declaration is legal and must not hang the build, so
`MAX_PATH_LENGTH` caps the path at 64. That is a safety net for pathological declarations, not a
supported nesting limit — real models are nowhere near it.

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
recurses into each constructor parameter through the context, then emits a named-argument
constructor call built by the shared `buildNamedArgumentsCall` helper (also used for the generated
Preview function's own call to the original composable).

**`SealedTypeMockGenerator`** builds a real instance of one of a sealed type's concrete subtypes
instead of handing the sealed type to MockK. MockK *can* produce a value for a sealed type — it
instantiates a subtype through Objenesis, so `is`/`when` checks against it pass. What it can't do is
produce a *useful* one: Objenesis skips the constructor, so fields stay unset; the subtype it picks
is its own choice, invisible in the generated file and not guaranteed stable; and every member read
off it goes through relaxed mode, which is where the erasure problem below begins. Emitting
`UiState.Loading` or `PaymentResult.Approved(receiptId = 1L)` avoids all three — the subtype is
chosen here and written into the file, with the same mock values every other generator produces.

Which subtype gets built has to be stable across builds, and `getSealedSubclasses()` promises no
particular order — not even declaration order. So candidates are sorted explicitly: `object` subtypes
first (they need nothing constructed, so a blocked context can't turn them away, and they introduce
no invented field values), then by simple name. The first one the context can actually build
wins. Generic sealed types are left to `InterfaceMockGenerator`, since substituting their subtypes'
type arguments isn't something `asStarProjectedType()` can do.

**`InterfaceMockGenerator`** covers interfaces and non-data classes. It prefers a real instance over a
mock when the type has a companion object that implements the type itself (`object Modifier : Modifier`
-style self-implementing companions, e.g. Compose's `Modifier`), emitting a plain reference to the
companion rather than a MockK mock. Otherwise it falls back to `mockk<T>(relaxed = true)`. For generic
interfaces (`Repository<String>`), `toTypeName()` recursively rebuilds a `ParameterizedTypeName` from
`KSType.arguments` so the emitted mock carries its full generic signature
(`mockk<Repository<String>>(relaxed = true)`, not a raw-type `mockk<Repository>(relaxed = true)` that
wouldn't compile).

`relaxed = true` tells MockK to auto-stub unspecified member calls instead of throwing — but it can
only work from the **erased** return type, and `StateFlow<T>.value` erases to `Object`. So reading
`viewModel.uiState.value` off a relaxed mock hands back a bare `java.lang.Object`, and the caller's
checkcast to the declared type throws:

```
java.lang.ClassCastException: class java.lang.Object cannot be cast to class FestivalUiState
```

That was issue #59. (The classloader names such a message carries are just how the JVM formats a
`ClassCastException`; they aren't evidence of a classloader problem — `GeneratedMockValueTest` in
`sample` reproduces this in an ordinary JVM unit test.) So the generator stubs the members that hit
that, up front, using `mockk()`'s own trailing-lambda DSL:

```kotlin
mockk<ScreenStateHolder>(relaxed = true) {
    every { uiState } returns MutableStateFlow(ScreenUiState.Loading)
}
```

A stubbed member never reaches MockK's relaxed fallback, so it never has to recover a value from an
erased type. `relaxed = true` stays on to cover what's left.

### Which members get stubbed

Only the ones relaxed mode can't answer. A concrete return type survives erasure and relaxed mode
produces a usable value for it — `titleFor(state): String` above needs nothing. What it can't answer
is a type that erases away: a **type parameter** becomes `Object`, and so does whatever is read out
of a **`Flow`** (`StateFlow<T>.value`).

Stubbing every member instead of only those was the original implementation, and it made each member
a branch: the mock for a member is another mock, whose members are stubbed in turn, so output grew as
the product of the member counts across the graph until it exhausted the heap (issue #75).

A member also has to be stubbed when it merely *leads* to one that does:

```kotlin
interface Outer  { val middle: Middle }              // not erased itself...
interface Middle { val inner: Inner }
interface Inner  { val items: StateFlow<Item> }      // ...but this is
```

Left out, relaxed mode invents the mocks in between and `items` can never be reached. So
`StubNecessity` asks "does anything reachable from here need a stub", searching the type graph rather
than looking at the member alone. Only paths that reach something erased get built, which is what
leaves a graph with nothing erased in it — the issue #75 shape — expanded not at all.

Two boundaries keep that search honest, both learned by measuring:

- **It stops at literals.** The standard library is full of generic members; walk into `String` and a
  couple of hops later `Iterator<T>.next()` reports "erased", which would mark practically every type
  as needing a stub.
- **It stops at compiled dependencies.** `Throwable` reaches an erased member through
  `Array<StackTraceElement>.get`, and stubbing that far in emits `every { get(any()) }`, which
  collides with MockK's matcher scope and doesn't compile. The cost is that an erased member behind a
  library type isn't found; `Flow` is unaffected, being recognised directly rather than by searching.

Narrowing makes the blow-up rare rather than impossible — a graph whose every branch leads to
something erased still expands along all of them — so `MockContext.MAX_STUBS` caps the total for one
composable. It sits far above anything a real graph produces; past it, a mock is emitted bare rather
than with stubs. Unlike the path bound, it is shared across the whole generation rather than copied
per path, and it is consumed only while generating, never while deciding, so `canMock` stays free of
side effects.

### What is emitted

| Member shape | Emitted |
|---|---|
| Property | `every { name } returns <mock>` |
| Property or function returning `Flow`/`SharedFlow`/`StateFlow`/`Mutable*` | `... returns MutableStateFlow(<mock of the element type>)` |
| Function | `every { name(any(), any()) } returns <mock>` — one matcher per parameter |
| `suspend` function | the same, with `coEvery` |

Beyond the narrowing above, a member is also skipped when it returns `Unit`, when no generator
supports its type, when it's non-public or an extension, when it declares its own type parameters
(its return type would still mention them), or when it takes a `vararg` (matching one takes a spread
of the matcher for its exact element type, `*anyLongVararg()` / `*anyVararg()` / …, and guessing
wrong emits a stub that doesn't compile). `equals`/`hashCode`/`toString` are skipped too — MockK
relies on its own answers for those.

Member types are resolved through `asMemberOf`, so a `Holder<String>`'s `val value: T` is stubbed
with `"mock"` rather than left unresolved. The containing type is made non-null first: `asMemberOf`
rejects a nullable one outright, and the exception would fail the whole KSP round rather than one
member (issue #74).

**`CollectionMockGenerator`** matches `List`/`Set`/`Map` by qualified name, resolves each type
argument through the context, and picks `listOf`/`setOf`/`mapOf` accordingly. `Map`'s two type
arguments (`K`, `V`) are paired with Kotlin's `to` infix function into a single `Pair` argument.

**`FunctionTypeMockGenerator`** matches any `kotlin.FunctionN` type. A lambda literal doesn't need to
reference its parameters to satisfy a function-type signature, so the same `{ }` (for `Unit`-returning
functions) or `{ <mock> }` (recursing into the return type through the context) body is valid
regardless of how many parameters the function type declares — `() -> Boolean` and
`(String, Int) -> Boolean` both just need *some* expression producing a `Boolean` as their last line.

**`NullableFallbackMockGenerator`** is the last resort: any `KSType` with `isMarkedNullable == true`
that no earlier generator claimed becomes a literal `null`.
