package net.varionic.mcbinpack

import org.junit.Test

import org.junit.Assert.*
import org.hamcrest.CoreMatchers.`is` as ist

class BinPackingTest {

    @Test
    fun toEndpoint() {
        val bin = Bin(150, 120, Vacancy(150, 120))
        assertThat(bin.toEndpoint(), ist(Point(150, 120)))
    }

    @Test
    fun traverse() {
        val bin = Bin(150, 120, Vacancy(150, 120))

        assertThat(bin.traverse({ _, _, node -> 42}, { a, b -> a + b }), ist(42))
    }

    @Test
    fun computeScore() {
        var bin = Bin(150, 120, Vacancy(150, 120))
        assertThat(computeScore(bin), ist(0))

        bin = bin.reject(Item(10, 20))
        assertThat(computeScore(bin), ist(200))

        bin = bin.reject(Item(7, 11))
        assertThat(computeScore(bin), ist(277))
    }
}