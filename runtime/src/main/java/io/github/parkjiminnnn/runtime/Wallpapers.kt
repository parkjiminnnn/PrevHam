package io.github.parkjiminnnn.runtime

/**
 * Wallpapers a Preview can derive dynamic theming from, for [Prev.wallpaper].
 *
 * Names and values match `androidx.compose.ui.tooling.preview.Wallpapers` exactly, so `@Prev` reads
 * the same as the `@Preview` it generates. They are declared here rather than reused from Compose
 * because `runtime` deliberately carries no dependencies - see `docs/architecture.md`.
 */
object Wallpapers {
    /** Dynamic theming disabled. Left out of the generated `@Preview` entirely. */
    const val NONE: Int = -1

    /** Example wallpaper whose dominant colour is red. */
    const val RED_DOMINATED_EXAMPLE: Int = 0

    /** Example wallpaper whose dominant colour is green. */
    const val GREEN_DOMINATED_EXAMPLE: Int = 1

    /** Example wallpaper whose dominant colour is blue. */
    const val BLUE_DOMINATED_EXAMPLE: Int = 2

    /** Example wallpaper whose dominant colour is yellow. */
    const val YELLOW_DOMINATED_EXAMPLE: Int = 3
}
