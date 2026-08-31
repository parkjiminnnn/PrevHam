package io.github.parkjiminnnn.gradle

import org.junit.Assert.assertEquals
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
    fun `states each slot's full path and type`() {
        // The reply is keyed by path, so the path has to be in front of the model exactly as it
        // will be validated against.
        val user = MockValuePrompt.user(listOf(slot("com.a.Festival", "festivalName")))

        assertTrue(user, user.contains("com.a.Festival.festivalName : kotlin.String"))
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
}
