package io.github.parkjiminnnn.compiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundReportTest {
    private fun tally(
        generated: Int = 0,
        failed: Int = 0,
        skipped: List<String> = emptyList(),
    ) = RoundTally().apply {
        repeat(generated) {
            found()
            generated()
        }
        repeat(failed) {
            found()
            failed()
        }
        skipped.forEach {
            found()
            skipped(it, "no mock generator available for parameter 'x'")
        }
    }

    @Test
    fun `says nothing when no Prev was found`() {
        assertNull(RoundReport.message(RoundTally()))
    }

    @Test
    fun `reports only the outcomes that happened`() {
        val message = requireNotNull(RoundReport.message(tally(generated = 43)))

        assertEquals("[PrevHam] 43 @Prev found: 43 generated", message)
    }

    @Test
    fun `lists the skipped ones with their reason`() {
        val message =
            requireNotNull(RoundReport.message(tally(generated = 40, skipped = listOf("FestivalCard", "NoticeRow"))))

        assertTrue(message, message.startsWith("[PrevHam] 42 @Prev found: 40 generated, 2 skipped:"))
        assertTrue(message, message.contains("\n  FestivalCard: no mock generator available for parameter 'x'"))
        assertTrue(message, message.contains("\n  NoticeRow: no mock generator available for parameter 'x'"))
    }

    @Test
    fun `counts a failure separately from a skip`() {
        // They call for different responses: a skip is a type PrevHam cannot mock yet, a failure is
        // a @Prev that no version of PrevHam can honour.
        val message = requireNotNull(RoundReport.message(tally(generated = 40, failed = 1, skipped = listOf("Card"))))

        assertTrue(message, message.startsWith("[PrevHam] 42 @Prev found: 40 generated, 1 skipped, 1 failed:"))
    }

    @Test
    fun `the outcomes add up to what was found`() {
        val tally = tally(generated = 5, failed = 2, skipped = listOf("A", "B", "C"))

        assertEquals(10, tally.found)
        assertEquals(tally.found, tally.generated + tally.failed + tally.skipped.size)
    }

    @Test
    fun `truncates a long list`() {
        val message = requireNotNull(RoundReport.message(tally(skipped = (1..25).map { "Card$it" })))

        assertTrue(message, message.contains("25 @Prev found: 0 generated, 25 skipped:"))
        assertTrue(message, message.contains("\n  Card10:"))
        assertFalse(message, message.contains("\n  Card11:"))
        assertTrue(message, message.contains("... and 15 more"))
    }

    @Test
    fun `is only worth a warning when something went wrong`() {
        assertFalse(tally(generated = 43).hasProblems)
        assertTrue(tally(generated = 42, skipped = listOf("Card")).hasProblems)
        assertTrue(tally(generated = 42, failed = 1).hasProblems)
    }
}
