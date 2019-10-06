package net.varionic.mcbinpack

import io.vavr.collection.List
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.CoreMatchers.instanceOf
import org.junit.Test

import org.junit.Assert.*
import kotlin.test.todo
import org.hamcrest.CoreMatchers.`is` as ist

class GuillotineTest {
    class MockGuillotine: Guillotine() {
        override suspend fun solve(bin: Bin, items: List<Item>): Bin {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        // TODO convert to extension function on Bin
        fun insertItemRandomly(bin: Bin, item: Item, splitter: NodeSplitter): Bin {
    //        val vacancies = gatherVacancies(bin)//bin.vacancies // Is it worth filtering by area first?
    //        assert(TreeSet.of(*vacancies.toTypedArray()) == bin.vacancies)
             val vacancies = bin.vacancies

            val target = vacancies.shuffle().lastOrNull {
                it.height >= item.height && it.width >= item.width
            }

            target ?: return bin.copy(rejects = bin.rejects.prepend(item))
            return insertItem(bin, item, target, splitter)
        }
    }

    @Test
    fun gatherVacancy() {
        val bin = Bin.empty(200, 200)

        val g = MockGuillotine()
        val vacs = g.gatherVacancies(bin)
        assertEquals(vacs.size, (1))
        assertEquals(vacs[0].width, 200)
        assertEquals(vacs[0].height, 200)
    }

    @Test
    fun gatherVacancies() {
        todo {  }
    }

    @Test
    fun insertItem() {
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
    fun replaceNode() {
        val g = MockGuillotine()
        val target = Vacancy(200,100)
        var bin = Bin(200, 200, HSplit(100,
                target.copy(),
                target
        ))

        val replacement = Item(50,50)
        val result = g.replaceNode(bin.root, target, replacement)

        bin = bin.copy(root=result)
        val items = bin.getItems()
        assertThat(items, ist(listOf(replacement)))
    }

    @Test
    fun insertItemFull() {
        val g = MockGuillotine()
        var bin = Bin.empty(200, 200)
        assertThat(bin.vacancies.toList().size(), ist(1))
        bin = g.insertItemRandomly(bin, Item(200,200), Guillotine.Companion::makeHVSplit)
        assertThat(bin.vacancies.size(), ist(0))
        assertThat(g.gatherVacancies(bin).size, ist(0))
    }

    @Test
    fun insertItemHalf() {
        val g = MockGuillotine()
        var bin = Bin.empty(200, 200)
        assertThat(bin.vacancies.toList().size(), ist(1))
        bin = g.insertItemRandomly(bin, Item(200,100), Guillotine.Companion::makeHVSplit)
        assertThat(bin.vacancies.size(), ist(1))
        assertThat(g.gatherVacancies(bin).size, ist(1))

        val first = bin.vacancies.first()
        assertThat(first.width, ist(200))
        assertThat(first.height, ist(100))
    }

    @Test
    fun insertItemSmall() {
        val g = MockGuillotine()
        var bin = Bin.empty(200, 200)
        assertThat(bin.vacancies.toList().size(), ist(1))
        bin = g.insertItemRandomly(bin, Item(25,50), Guillotine.Companion::makeHVSplit)
        assertThat(bin.vacancies.size(), ist(2))

        assertThat(bin.root, instanceOf(HSplit::class.java))
        val root = bin.root as HSplit
        assertThat(root.bottom, instanceOf(Vacancy::class.java))
        val bottom = root.bottom as Vacancy
        assertThat(bottom.width, ist(200))
        assertThat(bottom.height, ist(150))

        assertThat(root.top, instanceOf(VSplit::class.java))
        val top = root.top as VSplit
        assertThat(top.left, instanceOf(Item::class.java))
        val item = top.left as Item
        assertThat(item, ist(Item(25, 50)))

        assertThat(top.right, instanceOf(Vacancy::class.java))
        val right = top.right as Vacancy
        assertThat(right.width, ist(175))
        assertThat(right.height, ist(50))

        assertThat(bin.vacancies, hasItems(bottom, right))
        assertThat(g.gatherVacancies(bin), hasItems(bottom, right))

        println(bin)
    }
}