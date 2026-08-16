package io.github.parkjiminnnn.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.parkjiminnnn.compiler.testing.compilePrev
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// @Prev's parameters come in two kinds: variants decide how many @Preview annotations there are,
// settings apply to all of them.
@OptIn(ExperimentalCompilerApi::class)
class PreviewOptionsTest {
    private fun generate(
        name: String,
        prev: String,
    ): String {
        val result =
            compilePrev(
                SourceFile.kotlin(
                    "$name.kt",
                    """
                    package test
                    import androidx.compose.runtime.Composable
                    import io.github.parkjiminnnn.runtime.Prev

                    $prev
                    @Composable
                    fun $name(text: String) {}
                    """,
                ),
            )
        assertEquals(result.messages, KotlinCompilation.ExitCode.OK, result.exitCode)
        return requireNotNull(result.generatedFile("${name}Preview.kt"))
    }

    @Test
    fun `a bare Prev still produces a bare Preview`() {
        val generated = generate("BareCard", "@Prev")

        // Every setting matches @Preview's own default, so none of them are written out.
        assertTrue(generated, generated.contains("@Preview\n@Composable"))
    }

    @Test
    fun `writes each setting that differs from the Preview default`() {
        val generated =
            generate(
                "SettingsCard",
                """@Prev(
                    group = "cards",
                    apiLevel = 34,
                    widthDp = 320,
                    heightDp = 640,
                    showSystemUi = true,
                    showBackground = true,
                )""",
            )

        assertTrue(generated, generated.contains("""group = "cards""""))
        assertTrue(generated, generated.contains("apiLevel = 34"))
        assertTrue(generated, generated.contains("widthDp = 320"))
        assertTrue(generated, generated.contains("heightDp = 640"))
        assertTrue(generated, generated.contains("showSystemUi = true"))
        assertTrue(generated, generated.contains("showBackground = true"))
    }

    @Test
    fun `writes a background colour back as hex`() {
        val generated = generate("ColorCard", "@Prev(showBackground = true, backgroundColor = 0xFF00FF00)")

        // Colours are written as 0xAARRGGBB in source; echoing the decimal the annotation argument
        // arrives as would be unreadable in the generated file.
        assertTrue(generated, generated.contains("backgroundColor = 0xFF00FF00L"))
    }

    @Test
    fun `names a wallpaper constant rather than writing its raw value`() {
        val generated =
            generate(
                "WallpaperCard",
                "@Prev(wallpaper = io.github.parkjiminnnn.runtime.Wallpapers.BLUE_DOMINATED_EXAMPLE)",
            )

        // PrevHam declares its own Wallpapers with the same names and values as Compose's, so @Prev
        // reads the same as the @Preview it generates - and a bare `wallpaper = 2` in the output
        // would say nothing to a reader.
        assertTrue(generated, generated.contains("import androidx.compose.ui.tooling.preview.Wallpapers"))
        assertTrue(generated, generated.contains("wallpaper = Wallpapers.BLUE_DOMINATED_EXAMPLE"))
    }

    @Test
    fun `leaves out a wallpaper left at NONE`() {
        val generated =
            generate(
                "NoWallpaperCard",
                "@Prev(wallpaper = io.github.parkjiminnnn.runtime.Wallpapers.NONE)",
            )

        assertFalse(generated, generated.contains("wallpaper"))
    }

    @Test
    fun `adds one Preview per device`() {
        val generated = generate("DeviceCard", """@Prev(devices = ["id:pixel_5", "id:pixel_tablet"])""")

        assertTrue(generated, generated.contains("""device = "id:pixel_5""""))
        assertTrue(generated, generated.contains("""device = "id:pixel_tablet""""))
        assertTrue(generated, generated.contains("""name = "Device: id:pixel_5""""))
        // The default Preview is still there alongside the two device variants.
        assertEquals(generated, 3, generated.split("@Preview").size - 1)
    }

    @Test
    fun `uses a configured name as the prefix for variant names`() {
        val generated = generate("NamedCard", """@Prev(name = "Card", darkMode = true, locales = ["ko"])""")

        // Replacing the variant labels outright would leave three Previews with the same name.
        assertTrue(generated, generated.contains("""@Preview(name = "Card")"""))
        assertTrue(generated, generated.contains("""name = "Card - Dark Mode""""))
        assertTrue(generated, generated.contains("""name = "Card - Locale: ko""""))
    }

    @Test
    fun `applies settings to variants as well as the default`() {
        val generated = generate("SharedCard", """@Prev(darkMode = true, group = "g", showBackground = true)""")

        // A setting describes how to render, not what to render, so every Preview carries it.
        assertEquals(generated, 2, generated.split("""group = "g"""").size - 1)
        assertEquals(generated, 2, generated.split("showBackground = true").size - 1)
    }

    @Test
    fun `leaves out settings left at their defaults`() {
        val generated = generate("PartialCard", "@Prev(showBackground = true)")

        assertTrue(generated, generated.contains("showBackground = true"))
        assertFalse(generated, generated.contains("apiLevel"))
        assertFalse(generated, generated.contains("widthDp"))
        assertFalse(generated, generated.contains("group"))
        assertFalse(generated, generated.contains("backgroundColor"))
        assertFalse(generated, generated.contains("wallpaper"))
    }
}
