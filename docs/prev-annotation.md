# The `@Prev` Annotation

`@Prev` marks a `@Composable` for compile-time Preview generation. PrevHam reads the function's
signature, builds a mock value for each parameter, and writes a `@Preview @Composable` wrapper that
calls it — see [mock-generation.md](mock-generation.md) for how the values are built, and
[ksp-processing.md](ksp-processing.md) for how the file is produced.

```kotlin
import io.github.parkjiminnnn.runtime.Prev

@Prev
@Composable
fun UserCard(user: User, onClick: () -> Unit) { /* ... */ }
```

```kotlin
@Preview
@Composable
private fun UserCardPreview() {
    UserCard(
        user = User(id = 1, name = "mock", age = 1),
        onClick = { },
    )
}
```

## Where it can go

The composable has to be a **top-level function that isn't `private`**. The Preview is written into a
separate file, and KSP can only create new files — never add to an existing one — so anything that
file can't call has no Preview available to it:

| Declaration | Preview |
|---|---|
| top-level `public` / `internal` | generated |
| top-level `private` | rejected — a private top-level function is scoped to its own file |
| inside a `class` or `object` | rejected — the call would need the declaring type, and for a class an instance of it |

Rejection is a compile error naming the reason, not a silent skip. Keeping a composable `private` is
a perfectly good reason not to annotate it — writing a `@Preview` by hand in the same file is a
supported outcome. See the [FAQ](faq.md) for the full reasoning.

## Parameters

They come in two kinds, and the kind decides how they are generated.

### Variants — how many Previews

Each value adds one more stacked `@Preview`, alongside the default one. Compose's `@Preview` is
`@Repeatable`, so they all land on the single generated function.

| Parameter | Type | Adds |
|---|---|---|
| `darkMode` | `Boolean` | one Preview with `uiMode = UI_MODE_NIGHT_YES` |
| `locales` | `Array<String>` | one Preview per locale tag |
| `fontScales` | `FloatArray` | one Preview per font scale |
| `devices` | `Array<String>` | one Preview per device |

```kotlin
@Prev(darkMode = true, locales = ["ko", "en"], fontScales = [0.85f, 1.5f])
```

```kotlin
@Preview
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Locale: ko", locale = "ko")
@Preview(name = "Locale: en", locale = "en")
@Preview(name = "Font scale: 0.85x", fontScale = 0.85f)
@Preview(name = "Font scale: 1.5x", fontScale = 1.5f)
```

`devices` accepts anything Compose's `device` does. [`Devices`](#devices-and-wallpapers) has a
constant per named device, and a raw spec string works just as well:

```kotlin
@Prev(devices = [Devices.PIXEL_FOLD, "spec:width=900dp,height=1200dp"])
```

### Settings — how they render

These describe *how* to render rather than *what*, so they are applied to **every** generated
`@Preview`, variants included. Each mirrors the `@Preview` parameter of the same name, defaults
included — anything left alone is left out of the generated annotation entirely, so a bare `@Prev`
still produces a bare `@Preview`.

| Parameter | Type | Default |
|---|---|---|
| `name` | `String` | `""` |
| `group` | `String` | `""` |
| `apiLevel` | `Int` | `-1` |
| `widthDp` | `Int` | `-1` |
| `heightDp` | `Int` | `-1` |
| `showSystemUi` | `Boolean` | `false` |
| `showBackground` | `Boolean` | `false` |
| `backgroundColor` | `Long` | `0` |
| `wallpaper` | `Int` | `Wallpapers.NONE` |

```kotlin
@Prev(darkMode = true, group = "cards", showBackground = true)
```

```kotlin
@Preview(group = "cards", showBackground = true)
@Preview(
    name = "Dark Mode",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    group = "cards",
    showBackground = true,
)
```

`name` is the one that isn't a plain pass-through. It becomes the variants' **common prefix**, since
replacing their labels outright would leave several Previews sharing a single name:

```kotlin
@Prev(name = "Badge", darkMode = true)
```

```kotlin
@Preview(name = "Badge")
@Preview(name = "Badge - Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
```

### `Devices` and `Wallpapers`

`runtime` ships both, carrying the same names and values as Compose's, so `@Prev` reads the same as
the `@Preview` it generates. They are declared in PrevHam rather than reused from Compose because
`runtime` deliberately has no dependencies (see [architecture.md](architecture.md)) — the values are
plain `String`/`Int`, so Compose's own constants work interchangeably.

```kotlin
import io.github.parkjiminnnn.runtime.Devices
import io.github.parkjiminnnn.runtime.Wallpapers

@Prev(devices = [Devices.PIXEL_5], wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE)
```

Two values are reformatted on the way into the generated file rather than echoed as received, since
that file is meant to be read: `backgroundColor` back to `0xAARRGGBB` hex instead of the decimal the
annotation argument arrives as, and `wallpaper` to the named `Wallpapers` constant instead of a bare
small integer.

## Relationship to `@Preview`

`@Prev` covers every `@Preview` parameter, with three shaped differently:

| `@Preview` | `@Prev` |
|---|---|
| `locale`, `fontScale`, `device` | `locales`, `fontScales`, `devices` — plural, because `@Prev` isn't `@Repeatable` and arrays are how it expresses more than one |
| `uiMode` | `darkMode` only. Other `uiMode` values (TV, watch, automotive) need a hand-written `@Preview` |
| everything else | same name, same default |

## What isn't generated

A `@Prev` is skipped, with a warning naming the parameter, when a parameter's type has no mock
generator — see [the FAQ](faq.md#why-did-i-get-no-mock-generator-available-for-parameter-x). That is a
warning rather than an error, unlike the declaration rules above, because it's a gap PrevHam may
close later rather than something structurally impossible.
