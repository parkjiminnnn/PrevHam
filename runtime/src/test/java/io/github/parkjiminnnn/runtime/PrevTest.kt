package io.github.parkjiminnnn.runtime

import org.junit.Test

import org.junit.Assert.assertEquals
import java.lang.annotation.ElementType
import java.lang.annotation.Retention as JavaRetention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target as JavaTarget

class PrevTest {

    @Prev
    private fun annotatedFunction() = Unit

    @Test
    fun `has SOURCE retention`() {
        val retention = Prev::class.java.getAnnotation(JavaRetention::class.java)

        assertEquals(RetentionPolicy.SOURCE, retention.value)
    }

    @Test
    fun `targets FUNCTION only`() {
        val target = Prev::class.java.getAnnotation(JavaTarget::class.java)

        assertEquals(setOf(ElementType.METHOD), target.value.toSet())
    }

    @Test
    fun `can be applied to a function`() {
        annotatedFunction()
    }
}
