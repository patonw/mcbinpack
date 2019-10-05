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
        val onBest: suspend (Bin, Int) -> Unit = { _, _ -> Unit },
        val onProgress: suspend (Int, Int) -> Unit = { _, _ -> Unit }
) : Guillotine() {
    fun insertItem(bin: Bin, item: Item): Bin = insertItem(
            bin,
            if (Random.nextBoolean()) item else item.t(),
            if (Random.nextBoolean()) this::makeHVSplit else this::makeVHSplit)
    fun insertItems(bin: Bin, items: List<Item>): Bin = items.fold(bin) { acc, item -> insertItem(acc, item) }

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
            if (it%500 == 0)
                onProgress(it, n)
            yield()
        }

        onProgress(n,n)

        return solution
    }
}
