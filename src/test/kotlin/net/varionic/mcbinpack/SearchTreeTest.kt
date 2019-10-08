package net.varionic.mcbinpack

import org.junit.Test

import org.junit.Assert.*
import io.vavr.collection.List
import java.util.concurrent.atomic.AtomicInteger

import org.hamcrest.CoreMatchers.`is` as ist

class SearchTreeTest {

    @Test
    fun simulate() {
        val bin = Bin.empty(20,20)
        val items = List.of(Item(20, 20), Item(10,10))

        val ctr = AtomicInteger(7)
        val tree = SearchTree(bin, items)
        val scores = tree.simulate(5, ctr)

        assertThat(ctr.get(), ist(2))
        assertThat(scores.sum().toInt(), ist(0))
    }

    @Test
    fun leafDepths() {
    }
}