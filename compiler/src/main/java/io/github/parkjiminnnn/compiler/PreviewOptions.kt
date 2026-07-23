package io.github.parkjiminnnn.compiler

import com.google.devtools.ksp.symbol.KSFunctionDeclaration

internal data class PreviewOptions(
    val darkMode: Boolean,
    val locales: List<String>,
    val fontScales: List<Float>,
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
    return PreviewOptions(
        darkMode = argumentsByName["darkMode"]?.value as? Boolean ?: false,
        locales = argumentsByName["locales"]?.value.asList(),
        fontScales = argumentsByName["fontScales"]?.value.asList(),
    )
}

private inline fun <reified T> Any?.asList(): List<T> = (this as? List<*>)?.filterIsInstance<T>().orEmpty()
