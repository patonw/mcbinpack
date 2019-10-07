package net.varionic.mcbinpack

data class Point(val x: Int, val y: Int)
data class Divider(val start: Point, val end: Point)
data class Rect(val start: Point, val width: Int, val height: Int)

abstract class BaseRenderer<T> {
    private fun renderDivider(start: Point, end: Point, node: Node): List<Divider> = when (node) {
        is HSplit -> {
            val mid = start.y + node.pos
            listOf(Divider(Point(start.x, mid), Point(end.x, mid)))
        }
        is VSplit -> {
            val mid = start.x + node.pos
            listOf(Divider(Point(mid, start.y), Point(mid, end.y)))
        }
        else ->
            emptyList()
    }

    fun renderDividers(bin: Bin): List<Divider> = bin.traverse(::renderDivider) { a, b -> a + b }

    private fun renderItem(start: Point, end: Point, node: Node): List<Rect> =
            if (node is Item)
                listOf(Rect(start, node.width, node.height))
            else
                emptyList()

    fun renderItems(bin: Bin): List<Rect> = bin.traverse(::renderItem) { a, b -> a + b }

    private fun renderVacancy(start: Point, end: Point, node: Node): List<Rect> = when (node) {
        is Vacancy -> listOf(Rect(start, node.width, node.height))
        else -> emptyList()
    }

    fun renderVacancies(bin: Bin): List<Rect> = bin.traverse(::renderVacancy) { a, b -> a + b }

    fun renderRejects(bin: Bin): List<Rect> {
        var xoff = bin.width + 20
        var yoff = 0
        var colWidth = 0
        val result: MutableList<Rect> = mutableListOf()
        bin.rejects.forEach {
            if (yoff > 300) {
                yoff = 0
                xoff += colWidth + 10
                colWidth = 0
            }
            result.add(Rect(Point(xoff, yoff), it.width, it.height))
            yoff += it.height + 10
            colWidth = Integer.max(colWidth, it.width)
        }

        return result
    }

    abstract fun render(bin: Bin): T
}