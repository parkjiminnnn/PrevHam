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

- **Types nested more than 3 levels deep.** `MockGeneratorRegistry.default()` bounds recursion at
  `MAX_DEPTH = 3` specifically to avoid a `StackOverflowError` on self-referential or very deeply
  nested types (`data class Node(val next: Node?)`, `List<List<List<List<Int>>>>`, ...). A type that's
  still "container-shaped" (a data class, collection, or function type) at that depth is treated as
  unsupported. See [`mock-generation.md`](mock-generation.md#depth-limited-recursion) for why this is
  a structural limit rather than a special case.
- **Generic types with unresolvable type arguments**, e.g. a star projection like `Repository<*>`.
  `InterfaceMockGenerator` can't determine a concrete type to write inside `mockk<...>()` for these,
  so it reports the type as unsupported rather than emitting code that wouldn't compile.
- **A `@Prev` function whose own parameter type is a raw, unbound type parameter** — i.e. a generic
  `@Composable` function itself (as opposed to a non-generic function that merely *uses* a generic
  type like `Box<String>`, which is fully supported). This is a deliberate scope decision, not a
  current gap expected to close soon.

## Does mocking a `sealed interface`/`sealed class` break an exhaustive `when`?

No. It's a reasonable worry — a naive proxy-based mock could produce a value whose runtime type isn't
any of the sealed hierarchy's declared subtypes, which would make an otherwise-exhaustive `when` throw
at runtime. This was verified empirically against MockK's actual behavior: `mockk<T>(relaxed = true)`
on a sealed type falls back to instantiating a **real concrete subtype** (via objenesis, with default
field values), not a synthetic unknown type. Exhaustive `when` matching over the mocked value works
normally.

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
