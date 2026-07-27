package io.github.parkjiminnnn.runtime

/**
 * Marks a `@Composable` function for compile-time Preview generation.
 *
 * PrevHam's KSP processor analyzes the annotated function's parameters, generates a
 * compile-time-safe mock value for each supported type, and emits a `@Preview @Composable`
 * wrapper function that calls the original composable with those mocks.
 *
 * A bare `@Prev` generates a single default `@Preview`. [darkMode], [locales], and [fontScales]
 * each add one additional stacked `@Preview` per requested variant, alongside the default.
 *
 * ```kotlin
 * @Prev(darkMode = true, locales = ["ko", "en"], fontScales = [0.85f, 1.5f])
 * @Composable
 * fun UserCard(user: User) { ... }
 * ```
 *
 * @param darkMode when `true`, adds a Preview variant rendered in dark mode
 * (`uiMode = Configuration.UI_MODE_NIGHT_YES`).
 * @param locales adds one Preview variant per locale tag (e.g. `"ko"`, `"en"`), rendered with that
 * locale applied.
 * @param fontScales adds one Preview variant per font scale factor (e.g. `0.85f`, `1.5f`), rendered
 * with that font scale applied.
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class Prev(
    val darkMode: Boolean = false,
    val locales: Array<String> = [],
    val fontScales: FloatArray = [],
)
