package io.github.parkjiminnnn.gradle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockValuePromptTest {
    private fun slot(
        owner: String,
        name: String,
    ) = MockValueSlot("$owner.$name", owner, name, "kotlin.String")

    @Test
    fun `groups a type's properties together`() {
        // The siblings are the context: festivalName alone says little, festivalName beside
        // universityName under a type called Festival says what kind of app this is.
        val user =
            MockValuePrompt.user(
                listOf(
                    slot("com.a.Festival", "festivalName"),
                    slot("com.a.Poster", "imageUrl"),
                    slot("com.a.Festival", "universityName"),
                ),
            )

        val festival = user.substringAfter("type: com.a.Festival").substringBefore("type: com.a.Poster")
        assertTrue(user, festival.contains("festivalName"))
        assertTrue(user, festival.contains("universityName"))
    }

    @Test
    fun `asks with a short key, not a fully qualified path`() {
        // Echoing a 60-character path back exactly is transcription, not the work, and a model that
        // fails at it loses the answer with it. Measured against a real project, only the shortest
        // key survived validation.
        val user = MockValuePrompt.user(listOf(slot("com.a.Festival", "festivalName")))

        assertTrue(user, user.contains("Festival.festivalName"))
        assertFalse(user, user.contains("com.a.Festival.festivalName"))
    }

    @Test
    fun `keeps both names of a nested type`() {
        // Dropping the outer name would make two nested types indistinguishable.
        val user = MockValuePrompt.user(listOf(slot("com.a.Outer.Inner", "title")))

        assertTrue(user, user.contains("Inner.title"))
    }

    @Test
    fun `never puts two types with the same simple name in one request`() {
        // The reply is keyed by simple name, so com.a.Festival.name and com.b.Festival.name would be
        // indistinguishable in one reply.
        val slots = listOf(slot("com.a.Festival", "name"), slot("com.b.Festival", "name"))

        val chunks = MockValuePrompt.chunk(slots, maxSlots = 40)

        assertEquals(2, chunks.size)
    }

    @Test
    fun `names the language to write in`() {
        assertTrue(MockValuePrompt.system("ko").contains("ko"))
    }

    @Test
    fun `tells the model not to invent people`() {
        // These values end up committed to a repository, so they must not read as real user data.
        val system = MockValuePrompt.system("en")

        assertTrue(system, system.contains("never real user data"))
    }

    @Test
    fun `keeps a type whole rather than splitting it across requests`() {
        // A type answered in halves loses the sibling context that makes its values agree.
        val slots = (1..6).map { slot("com.a.Wide", "p$it") } + (1..2).map { slot("com.a.Narrow", "q$it") }

        val chunks = MockValuePrompt.chunk(slots, maxSlots = 4)

        assertEquals(2, chunks.size)
        assertEquals(6, chunks[0].size)
        assertTrue(chunks.all { chunk -> chunk.map { it.owner }.distinct().size == chunk.groupBy { it.owner }.size })
    }

    @Test
    fun `fits several types into one request`() {
        val slots = (1..3).map { slot("com.a.A", "p$it") } + (1..3).map { slot("com.a.B", "q$it") }

        assertEquals(1, MockValuePrompt.chunk(slots, maxSlots = 40).size)
    }

    @Test
    fun `chunks nothing into nothing`() {
        assertEquals(emptyList<List<MockValueSlot>>(), MockValuePrompt.chunk(emptyList(), maxSlots = 40))
    }

    @Test
    fun `states each slot's type, simply named`() {
        // The model has to know a property is a number to answer with one, and kotlin.Int says
        // nothing Int doesn't.
        val user =
            MockValuePrompt.user(
                listOf(MockValueSlot("com.a.Festival.visitors", "com.a.Festival", "visitors", "kotlin.Int")),
            )

        assertTrue(user, user.contains("Festival.visitors : Int"))
        assertFalse(user, user.contains("kotlin.Int"))
    }

    @Test
    fun `tells the model a number is a bare number`() {
        val system = MockValuePrompt.system("ko")

        assertTrue(system, system.contains("bare number"))
    }
}
