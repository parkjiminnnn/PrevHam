package io.github.parkjiminnnn.compiler.codegen

import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier

internal object PreviewFileGenerator {
    private val COMPOSABLE = ClassName("androidx.compose.runtime", "Composable")
    private val PREVIEW = ClassName("androidx.compose.ui.tooling.preview", "Preview")

    fun generate(
        function: KSFunctionDeclaration,
        arguments: Map<String, CodeBlock>,
    ): FileSpec {
        val functionName = function.simpleName.asString()
        val previewName = "${functionName}Preview"
        val packageName = function.packageName.asString()

        val previewFunction =
            FunSpec
                .builder(previewName)
                .addModifiers(KModifier.PRIVATE)
                .addAnnotation(PREVIEW)
                .addAnnotation(COMPOSABLE)
                .addCode(buildNamedArgumentsCall(functionName, arguments))
                .addCode("\n")
                .build()

        return FileSpec
            .builder(packageName, previewName)
            .addFunction(previewFunction)
            .build()
    }
}
