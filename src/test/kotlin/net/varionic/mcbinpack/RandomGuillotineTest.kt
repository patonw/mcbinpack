package net.varionic.mcbinpack

import io.vavr.collection.List
import io.vavr.kotlin.toVavrList
import org.junit.Test

import org.junit.Assert.*
import kotlin.test.todo
import org.hamcrest.CoreMatchers.`is` as ist

class RandomGuillotineTest {

    @Test
    fun gatherVacancy() {
        todo {  }
    }

    @Test
    fun gatherVacancies() {
        todo {  }
    }

    @Test
    fun makeHVSplit() {
        todo {  }
    }

    @Test
    fun makeVHSplit() {
        todo {  }
    }

    @Test
    fun insertItem() {
        todo {  }
    }

    @Test
    fun testInsertItems() {
        var bin = Bin.empty(154, 128)
        val items = List.of(
                Item(30, 40),
                Item(30, 40),
                Item(30, 40),
                Item(30, 40),
                Item(25, 25),
                Item(30, 30),
                Item(60, 40),
                Item(30, 40),
                Item(60, 40),
                Item(30, 80),
                Item(10, 10),
                Item(25, 25),
                Item(30, 30),
                Item(30, 80),
                Item(10, 10),
                Item(25, 25),
                Item(30, 30),
                Item(25, 25),
                Item(8, 30),
                Item(5, 20),
                Item(30, 40)
        ).sortedByDescending { it.width * it.height }.toVavrList()

        assertThat(items.length(), ist(21))
        assertThat(bin.countItems(), ist(0))

        val sampler = RandomGuillotineFF()
        bin = sampler.insertItems(bin, items)

        assertThat(bin.countItems() + bin.rejects.length(), ist(items.length()))
        println(bin)
    }
}