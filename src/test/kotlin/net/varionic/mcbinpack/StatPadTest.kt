package net.varionic.mcbinpack

import org.junit.Test

import org.junit.Assert.*
import org.hamcrest.CoreMatchers.*
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.lessThan

import io.vavr.collection.List
import org.hamcrest.CoreMatchers.`is` as ist

class StatPadTest {
    @Test
    fun testOf() {
        val a = StatPad.of(List.of(2,3,4))
        assertThat(a, ist(StatPad(3, 2, 3.0, 2/3.0)))
    }
    @Test
    fun plus() {
        val a = StatPad(10, 10, 100.0, 1.0)
        val b = StatPad(10, 5, 200.0, 5.0)

        val c = a + b
        assertThat(c, ist(StatPad(20, 5, 150.0, 2503.0)))

        val z = StatPad(0, 0, 0.0, 0.0)
        assertThat(a + z, ist(a))
        assertThat(z + a, ist(a))
    }

    @Test
    fun lcb() {
        val a = StatPad(5, 5, 200.0, 5.0)
        val b = StatPad(10, 5, 200.0, 5.0)

        assertThat(a.lcb(), closeTo(198.0, 1e-4))
        assertThat(a.lcb(), lessThan(b.lcb())) // Fewer samples means lower confidence

        val z = StatPad(0, 0, 0.0, 0.0)

        assertThat(z.lcb(), ist(Double.NEGATIVE_INFINITY))
    }
}