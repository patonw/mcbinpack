package net.varionic.mcbinpack

import org.slf4j.LoggerFactory

open class Guillotine {
    companion object {
        val log = LoggerFactory.getLogger(Guillotine::class.java)
    }

    fun gatherVacancy(start: Point, end: Point, node: Node): List<Vacancy> =
            if (node is Vacancy)
                listOf(node)
            else
                emptyList()

    // Should cache vacancies in a field and update after split
    fun gatherVacancies(bin: Bin): List<Vacancy> = bin.traverse(::gatherVacancy) { a, b -> a + b }

    fun insertItem(bin: Bin, item: Item, splitter: (Item, Vacancy) -> Node): Bin {
        val vacancies = gatherVacancies(bin)
        val target = vacancies.firstOrNull {
            it.height >= item.height && it.width >= item.width
        }

        if (target == null) {
            return bin.copy(rejects = bin.rejects.prepend(item))
        }

        val replacement = splitter(item, target)

        val result = bin.copy(root = replaceNode(bin.root, target, replacement))
        if (result.items.size != bin.items.size+1) {
            log.warn("Something went wrong!")
        }
        return result
    }

    fun makeHVSplit(item: Item, vacancy: Vacancy): HSplit {
        val inner = VSplit(item.width,
                item,
                Vacancy(vacancy.width - item.width, item.height))


        return HSplit(item.height,
                inner,
                Vacancy(vacancy.width, vacancy.height - item.height))
    }

    fun makeVHSplit(item: Item, vacancy: Vacancy): VSplit {
        val inner = HSplit(item.height,
                item,
                Vacancy(item.width, vacancy.height - item.height))

        return VSplit(item.width,
                inner,
                Vacancy(vacancy.width - item.width, vacancy.height))
    }

    fun replaceNode(root: Node, target: Node, replacement: Node): Node {
        return when (root) {
            target -> replacement
            is HSplit -> when (target) {
                root.top -> HSplit(root.pos, replacement, root.bottom)
                root.bottom -> HSplit(root.pos, root.top, replacement)
                else -> HSplit(root.pos, replaceNode(root.top, target, replacement), replaceNode(root.bottom, target, replacement))
            }
            is VSplit -> when (target) {
                root.left -> VSplit(root.pos, replacement, root.right)
                root.right -> VSplit(root.pos, root.left, replacement)
                else -> VSplit(root.pos, replaceNode(root.left, target, replacement), replaceNode(root.right, target, replacement))
            }
            else -> root
        }
    }
}