package io.github.parkjiminnnn.compiler.codegen

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import io.github.parkjiminnnn.compiler.PreviewOptions
import io.github.parkjiminnnn.compiler.PreviewSettings

internal object PreviewFileGenerator {
    private val COMPOSABLE = ClassName("androidx.compose.runtime", "Composable")
    private val PREVIEW = ClassName("androidx.compose.ui.tooling.preview", "Preview")
    private val CONFIGURATION = ClassName("android.content.res", "Configuration")
    private val WALLPAPERS = ClassName("androidx.compose.ui.tooling.preview", "Wallpapers")
    private val WALLPAPER_CONSTANTS =
        mapOf(
            0 to "RED_DOMINATED_EXAMPLE",
            1 to "GREEN_DOMINATED_EXAMPLE",
            2 to "BLUE_DOMINATED_EXAMPLE",
            3 to "YELLOW_DOMINATED_EXAMPLE",
        )

    fun generate(
        function: KSFunctionDeclaration,
        arguments: Map<String, CodeBlock>,
        options: PreviewOptions,
    ): FileSpec {
        val functionName = function.simpleName.asString()
        val previewName = "${functionName}Preview"
        val packageName = function.packageName.asString()

        val previewFunction =
            FunSpec
                .builder(previewName)
                .addModifiers(KModifier.PRIVATE)
                .addAnnotations(previewAnnotations(options))
                .addAnnotation(COMPOSABLE)
                .addCode(buildNamedArgumentsCall(functionName, arguments))
                .addCode("\n")
                .build()

        return FileSpec
            .builder(packageName, previewName)
            .addFunction(previewFunction)
            .build()
    }

    // One @Preview per requested variant, plus the default. Compose's @Preview is @Repeatable, so
    // they stack on the single generated function rather than needing one wrapper each.
    private fun previewAnnotations(options: PreviewOptions): List<AnnotationSpec> {
        val variants = mutableListOf<PreviewVariant>()
        variants += PreviewVariant(label = null)

        if (options.darkMode) {
            variants += PreviewVariant("Dark Mode") { addMember("uiMode = %T.UI_MODE_NIGHT_YES", CONFIGURATION) }
        }
        options.locales.forEach { locale ->
            variants += PreviewVariant("Locale: $locale") { addMember("locale = %S", locale) }
        }
        options.fontScales.forEach { scale ->
            variants += PreviewVariant("Font scale: ${scale}x") { addMember("fontScale = %Lf", scale) }
        }
        options.devices.forEach { device ->
            variants += PreviewVariant("Device: $device") { addMember("device = %S", device) }
        }

        return variants.map { variant -> variant.toAnnotation(options.settings) }
    }

    private class PreviewVariant(
        val label: String?,
        val addVariantMembers: AnnotationSpec.Builder.() -> Unit = {},
    )

    private fun PreviewVariant.toAnnotation(settings: PreviewSettings): AnnotationSpec =
        AnnotationSpec
            .builder(PREVIEW)
            .apply {
                previewName(label, settings.name)?.let { addMember("name = %S", it) }
                addVariantMembers()
                addSettings(settings)
            }.build()

    // A user-supplied name becomes the variants' common prefix rather than replacing their labels,
    // so "Card", "Card - Dark Mode" and "Card - Locale: ko" all stay distinguishable in the IDE.
    private fun previewName(
        label: String?,
        configuredName: String,
    ): String? =
        when {
            configuredName.isEmpty() -> label
            label == null -> configuredName
            else -> "$configuredName - $label"
        }

    // Only what differs from @Preview's own defaults is written out, so a bare @Prev still produces
    // a bare @Preview.
    private fun AnnotationSpec.Builder.addSettings(settings: PreviewSettings) {
        if (settings.group.isNotEmpty()) addMember("group = %S", settings.group)
        if (settings.apiLevel != -1) addMember("apiLevel = %L", settings.apiLevel)
        if (settings.widthDp != -1) addMember("widthDp = %L", settings.widthDp)
        if (settings.heightDp != -1) addMember("heightDp = %L", settings.heightDp)
        if (settings.showSystemUi) addMember("showSystemUi = true")
        if (settings.showBackground) addMember("showBackground = true")
        if (settings.backgroundColor != 0L) addMember("backgroundColor = %L", settings.backgroundColor.asColorLiteral())
        // PrevHam's Wallpapers constants mirror Compose's names and values, so the value maps
        // straight across - written as the named constant, since a bare 2 says nothing to a reader.
        WALLPAPER_CONSTANTS[settings.wallpaper]?.let { addMember("wallpaper = %T.%L", WALLPAPERS, it) }
    }

    // Colours are written as 0xAARRGGBB in source, so echo them back that way instead of as the
    // decimal the annotation argument arrives as.
    private fun Long.asColorLiteral(): String = if (this < 0) "${this}L" else "0x%XL".format(this)
}
