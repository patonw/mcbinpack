package net.varionic.mcbinpack

import org.slf4j.LoggerFactory

typealias NodeSplitter = (Item, Vacancy) -> Pair<Node, List<Node>>

abstract class Guillotine : Solver {
    companion object {
        val log = LoggerFactory.getLogger(Guillotine::class.java)

        fun makeHVSplit(item: Item, vacancy: Vacancy): Pair<Node, List<Node>> {
            val inVac = Vacancy.make(vacancy.width - item.width, item.height)
            val inner = VSplit(item.width,
                    item,
                    inVac)


            val outVac = Vacancy.make(vacancy.width, vacancy.height - item.height)
            return HSplit(item.height,
                    inner,
                    outVac) to listOf(inVac, outVac)
        }

        fun makeVHSplit(item: Item, vacancy: Vacancy): Pair<Node, List<Node>> {
            val inVac = Vacancy.make(item.width, vacancy.height - item.height)
            val inner = HSplit(item.height,
                    item,
                    inVac)

            val outVac = Vacancy.make(vacancy.width - item.width, vacancy.height)
            return VSplit(item.width,
                    inner,
                    outVac) to listOf(inVac, outVac)
        }
    }

    fun gatherVacancy(start: Point, end: Point, node: Node) =
            if (node is Vacancy)
                listOf(node)
            else
                emptyList()

    // Should cache vacancies in a field and update after split
    fun gatherVacancies(bin: Bin) = bin.traverse(::gatherVacancy) { a, b -> a + b }

    // TODO convert to extension function on Bin
    fun insertItem(bin: Bin, item: Item, target: Vacancy, splitter: NodeSplitter): Bin {
        val (replacement, newVacs) = splitter(item, target)
        val vacList = bin.vacancies.remove(target).prependAll(newVacs.filterIsInstance<Vacancy>())
        return bin.copy(root = replaceNode(bin.root, target, replacement), vacancies = vacList)
    }

    // TODO convert to extension on node
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