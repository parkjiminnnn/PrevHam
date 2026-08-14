package io.github.parkjiminnnn.runtime

/**
 * Marks a `@Composable` function for compile-time Preview generation.
 *
 * PrevHam's KSP processor analyzes the annotated function's parameters, generates a
 * compile-time-safe mock value for each supported type, and emits a `@Preview @Composable`
 * wrapper function that calls the original composable with those mocks.
 *
 * The composable has to be a top-level function that isn't `private`: the Preview is generated into
 * a separate file, which can only call declarations it can reach. `internal` is fine.
 *
 * ```kotlin
 * @Prev(darkMode = true, locales = ["ko", "en"], showBackground = true)
 * @Composable
 * fun UserCard(user: User) { ... }
 * ```
 *
 * Parameters come in two kinds.
 *
 * **Variants** — [darkMode], [locales], [fontScales], and [devices] each add stacked `@Preview`
 * annotations alongside the default one, so a single `@Prev` can render the composable several
 * ways. Compose's `@Preview` is `@Repeatable`, so they all land on one generated function.
 *
 * **Settings** — everything else describes *how* to render rather than *what* to render, and is
 * applied to every generated `@Preview`, variants included. Each mirrors the `@Preview` parameter of
 * the same name and is left out of the generated code entirely when kept at its default.
 *
 * @param darkMode when `true`, adds a Preview variant rendered in dark mode
 * (`uiMode = Configuration.UI_MODE_NIGHT_YES`).
 * @param locales adds one Preview variant per locale tag (e.g. `"ko"`, `"en"`), rendered with that
 * locale applied.
 * @param fontScales adds one Preview variant per font scale factor (e.g. `0.85f`, `1.5f`), rendered
 * with that font scale applied.
 * @param devices adds one Preview variant per device, rendered on that device. [Devices] has a
 * constant for each device Compose names, and any other spec string works too.
 * @param name names the Preview. With variants, it becomes their common prefix
 * (`"Card - Dark Mode"`), so each stays distinguishable in the IDE.
 * @param group groups this composable's Previews under a label, which the IDE can filter by.
 * @param apiLevel renders at a specific API level instead of the default.
 * @param widthDp fixes the Preview width in dp. Ignored by Compose when [devices] is used.
 * @param heightDp fixes the Preview height in dp. Ignored by Compose when [devices] is used.
 * @param showSystemUi when `true`, renders inside a device frame with status and navigation bars.
 * @param showBackground when `true`, renders on an opaque background rather than a transparent one.
 * @param backgroundColor the background to render on, as `0xAARRGGBB`. Takes effect only together
 * with [showBackground].
 * @param wallpaper the dynamic-colour wallpaper to derive theming from, as a [Wallpapers] constant.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class Prev(
    // Variants: each value adds one more stacked @Preview.
    val darkMode: Boolean = false,
    val locales: Array<String> = [],
    val fontScales: FloatArray = [],
    val devices: Array<String> = [],
    // Settings: applied to every generated @Preview. Defaults mirror @Preview's own, so anything
    // left alone here is left out of the generated annotation too.
    val name: String = "",
    val group: String = "",
    val apiLevel: Int = -1,
    val widthDp: Int = -1,
    val heightDp: Int = -1,
    val showSystemUi: Boolean = false,
    val showBackground: Boolean = false,
    val backgroundColor: Long = 0,
    val wallpaper: Int = Wallpapers.NONE,
)
