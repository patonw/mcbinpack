package net.varionic.mcbinpack

import org.junit.Test

import org.junit.Assert.*
import kotlin.test.todo
import org.hamcrest.CoreMatchers.`is` as ist

class GuillotineTest {

    @Test
    fun gatherVacancy() {
        val bin = Bin(200, 200, Vacancy(200, 200))

        val g = Guillotine()
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
        val g = Guillotine()
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
}