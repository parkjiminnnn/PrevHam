package io.github.parkjiminnnn.compiler.mock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MockValuesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun fileOf(text: String): File = folder.newFile("mock-values.json").apply { writeText(text) }

    @Test
    fun `reads a slot's value`() {
        val values = MockValues.from(fileOf("""{"com.example.Festival.name": "2026 대동제"}""")).getOrThrow()

        assertEquals("2026 대동제", values["com.example.Festival.name"])
    }

    @Test
    fun `has no value for an unlisted slot`() {
        val values = MockValues.from(fileOf("""{"com.example.Festival.name": "x"}""")).getOrThrow()

        assertNull(values["com.example.User.name"])
    }

    @Test
    fun `treats an empty file as no values`() {
        assertTrue(MockValues.from(fileOf("")).getOrThrow().isEmpty)
    }

    @Test
    fun `fails without throwing on malformed json`() {
        // A hand-edited file with a stray comma degrades the Preview to its defaults; it must not
        // fail the compilation round.
        val result = MockValues.from(fileOf("""{"a": "b",}"""))

        assertTrue(result.isFailure)
    }

    @Test
    fun `fails without throwing on a missing file`() {
        val result = MockValues.from(File(folder.root, "absent.json"))

        assertTrue(result.isFailure)
    }
}
