# KSP Processing

This document walks through how `compiler` turns a `@Prev`-annotated function into a generated
Preview file, and the specific KSP APIs each step relies on.

## Entry point

KSP discovers processors via `SymbolProcessorProvider`, registered with `@AutoService` so it's picked
up without manual `META-INF/services` wiring:

```kotlin
@AutoService(SymbolProcessorProvider::class)
class PrevSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        PrevSymbolProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
        )
}
```

## Finding `@Prev`-annotated functions

`PrevSymbolProcessor.process(resolver: Resolver)` is KSP's per-round entry point:

```kotlin
override fun process(resolver: Resolver): List<KSAnnotated> {
    resolver
        .getSymbolsWithAnnotation(PREV_ANNOTATION_NAME)
        .filterIsInstance<KSFunctionDeclaration>()
        .forEach(::processFunction)
    return emptyList()
}
```

`getSymbolsWithAnnotation` takes the annotation's fully-qualified name as a `String`
(`"io.github.parkjiminnnn.runtime.Prev"`, defined once in `PrevAnnotation.kt` and shared across the
module) rather than a `KClass` reference — this is what lets `compiler` find `@Prev` usages without a
compile dependency on `runtime` (see [architecture.md](architecture.md)). The result is filtered to
`KSFunctionDeclaration` since `@Prev`'s `@Target` restricts it to functions.

Returning `emptyList()` tells KSP there are no deferred symbols to retry in a later round — PrevHam
never needs multi-round resolution, since everything it needs (parameter types, the annotation's own
arguments) is resolvable in a single pass.

## Processing a single function

```kotlin
private fun processFunction(function: KSFunctionDeclaration) {
    if (!function.isComposable()) { /* log error, skip */ return }
    if (function.uncallableFromGeneratedFileReason() != null) { /* log error, skip */ return }

    val arguments = buildMockArguments(function) ?: return
    val options = function.previewOptions()
    val fileSpec = PreviewFileGenerator.generate(function, arguments, options)

    codeGenerator
        .createNewFile(
            dependencies = Dependencies(aggregating = false, function.containingFile!!),
            packageName = fileSpec.packageName,
            fileName = fileSpec.name,
        ).bufferedWriter()
        .use { writer -> fileSpec.writeTo(writer) }
}
```

Two checks run before anything is generated, then three pieces of information are gathered:

1. **`isComposable()`** — checks `function.annotations` for `androidx.compose.runtime.Composable`,
   again by qualified name string. `@Prev` on a non-`@Composable` function is a hard error, not a
   silent skip, since it's very likely a mistake.
1. **`uncallableFromGeneratedFileReason()`** — checks that a *separate* file could call the
   function at all: `functionKind` must be `TOP_LEVEL`, and `getVisibility()` must not be
   `PRIVATE`. The Preview always goes into a new file, so a member function (no receiver, and for a
   class member no instance) or a file-private one can never be called from it.

   This is also a hard error. Unlike an unsupported parameter type, which is a gap PrevHam might
   close later, this one is structural — `CodeGenerator` has no API to add to an existing file, so
   no future version can make it work. Without the check the file is written anyway and then fails
   to compile, with an error that never mentions PrevHam.
2. **`arguments: Map<String, CodeBlock>`** — one mock value per parameter of the annotated function,
   built by the mock generator pipeline (see [mock-generation.md](mock-generation.md)).
3. **`options: PreviewOptions`** — read from `@Prev`'s own arguments, covered below.

### Reading `@Prev`'s own arguments

Every other part of `compiler` reads the *types of the annotated function's parameters*. Reading
`@Prev`'s own arguments is a different KSP API: it means reading the *arguments of the annotation
instance*, via `KSAnnotation.arguments`:

```kotlin
internal fun KSFunctionDeclaration.previewOptions(): PreviewOptions {
    val prevAnnotation = annotations.first {
        it.annotationType.resolve().declaration.qualifiedName?.asString() == PREV_ANNOTATION_NAME
    }
    val argumentsByName = prevAnnotation.arguments.associateBy { it.name?.asString() }
    return PreviewOptions(
        darkMode = boolean("darkMode", default = false),
        locales = argumentsByName["locales"]?.value.asList(),
        // ...
        settings = PreviewSettings(/* name, group, apiLevel, widthDp, ... */),
    )
}
```

- `function.annotations` is the list of `KSAnnotation`s on the function (`@Prev`, `@Composable`, ...);
  it's filtered down to the one matching `@Prev`'s qualified name.
- `KSAnnotation.arguments` is `List<KSValueArgument>`. Each entry's `.value` is already resolved to a
  plain Kotlin runtime value by KSP — `Boolean` for `darkMode`, `List<*>` for the array-typed
  `locales`/`fontScales`/`devices` (confirmed empirically; KSP normalizes both `Array<String>` and
  `FloatArray` annotation members to `List<*>` here, not to `Array`/`FloatArray`).
- Default values declared on `@Prev` itself (`darkMode: Boolean = false`, etc.) are filled in by KSP
  even when the call site omits them, so `arguments` always has every entry. An omitted argument and
  an explicitly-default one are therefore indistinguishable — which is fine, since both mean "leave
  it out of the generated annotation".

### Variants and settings

`PreviewOptions` splits `@Prev`'s parameters the way the generated annotations are built:

- **Variants** (`darkMode`, `locales`, `fontScales`, `devices`) decide *how many* `@Preview`
  annotations there are. Each value adds one, alongside the default.
- **Settings** (`name`, `group`, `apiLevel`, `widthDp`, `heightDp`, `showSystemUi`,
  `showBackground`, `backgroundColor`, `wallpaper`) describe *how* to render rather than what, so
  `PreviewFileGenerator` applies them to every generated annotation, variants included.

Each setting's default mirrors the corresponding `@Preview` parameter's default, which is what lets
the generator write out only what actually differs — a bare `@Prev` still produces a bare
`@Preview`. Two values are reformatted on the way out rather than echoed as received:
`backgroundColor` back to `0xAARRGGBB` hex, and `wallpaper` from the `Int` it arrives as to the
named `Wallpapers.*` constant, since a bare `2` says nothing to a reader. PrevHam declares its own
`Wallpapers` in `runtime` with the same names and values as Compose's, so `@Prev` reads the same as
the `@Preview` it generates without `runtime` taking on a dependency.

`name` is the one setting that isn't a plain pass-through: it becomes the variants' common prefix
(`"Card - Dark Mode"`), since replacing their labels outright would leave several Previews sharing a
single name.

## Reporting on the round

Three things are said once every round has run, not per round and not where they happen:

```kotlin
override fun finish() {
    writeSlotManifest()
    warnAboutUndecidedSlots()
    reportRound()
}

// KSP calls this instead of finish() when the round reported an error.
override fun onError() {
    reportRound()
}
```

`finish()` is the only place with a complete picture. A manifest rewritten per round is incomplete
until the last one; a missing-value warning raised per round names slots a later round goes on to
decide; and a per-round summary splits one project across several lines that each look like the whole
picture.

`onError()` matters more than it looks. KSP calls it **instead of** `finish()` when anything reported
an error, so a summary written only in `finish()` never appears on the builds where a count is most
worth having — the ones with a broken `@Prev` in them. Only the summary is repeated there: the
manifest and the missing-value warning describe a compilation that produced Previews, and that one
did not.

### Why skips are gathered rather than warned about in place

An unsupported parameter used to log a warning at the point it was found. It now records into a tally
that `RoundReport` formats:

```kotlin
tally.skipped(
    name = function.simpleName.asString(),
    reason = "no mock generator available for parameter '${unsupported.name}'",
)
```

One warning per skip, scattered through however many hundred lines of unrelated output a build
produces, is easy to miss and impossible to count. Nothing is lost by moving them: KSP prints these
without a source location either way, so the standalone warnings pointed at nothing the reader could
click.

The summary is a warning when something was skipped or failed, and `info` otherwise. KSP's logger has
no level between the two, and a build that produced every Preview it was asked for has nothing wrong
with it to warn about.

## Generic type resolution: `resolve()` vs. `asMemberOf()`

Parameter types are read via `KSValueParameter.type.resolve() : KSType`. For a non-generic parameter
this is sufficient. It breaks down for generic types, though:

```kotlin
data class Box<T>(val value: T)

@Prev
@Composable
fun BoxCard(box: Box<String>) { ... }
```

`box`'s declared type resolves fine to `Box<String>`. But `DataClassMockGenerator` needs to build a
mock instance of `Box`, which means it needs the type of `Box`'s **primary constructor parameter**
(`value: T`) — and `KSValueParameter.type.resolve()` on that constructor parameter, taken in isolation,
resolves `T` to itself (a `KSTypeParameter`, not a concrete `KSType` like `String`). `resolve()` has no
notion of "as used at this call site" — it only knows the declaration it's attached to.

`KSFunctionDeclaration.asMemberOf(containingType: KSType)` is the API that closes this gap: given the
*specific* `KSType` the member is being viewed through (here, `Box<String>`), it returns a `KSFunction`
whose `parameterTypes` have type parameters substituted with their actual arguments:

```kotlin
private fun KSType.substitutedConstructorParameters(): List<MockParameter>? {
    val declaration = declaration as? KSClassDeclaration ?: return null
    if (Modifier.DATA !in declaration.modifiers) return null
    val constructor = declaration.primaryConstructor ?: return null
    val substitutedTypes = constructor.asMemberOf(this).parameterTypes
    return constructor.parameters.zip(substitutedTypes).map { (parameter, type) ->
        parameter.toMockParameter(type ?: return null) ?: return null
    }
}
```

`this` here is the `Box<String>` `KSType` seen at the call site — `constructor.asMemberOf(this)`
substitutes `T → String` for that specific usage, so `parameterTypes[0]` comes back as `String`, not
`T`. This is why `MockParameter` (the DTO passed around the mock generator pipeline) carries an
explicit `type: KSType` field decoupled from the parameter's own declaration, with
`toMockParameter(type: KSType = this.type.resolve())` defaulting to plain `resolve()` for the common
non-generic case while still allowing an `asMemberOf`-substituted type to be injected.

The same pattern is what makes `Box<String>`, `Repository<String>` (a generic interface), and nested
cases like `Repository<List<Int>>` all resolve correctly — see [mock-generation.md](mock-generation.md)
for how each generator consumes the substituted type.

## Incremental compilation

Each generated file is registered with an explicit `Dependencies`:

```kotlin
Dependencies(aggregating = false, function.containingFile!!)
```

This tells KSP's incremental compiler: "this generated file depends on `function`'s source file; if
that source file changes, invalidate and regenerate this output." Verified empirically against three
scenarios:

- **Parameter change** — editing a `@Prev` function's signature and recompiling regenerates only that
  function's Preview file with the new mock arguments.
- **Function rename** — renaming the annotated function causes the *old* generated
  `<OldName>Preview.kt` to be removed and a new `<NewName>Preview.kt` to be created, rather than both
  existing.
- **Function/annotation removal** — deleting the function, or just removing `@Prev` from it, removes
  the previously generated Preview file.

Cleanup on rename/delete/annotation-removal comes from registering the dependency at all (KSP tracks
"this output came from this input" and prunes outputs whose input no longer produces them) — it isn't
specific to the `aggregating` flag's value. `aggregating` controls invalidation *granularity*: `false`
("isolating") means only symbols in the same file as a change are reprocessed; `true` ("aggregating")
means any change anywhere can force reprocessing. Since each `@Prev` function's mock generation only
ever depends on symbols reachable from its own containing file, `aggregating = false` gives the more
precise (and cheaper) invalidation scope without losing correctness.
