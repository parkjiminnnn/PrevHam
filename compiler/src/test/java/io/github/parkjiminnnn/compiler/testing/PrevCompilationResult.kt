package io.github.parkjiminnnn.compiler.testing

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import io.github.parkjiminnnn.compiler.PrevSymbolProcessorProvider
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

@OptIn(ExperimentalCompilerApi::class)
internal class PrevCompilationResult(
    val result: JvmCompilationResult,
    private val generatedFilesByName: Map<String, String>,
) {
    val exitCode get() = result.exitCode
    val messages get() = result.messages

    /** The text content of a KSP-generated file (e.g. "UserCardPreview.kt"), or null if it wasn't generated. */
    fun generatedFile(fileName: String): String? = generatedFilesByName[fileName]
}

/**
 * Compiles [sources] together with stubs for @Prev/@Composable/@Preview/Configuration/Modifier
 * (see [Stubs]) through the real [PrevSymbolProcessorProvider], mirroring how `sample` is used
 * manually to verify KSP output during development.
 */
@OptIn(ExperimentalCompilerApi::class)
internal fun compilePrev(
    vararg sources: SourceFile,
    options: Map<String, String> = emptyMap(),
): PrevCompilationResult {
    val compilation =
        KotlinCompilation().apply {
            this.sources = Stubs.all + sources.toList()
            inheritClassPath = true
            configureKsp {
                symbolProcessorProviders.add(PrevSymbolProcessorProvider())
                processorOptions.putAll(options)
            }
        }
    val result = compilation.compile()
    val generatedFilesByName =
        result.sourcesGeneratedBySymbolProcessor
            .filter { it.extension == "kt" }
            .associate { it.name to it.readText() }
    return PrevCompilationResult(result, generatedFilesByName)
}
