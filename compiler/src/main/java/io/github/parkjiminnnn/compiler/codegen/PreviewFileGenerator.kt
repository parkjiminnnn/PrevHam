package io.github.parkjiminnnn.compiler.codegen

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import io.github.parkjiminnnn.compiler.PreviewOptions

internal object PreviewFileGenerator {
    private val COMPOSABLE = ClassName("androidx.compose.runtime", "Composable")
    private val PREVIEW = ClassName("androidx.compose.ui.tooling.preview", "Preview")
    private val CONFIGURATION = ClassName("android.content.res", "Configuration")

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

    private fun previewAnnotations(options: PreviewOptions): List<AnnotationSpec> {
        val annotations = mutableListOf(AnnotationSpec.builder(PREVIEW).build())

        if (options.darkMode) {
            annotations +=
                AnnotationSpec
                    .builder(PREVIEW)
                    .addMember("name = %S", "Dark Mode")
                    .addMember("uiMode = %T.UI_MODE_NIGHT_YES", CONFIGURATION)
                    .build()
        }

        options.locales.forEach { locale ->
            annotations +=
                AnnotationSpec
                    .builder(PREVIEW)
                    .addMember("name = %S", "Locale: $locale")
                    .addMember("locale = %S", locale)
                    .build()
        }

        options.fontScales.forEach { scale ->
            annotations +=
                AnnotationSpec
                    .builder(PREVIEW)
                    .addMember("name = %S", "Font scale: ${scale}x")
                    .addMember("fontScale = %Lf", scale)
                    .build()
        }

        return annotations
    }
}
