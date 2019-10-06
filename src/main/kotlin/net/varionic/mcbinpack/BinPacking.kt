package net.varionic.mcbinpack

import io.vavr.collection.List

sealed class Node
object Empty : Node()
data class Item(val width: Int, val height: Int) : Node() {

    /**
     * Transpose item
     */
    fun t() = Item(height, width)

}

data class HSplit(val pos: Int, val top: Node, val bottom: Node) : Node()
data class VSplit(val pos: Int, val left: Node, val right: Node) : Node()
data class Vacancy(val width: Int, val height: Int) : Node(), Comparable<Vacancy> {
    companion object {
        fun make(width:Int, height:Int) = if (width * height > 0) Vacancy(width, height) else Empty
    }

    val area get() = width * height

    override fun compareTo(other: Vacancy): Int {
        return (area).compareTo(other.area)
    }

    override fun equals(other: Any?): Boolean {
        return this === other
    }

    override fun toString(): String {
        return "Vacancy($width, $height, ***)"
    }
}

data class Bin(val width: Int, val height: Int, val root: Node = Empty, val vacancies: List<Vacancy> = List.of(Vacancy(width, height)), val rejects: List<Item> = List.empty()) {
    companion object {
        fun empty(width: Int, height: Int) = Vacancy(width, height).let { Bin(width, height, it, List.of(it) ) }
    }

    fun reject(item: Item) = this.copy(rejects = rejects.prepend(item))
}

fun Bin.toEndpoint() = Point(width, height)

/**
 * Traverses the bin with a visitor lambda.
 *
 * Runs [block] on each node of the receiver and combines results from each subtree.
 *
 * @receiver Bin the object to traverse
 * @param block Lambda to execute on each node
 * @param combine Binary function to combine the results of executing [block] on each subtree
 */
fun <T> Bin.traverse(block: (Point, Point, Node) -> T, combine: (T, T) -> T): T {
    fun helper(start: Point, end: Point, node: Node): T {
        val result = block(start, end, node)
        when (node) {
            is HSplit -> {
                val mid = start.y + node.pos
                val tops = helper(start, Point(end.x, mid), node.top)
                val bottoms = helper(Point(start.x, mid), end, node.bottom)

                return combine(result, combine(tops, bottoms))
            }
            is VSplit -> {
                val mid = start.x + node.pos
                val lefts = helper(start, Point(mid, end.y), node.left)
                val rights = helper(Point(mid, start.y), end, node.right)

                return combine(result, combine(lefts, rights))
            }
            else -> return result
        }
    }

    return helper(Point(0, 0), this.toEndpoint(), this.root)
}

fun Bin.getItems() = traverse({ _, _, node ->
    if (node is Item) listOf(node) else emptyList()
}, { a, b ->
    a + b
})

fun Bin.countItems() = traverse({ _, _, node ->
    if (node is Item) 1 else 0
}, { a, b ->
    a + b
})

/**
 * Computes a score based on the area of rejected items.
 *
 */
//fun computeScore(bin: Bin) = bin.rejects.map { it.height * it.width }.sum().toInt()

val vacancyArea: (Point, Point, Node) -> Int = { _,_,node -> when(node) {
    is Vacancy -> node.width * node.height
    else -> 0
}}

// Wasted space per bin
fun computeScore(bin: Bin) = bin.traverse(vacancyArea) { a, b -> a + b }

