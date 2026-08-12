# Extending: Adding a New Mock Generator

PrevHam's mock generation is deliberately built as a set of small, independent strategies rather than
one large branching function — see [`mock-generation.md`](mock-generation.md) for the full design.
This guide walks through what's involved in adding support for a new parameter type shape.

## 1. Implement `MockGenerator`

Every generator lives in `compiler/src/main/java/io/github/parkjiminnnn/compiler/mock/` and implements:

```kotlin
internal interface MockGenerator {
    fun supports(type: KSType, context: MockContext): Boolean
    fun generate(type: KSType, context: MockContext): CodeBlock
}
```

- `supports(type, context)` must be a pure check — no side effects, safe to call speculatively. Match
  on `type.declaration.qualifiedName?.asString()` for a specific known type (see
  `StringMockGenerator`), or on structural shape (`ClassKind`, `Modifier.DATA`, `type.arguments`, ...)
  for a family of types (see `EnumMockGenerator`, `DataClassMockGenerator`).
- `generate(type, context)` is only ever called after `supports` returned `true` for the same
  arguments — it can assume that precondition and doesn't need to re-validate it.
- If your generator needs to recurse into other types (a field type, a collection element type, a
  function's return type), go through the context — `context.canMock(inner)` in `supports`, and
  `context.mock(inner)` in `generate` — rather than hardcoding a specific generator. See
  `DataClassMockGenerator`, `CollectionMockGenerator`, and `FunctionTypeMockGenerator` for the
  pattern. The context carries the types already being expanded on this path, so going through it is
  what keeps your generator from recursing forever on a self-referential type.
- Check `canMock` before calling `mock`, and honour a `false`. On a blocked context `canMock` returns
  `false` for everything, which is how recursion stops; a generator that expands nothing (a literal,
  a bare mock, `null`) can still answer there, and should.

## 2. Register it in `MockGeneratorRegistry.default()`

```kotlin
fun default(): MockGeneratorRegistry =
    MockGeneratorRegistry(
        listOf(
            PrimitiveMockGenerator(),
            StringMockGenerator(),
            EnumMockGenerator(),
            SealedTypeMockGenerator(),
            DataClassMockGenerator(),
            CollectionMockGenerator(),
            FunctionTypeMockGenerator(),
            InterfaceMockGenerator(),
            NullableFallbackMockGenerator(),
        ),
    )
```

Every generator is available at every point in the recursion — the bound lives in `MockContext`, not
in this list — so there is only one decision to make here:

- **Where in the list?** `MockGeneratorRegistry.supports`/`generate` resolve by **list order** —
  the first generator whose `supports` matches wins (`any { }`/`first { }` short-circuiting). Put
  narrow, specific-type generators before broad, structural ones. Concretely:
  `InterfaceMockGenerator` matches *any* interface or non-data class, so `List`, `Set`, `Map`, and
  function types (which are also Kotlin interfaces) must be explicitly excluded from it — see
  `isOwnedByAnotherGenerator()` in `InterfaceMockGenerator.kt` and the shared qualified-name constants
  in `KotlinTypeNames.kt`. If your new generator's target type could also match an existing broad
  generator, either place it earlier in the list or add a similar exclusion.
- **`NullableFallbackMockGenerator` must stay last.** It matches any nullable type, so it should only
  ever be reached once nothing more specific has claimed the type.

## 3. Verify with a real KSP build

Don't reason about KSP/KotlinPoet behavior in the abstract — add a `@Prev`-annotated composable in
`sample` that exercises the new type, then inspect the actual generated output:

```bash
./gradlew :sample:kspDebugKotlin
cat sample/build/generated/ksp/debug/kotlin/io/github/parkjiminnnn/prevham/<YourComposable>Preview.kt
./gradlew :sample:compileDebugKotlin   # confirm the generated code actually compiles
```

This project's history has repeatedly turned up KSP/KotlinPoet behavior that doesn't match intuition
(e.g. `KSAnnotation` array arguments surfacing as `List<*>` rather than `Array`/primitive array types,
or a raw `ClassName` silently dropping generic type arguments in `%T`) — a real build output is the
only reliable way to confirm a generator does what you expect.

## Common pitfalls

- **Star projections / unresolvable type arguments.** `InterfaceMockGenerator.toTypeName()` returns
  `null` for anything it can't fully resolve (e.g. `Repository<*>`), which makes `supports()` return
  `false` for that case rather than emitting code that doesn't compile. If your generator handles
  generic types, follow the same pattern: fail `supports()` cleanly instead of generating broken code.
- **Self-referential or deeply nested types.** If your generator recurses, it participates in the
  depth-limited registry chain automatically as long as it takes a `MockGeneratorRegistry` and
  delegates to it — don't add your own ad hoc recursion guard.
- **Parameters with default values.** `firstUnsupportedParameter` in `MockArguments.kt` already skips
  parameters that have a default value even when unsupported, since the generated call can omit them.
  A new generator doesn't need to special-case this itself.
