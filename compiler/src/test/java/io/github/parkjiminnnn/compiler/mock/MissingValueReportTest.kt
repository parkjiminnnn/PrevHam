package io.github.parkjiminnnn.compiler.mock

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MissingValueReportTest {
    @Test
    fun `has nothing to say when every slot is decided`() {
        assertNull(MissingValueReport.message(emptyList()))
    }

    @Test
    fun `lists every path up to the limit`() {
        val paths = (1..10).map { "com.foo.Festival.field$it" }

        val message = requireNotNull(MissingValueReport.message(paths))

        paths.forEach { assertTrue(message, message.contains(it)) }
        assertTrue(message, !message.contains("and 0 more"))
    }

    @Test
    fun `counts the ones it does not list`() {
        val message = requireNotNull(MissingValueReport.message((1..25).map { "com.foo.Festival.field$it" }))

        assertTrue(message, message.contains("25 slot(s) have no mock value"))
        assertTrue(message, message.contains("com.foo.Festival.field10"))
        assertTrue(message, !message.contains("com.foo.Festival.field11"))
        assertTrue(message, message.contains("... and 15 more"))
    }

    @Test
    fun `names the way out`() {
        // Whoever reads this is one command away from fixing it, and one setting away from never
        // hearing it again. Both belong in the message rather than in documentation they would have
        // to go looking for.
        val message = requireNotNull(MissingValueReport.message(listOf("com.foo.Festival.slogan")))

        assertTrue(message, message.contains("prevhamGenerateMockValues"))
        assertTrue(message, message.contains("warnOnMissingValues = false"))
    }
}
