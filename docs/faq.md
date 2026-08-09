# FAQ / Troubleshooting

## Why did I get `no mock generator available for parameter 'X'`?

The full warning looks like:

```
[PrevHam] skipping @Prev on 'FunctionName': no mock generator available for parameter 'X'
```

It means parameter `X`'s type doesn't match any `MockGenerator` in the registry (see
[`mock-generation.md`](mock-generation.md)), **and** `X` has no default value — so PrevHam can't build
a valid call to the original composable, and skips generating a Preview for the whole function rather
than emitting a call that's missing a required argument.

This is an all-or-nothing decision per function: if even one required parameter is unsupported, no
Preview file is generated for that composable at all. Parameters that *do* have a default value are
allowed to be unsupported — the generated call simply omits them and lets the default apply.

## What types aren't supported today?

- **A type that has to contain an instance of itself**, like `data class Node(val next: Node)`. No
  value of that type can be constructed in ordinary code either. Recursion stops wherever a type is
  about to be expanded a second time on the same path, and where the stop lands on something that
  needs no expansion the mock is still built — `data class Node(val value: Int, val next: Node?)`
  becomes `Node(value = 1, next = null)`. There is **no depth limit**: nesting can go as deep as the
  model does. See [`mock-generation.md`](mock-generation.md#bounding-recursion-cycle-detection-not-depth).
- **Generic types with unresolvable type arguments**, e.g. a star projection like `Repository<*>`.
  `InterfaceMockGenerator` can't determine a concrete type to write inside `mockk<...>()` for these,
  so it reports the type as unsupported rather than emitting code that wouldn't compile.
- **A `@Prev` function whose own parameter type is a raw, unbound type parameter** — i.e. a generic
  `@Composable` function itself (as opposed to a non-generic function that merely *uses* a generic
  type like `Box<String>`, which is fully supported). This is a deliberate scope decision, not a
  current gap expected to close soon.

## Does mocking a `sealed interface`/`sealed class` break an exhaustive `when`?

Sealed types aren't mocked at all any more. `SealedTypeMockGenerator` builds a real instance of one of
the declared subtypes — `UiState.Loading`, or `PaymentResult.Approved(receiptId = 1L)` — so a `when`
over it behaves exactly as it would with a hand-written Preview.

This used to go through MockK, and an exhaustive `when` did work: MockK instantiates a sealed type via
Objenesis, which produces a real concrete subtype rather than a synthetic one. The reasons for moving
off it are different — Objenesis skips the constructor, so the instance's fields are unset; the
subtype it picks is MockK's choice rather than something recorded in the generated file; and every
member read off it is answered by relaxed mode, with the consequences described two questions down.

## Which subtype does PrevHam pick for a sealed type?

`object` subtypes first, then by simple name, taking the first one it can actually build. So a
`Loading`/`Success`/`Error` UI state resolves to `Loading`. The order is fixed rather than left to
`getSealedSubclasses()`, which promises no particular ordering, so the generated file doesn't change
between builds.

If no subtype can be built — the sealed type is generic, or every subtype's fields exceed the depth
limit — PrevHam falls back to `mockk<T>(relaxed = true)` for that type, with the caveat in the next
question.

## Why did my Preview crash with a `ClassCastException` on a ViewModel-shaped parameter?

```
java.lang.ClassCastException: class java.lang.Object cannot be cast to class FestivalUiState
```

This was issue #59, fixed in the release after 1.0.0. The cause is **type erasure**, not anything
Android- or Preview-specific.

`StateFlow<T>.value` erases to `Object` in the bytecode. MockK's relaxed mode answers an unstubbed
call by inventing a value for the return type it can see — which for a generic member is just
`Object`. So `viewModel.uiState.value` handed back a bare `java.lang.Object`, and the checkcast the
Kotlin compiler inserts at the call site rejected it.

PrevHam now stubs the members it can build values for up front:

```kotlin
mockk<HomeViewModel>(relaxed = true) {
    every { uiState } returns MutableStateFlow(FestivalUiState.Loading)
}
```

A stubbed member never reaches the relaxed fallback, so nothing has to be recovered from an erased
type. `GeneratedMockValueTest` in `sample` covers both directions — the stub yields the real state
object, and a relaxed-only mock still throws.

Worth noting, since the original report pointed this way: the classloader names in that message
(`... is in unnamed module of loader StudioModuleClassLoader ...`) are just how the JVM formats every
`ClassCastException`. They aren't a sign of a classloader problem — the same crash reproduces in an
ordinary JVM unit test.

Some members are still left to relaxed mode, and a composable reading a generic member off *those* can
still hit this crash:

- **`vararg` functions, generic functions, non-public members, and types no generator supports** (see
  the list above).
- **A member whose type is already being expanded further up the chain.** Recursion has to stop
  somewhere, and that mock comes out bare.

A long chain of interfaces is no longer one of these. `Outer.middle` → `Middle.inner` →
`Inner.items: StateFlow<Item>` used to leave the innermost mock bare once the old depth limit ran
out, which put this crash back within reach; nothing there revisits a type, so it is now stubbed all
the way down (issue #60).

Extracting a stateless composable that takes the resolved state directly, and putting `@Prev` on that,
avoids all of it — and is the better Compose shape regardless.

## My data class has a nullable field of an otherwise-unsupported type — does the whole function get skipped?

No. `NullableFallbackMockGenerator` matches *any* nullable type (`isMarkedNullable == true`) and is
always the last generator checked, so a nullable field always has *some* generator that supports it —
worst case, it falls back to a literal `null`. This applies recursively inside data classes,
collection elements, and function return types too, not just top-level function parameters.

## How do I fix a skipped `@Prev`?

In rough order of preference:

1. **Give the parameter a default value** in the original composable, if that's reasonable for your
   use case — an unsupported parameter with a default is simply omitted from the generated call.
2. **Restructure the type** to fit an existing supported shape (e.g. flatten deep nesting, avoid star
   projections by specifying a concrete type argument).
3. **Add a new `MockGenerator`** if the type shape is genuinely something PrevHam should support — see
   [`extending-mock-generators.md`](extending-mock-generators.md).

## Why did an old generated Preview file disappear after I renamed/deleted a composable?

That's expected, and intentional — see
[`ksp-processing.md`](ksp-processing.md#incremental-compilation) for how KSP's incremental compilation
tracks and prunes stale generated outputs.
