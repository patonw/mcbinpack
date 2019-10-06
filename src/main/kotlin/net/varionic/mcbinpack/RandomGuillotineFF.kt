package net.varionic.mcbinpack

import io.vavr.collection.List
import kotlinx.coroutines.yield
import kotlin.random.Random

/**
 * Inserts items into a bin and subdivides remaining space twice.
 *
 * Item is inserted into first vacant block that has enough capacity.
 * The vacant block is split along one edge of the item then an adjacent edge.
 * Orientation of first split is chosen at random.
 */
class RandomGuillotineFF(
        val n: Int = 100_000,
        val reportInterval: Int = 500,
        val onBest: suspend (Bin, Int) -> Unit = { _, _ -> Unit },
        val onProgress: suspend (Int, Int) -> Unit = { _, _ -> Unit }
) : Guillotine() {

    /**
     * Uses a random transpose, random splitter and random vacancy selection
     */
    fun insertItemRandomly(bin: Bin, origItem: Item): Bin {
        val item = if (Random.nextBoolean()) origItem else origItem.t()
        val splitter = if (Random.nextBoolean()) Companion::makeHVSplit else Companion::makeVHSplit

        val target = bin.vacancies.shuffle().firstOrNull {
            it.height >= item.height && it.width >= item.width
        }

        target ?: return bin.copy(rejects = bin.rejects.prepend(item))
        return insertItem(bin, item, target, splitter)
    }

    fun insertItems(bin: Bin, items: List<Item>): Bin = items.fold(bin) { acc, item -> insertItemRandomly(acc, item) }

    override suspend fun solve(bin: Bin, items: List<Item>): Bin {
        var bestScore = Int.MAX_VALUE
        var solution: Bin = bin

        onProgress(0, n)

        (1..n).forEach {
            val candidate = insertItems(bin, items)
            val score = computeScore(candidate)
            if (score < bestScore) {
                bestScore = score
                solution = candidate
                onBest(candidate, score)
            }
            if (it % reportInterval == 0)
                onProgress(it, n)
            yield()
        }

        onProgress(n,n)

        return solution
    }

}
