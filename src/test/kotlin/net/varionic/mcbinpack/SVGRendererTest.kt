package net.varionic.mcbinpack

import org.junit.Test

import org.junit.Assert.*
import kotlin.test.todo

import org.hamcrest.CoreMatchers.`is` as ist

class SVGRendererTest {
    @Test
    fun renderDividers() {
        var bin = Bin(150, 120, Vacancy(150, 120))
        val renderer = SVGRenderer()
        assertThat(renderer.renderDividers(bin), ist(listOf()))

        val hSplit = HSplit(25, Empty, Empty)
        bin = bin.copy(root=hSplit)
        assertThat(renderer.renderDividers(bin), ist(listOf(Divider(Point(0,25), Point(150,25)))))

        val vSplit = VSplit(50, Empty, Empty)
        bin = bin.copy(root=hSplit.copy(bottom=vSplit))
        assertThat(renderer.renderDividers(bin), ist(listOf(
                Divider(Point(0,25), Point(150,25)),
                Divider(start=Point(x=50, y=25), end=Point(x=50, y=120))
                )))
    }

    @Test
    fun renderItems() {
        todo {  }
    }

    @Test
    fun renderVacancies() {
        todo {  }
    }

    @Test
    fun renderRejects() {
        todo {  }
    }

    @Test
    fun render() {
        todo {  }
    }
}