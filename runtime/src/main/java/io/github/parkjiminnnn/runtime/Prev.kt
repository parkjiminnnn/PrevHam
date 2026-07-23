package io.github.parkjiminnnn.runtime

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class Prev(
    val darkMode: Boolean = false,
    val locales: Array<String> = [],
    val fontScales: FloatArray = [],
)
