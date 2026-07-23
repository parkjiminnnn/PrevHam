package io.github.parkjiminnnn.compiler.mock

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCompilerApi::class)
class EnumMockGeneratorTest {
    @Test
    fun `generates a reference to the first declared enum entry`() {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "StatusCard.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    enum class Status { ACTIVE, INACTIVE, PENDING }

                    @Prev
                    @Composable
                    fun StatusCard(status: Status) {}
                    """,
                ),
            )

        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        val generated = requireNotNull(result.generatedFile("StatusCardPreview.kt"))
        assertTrue(generated.contains("status = Status.ACTIVE"))
    }
}
