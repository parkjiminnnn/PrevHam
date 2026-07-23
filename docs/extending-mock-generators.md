# Extending: Adding a New Mock Generator

PrevHam's mock generation is deliberately built as a set of small, independent strategies rather than
one large branching function — see [`mock-generation.md`](mock-generation.md) for the full design.
This guide walks through what's involved in adding support for a new parameter type shape.

## 1. Implement `MockGenerator`

Every generator lives in `compiler/src/main/java/io/github/parkjiminnnn/compiler/mock/` and implements:

```kotlin
internal interface MockGenerator {
    fun supports(type: KSType): Boolean
    fun generate(type: KSType): CodeBlock
}
```

- `supports(type)` must be a pure check — no side effects, safe to call speculatively. Match on
  `type.declaration.qualifiedName?.asString()` for a specific known type (see
  `StringMockGenerator`), or on structural shape (`ClassKind`, `Modifier.DATA`, `type.arguments`, ...)
  for a family of types (see `EnumMockGenerator`, `DataClassMockGenerator`).
- `generate(type)` is only ever called after `supports(type)` returned `true` for the same `type` —
  it can assume that precondition and doesn't need to re-validate it.
- If your generator needs to recurse into other types (a field type, a collection element type, a
  function's return type), take a `MockGeneratorRegistry` as a constructor parameter and delegate to
  it rather than hardcoding a specific generator — see `DataClassMockGenerator`,
  `CollectionMockGenerator`, and `FunctionTypeMockGenerator` for the pattern. This is what makes the
  depth-limiting in `MockGeneratorRegistry.default()` work correctly for your generator too.

## 2. Register it in `MockGeneratorRegistry.default()`

```kotlin
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
```

Two decisions to make here:

- **Leaf or recursive?** If your generator never calls back into a registry to resolve an inner type,
  it's a leaf generator — add it to `leafGenerators`, so it's available even at `MAX_DEPTH`. If it
  does recurse, add it to `recursiveGenerators` (constructed with `nested`), so it's excluded once the
  depth limit is hit rather than risking unbounded recursion.
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
