package io.github.parkjiminnnn.gradle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// The pipeline either side of a value source: reading what the compiler recorded, subtracting what
// is already decided, and merging without ever losing a decision. None of it depends on where the
// values come from, which is why all of it can be tested without one.
class MockValueFilesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun file(
        name: String,
        text: String,
    ): File = folder.newFile(name).apply { writeText(text) }

    private fun slot(path: String) = MockValueSlot(path, path.substringBeforeLast('.'), path.substringAfterLast('.'), "kotlin.String")

    @Test
    fun `reads the slots the compiler recorded`() {
        val manifest =
            file(
                "slots.json",
                """
                {"slots": [
                  {"slot": "com.a.Festival.name", "owner": "com.a.Festival",
                   "name": "name", "type": "kotlin.String"}
                ]}
                """,
            )

        val slots = MockValueFiles.readSlots(manifest)

        assertEquals(listOf(MockValueSlot("com.a.Festival.name", "com.a.Festival", "name", "kotlin.String")), slots)
    }

    @Test
    fun `reads no slots when the compiler has not written a manifest`() {
        assertEquals(emptyList<MockValueSlot>(), MockValueFiles.readSlots(File(folder.root, "absent.json")))
    }

    @Test
    fun `reads no values when the file does not exist yet`() {
        assertEquals(emptyMap<String, String>(), MockValueFiles.readValues(File(folder.root, "absent.json")))
    }

    @Test
    fun `asks only about slots with nothing decided`() {
        val slots = listOf(slot("com.a.A.x"), slot("com.a.B.y"))

        val undecided = slots.undecided(mapOf("com.a.A.x" to "decided"), force = false)

        assertEquals(listOf(slot("com.a.B.y")), undecided)
    }

    @Test
    fun `counts a blank as undecided`() {
        // The scaffold writes blanks, so a second run has to be able to fill them.
        val slots = listOf(slot("com.a.A.x"))

        assertEquals(slots, slots.undecided(mapOf("com.a.A.x" to "  "), force = false))
    }

    @Test
    fun `asks about everything when forced`() {
        val slots = listOf(slot("com.a.A.x"), slot("com.a.B.y"))

        assertEquals(slots, slots.undecided(mapOf("com.a.A.x" to "decided"), force = true))
    }

    @Test
    fun `never overwrites a decided value when merging`() {
        // The second of two guards on the same rule: undecided() already filtered this slot out, so
        // a source answering it anyway is answering more than it was asked. A hand-written value
        // surviving every later run is the whole reason values live in a file.
        val merged = mapOf("a" to "by hand").mergedWith(mapOf("a" to "generated", "b" to "new"))

        assertEquals(mapOf("a" to "by hand", "b" to "new"), merged)
    }

    @Test
    fun `writes values sorted so a re-run diffs by what changed`() {
        val target = File(folder.root, "values.json")

        MockValueFiles.writeValues(target, mapOf("com.b.B.y" to "second", "com.a.A.x" to "first"))

        val written = target.readText()
        assertTrue(written, written.indexOf("com.a.A.x") < written.indexOf("com.b.B.y"))
        assertEquals(mapOf("com.a.A.x" to "first", "com.b.B.y" to "second"), MockValueFiles.readValues(target))
    }

    @Test
    fun `scaffolds every asked slot with a blank`() {
        val slots = listOf(slot("com.a.A.x"), slot("com.a.B.y"))

        assertEquals(mapOf("com.a.A.x" to "", "com.a.B.y" to ""), PlaceholderMockValueSource.valuesFor(slots))
    }

    @Test
    fun `writes non-ascii as itself, not as escapes`() {
        // This file exists to be opened and edited by hand, and "제 1회 …" is a value nobody
        // will correct. JSON is UTF-8; the characters need no escaping.
        val target = File(folder.root, "values.json")

        MockValueFiles.writeValues(target, mapOf("com.a.Festival.name" to "제 1회 대학 음악제"))

        val written = target.readText()
        assertTrue(written, written.contains("제 1회 대학 음악제"))
        assertFalse(written, written.contains("\\u"))
    }

    @Test
    fun `escapes what JSON actually requires`() {
        val target = File(folder.root, "values.json")
        val awkward = "he said \"hi\"\\\n\ttab"

        MockValueFiles.writeValues(target, mapOf("com.a.A.x" to awkward))

        // Round-tripping is the assertion: whatever the escaping looks like, reading it back has to
        // produce what went in.
        assertEquals(mapOf("com.a.A.x" to awkward), MockValueFiles.readValues(target))
    }

    @Test
    fun `writes an empty file as an empty object`() {
        val target = File(folder.root, "values.json")

        MockValueFiles.writeValues(target, emptyMap())

        assertEquals(emptyMap<String, String>(), MockValueFiles.readValues(target))
    }
}
