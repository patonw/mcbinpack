package net.varionic.mcbinpack

import io.vavr.collection.List
import io.vavr.control.Option
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

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
data class Vacancy(val width: Int, val height: Int) : Node() {
    override fun equals(other: Any?): Boolean {
        return this === other
    }

    override fun toString(): String {
        return "Vacancy($width, $height, ***)"
    }
}

data class Bin(val width: Int, val height: Int, val root: Node = Empty, val rejects: List<Item> = List.empty()) {
    companion object {
        fun create(width: Int, height: Int) = Bin(width, height, Vacancy(width, height))
    }

    fun reject(item: Item) = this.copy(rejects = rejects.prepend(item))
    val items by lazy { this.getItems() }
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

data class StatPad(val n: Int, val min: Int, val mean: Double, val vrc: Double) {
    // Combine statistics of two subsamples
    operator fun plus(that: StatPad): StatPad {
        if (that.n == 0)
            return this
        if (n == 0)
            return that

        val nC = n + that.n
        val meanC = (n * mean + that.n * that.mean) / nC
        val var2C = (n * (vrc + (mean - meanC).pow(2)) + that.n * (that.vrc + (that.mean - meanC).pow(2))) / nC
        val minC = min(min, that.min)
        return StatPad(nC, minC, meanC, var2C)
    }

    companion object {
        val empty = StatPad(0, Int.MAX_VALUE, 0.0, 0.0)
        fun of(samples: List<Int>): StatPad {
            val n = samples.size()
            val mean = samples.sum().toDouble() / n
            val var2 = samples.map { (it - mean).pow(2) }.sum().toDouble() / n

            return StatPad(n, samples.min().getOrElse(Int.MAX_VALUE), mean, var2)
        }
    }

    fun lcb(z: Double = 2.0) = if (n > 1) mean - z * sqrt(vrc/n) else Double.NEGATIVE_INFINITY
}