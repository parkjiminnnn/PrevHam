package io.github.parkjiminnnn.compiler.testing

import com.tschuchort.compiletesting.SourceFile

// `compiler` deliberately has no compile dependency on `runtime` or any AndroidX artifact (see
// docs/architecture.md), and AndroidX Compose artifacts are AAR-packaged, not resolvable on a
// plain JVM test classpath. These stubs stand in for the real @Prev/@Composable/@Preview/
// Configuration/Modifier declarations by fully-qualified name only, which is all PrevSymbolProcessor
// and the mock generators ever look at.
internal object Stubs {
    val prev =
        SourceFile.kotlin(
            "Prev.kt",
            """
            package io.github.parkjiminnnn.runtime

            annotation class Prev(
                val darkMode: Boolean = false,
                val locales: Array<String> = [],
                val fontScales: FloatArray = [],
            )
            """,
        )

    val composable =
        SourceFile.kotlin(
            "Composable.kt",
            """
            package androidx.compose.runtime

            annotation class Composable
            """,
        )

    val preview =
        SourceFile.kotlin(
            "Preview.kt",
            """
            package androidx.compose.ui.tooling.preview

            @Repeatable
            annotation class Preview(
                val name: String = "",
                val uiMode: Int = 0,
                val locale: String = "",
                val fontScale: Float = 1f,
            )
            """,
        )

    val configuration =
        SourceFile.kotlin(
            "Configuration.kt",
            """
            package android.content.res

            class Configuration {
                companion object {
                    const val UI_MODE_NIGHT_YES: Int = 0x20
                }
            }
            """,
        )

    // Mirrors androidx.compose.ui.Modifier's real shape: an interface with a companion object
    // that implements the interface itself, used to test InterfaceMockGenerator's preference for
    // a real self-implementing companion instance over a MockK mock.
    val modifier =
        SourceFile.kotlin(
            "Modifier.kt",
            """
            package androidx.compose.ui

            interface Modifier {
                companion object : Modifier
            }
            """,
        )

    // kotlinx.coroutines and MockK need no stubs: both are real dependencies of this module's
    // tests, so inheritClassPath makes them resolvable in compiled sources and in the code KSP
    // generates from them.
    val all = listOf(prev, composable, preview, configuration, modifier)
}
