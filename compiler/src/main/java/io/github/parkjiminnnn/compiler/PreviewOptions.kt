package io.github.parkjiminnnn.compiler

import com.google.devtools.ksp.symbol.KSFunctionDeclaration

/**
 * What `@Prev` asked for, split the way the generated annotations are built.
 *
 * [variants] decide *how many* `@Preview` annotations there are; [settings] apply to all of them.
 */
internal data class PreviewOptions(
    val darkMode: Boolean,
    val locales: List<String>,
    val fontScales: List<Float>,
    val devices: List<String>,
    val settings: PreviewSettings,
)

/**
 * The `@Prev` parameters that describe how to render rather than what to render.
 *
 * Every default here mirrors the corresponding `@Preview` parameter's default, so a value left
 * untouched can be recognised and left out of the generated annotation.
 */
internal data class PreviewSettings(
    val name: String,
    val group: String,
    val apiLevel: Int,
    val widthDp: Int,
    val heightDp: Int,
    val showSystemUi: Boolean,
    val showBackground: Boolean,
    val backgroundColor: Long,
    val wallpaper: Int,
)

internal fun KSFunctionDeclaration.previewOptions(): PreviewOptions {
    val prevAnnotation =
        annotations.first {
            it.annotationType
                .resolve()
                .declaration.qualifiedName
                ?.asString() == PREV_ANNOTATION_NAME
        }
    val argumentsByName = prevAnnotation.arguments.associateBy { it.name?.asString() }

    // KSP hands back the argument's declared default when the call site omits it, so a missing
    // entry and an explicitly-default one are indistinguishable here - which is fine, since both
    // mean "leave it out of the generated annotation".
    fun boolean(
        name: String,
        default: Boolean,
    ) = argumentsByName[name]?.value as? Boolean ?: default

    fun int(
        name: String,
        default: Int,
    ) = argumentsByName[name]?.value as? Int ?: default

    fun string(name: String) = argumentsByName[name]?.value as? String ?: ""

    return PreviewOptions(
        darkMode = boolean("darkMode", default = false),
        locales = argumentsByName["locales"]?.value.asList(),
        fontScales = argumentsByName["fontScales"]?.value.asList(),
        devices = argumentsByName["devices"]?.value.asList(),
        settings =
            PreviewSettings(
                name = string("name"),
                group = string("group"),
                apiLevel = int("apiLevel", default = -1),
                widthDp = int("widthDp", default = -1),
                heightDp = int("heightDp", default = -1),
                showSystemUi = boolean("showSystemUi", default = false),
                showBackground = boolean("showBackground", default = false),
                backgroundColor = argumentsByName["backgroundColor"]?.value as? Long ?: 0L,
                wallpaper = int("wallpaper", default = WALLPAPER_NONE),
            ),
    )
}

private const val WALLPAPER_NONE = -1

private inline fun <reified T> Any?.asList(): List<T> = (this as? List<*>)?.filterIsInstance<T>().orEmpty()
