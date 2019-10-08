package net.varionic.mcbinpack

import io.vavr.collection.List
import io.vavr.kotlin.toVavrList
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class SearchTree(val partial: Bin, val items: List<Item>, val label: String = "", val depth: Int = 0) {
    companion object {
        val log = LoggerFactory.getLogger(SearchTree::class.java)
        val marker = MarkerFactory.getMarker("MCTS_NODE")
    }

    val sampler = RandomGuillotineFF()
    val lossFunc = ::computeScore
    val childRef: AtomicReference<List<SearchTree>> = AtomicReference(List.empty())
    val children get() = childRef.get()
    var best: AtomicReference<Pair<Int, Bin>> = AtomicReference(Int.MAX_VALUE to partial)
    val bestScore: Int
        get() = best.get().first
    val solution: Bin
        get() = best.get().second

    var localStats: AtomicReference<StatPad> = AtomicReference(StatPad.empty)
    var descStats: StatPad = StatPad.empty // Computed from localStats of children

    private fun simulate(): Pair<Bin, Int> {
        val result = items.fold(partial) { bin, item -> sampler.insertItemRandomly(bin, item) }
        val score = lossFunc(result)

        while (true) {
            val lastBest = best.get()

            if (score >= lastBest.first)
                break

            if (best.compareAndSet(lastBest, score to result))
                break
            else
                log.debug("Retrying to set simulated best $score")
        }

        return result to score
    }

    private fun simulate(times: Int): List<Int> {
        val scores = (1..times).map { simulate() }.map { it.second }.toVavrList()
        val newStats = StatPad.of(scores)

        while (true) {
            val oldStats = localStats.get()
            val combinedStats = oldStats + newStats
            if (localStats.compareAndSet(oldStats, combinedStats))
                break
            else
                log.debug("Retrying to update localStats at $label")
        }

        return scores
    }

    fun simulate(times: Int, quota: AtomicInteger) = simulate(times).also { quota.addAndGet(-times) }

    // For profiling tree structure
    fun leafDepths(): List<Int> {
        if (children.isEmpty)
            return List.of(0)
        return children.flatMap { it.leafDepths() }.map { it + 1 }.toVavrList()
    }
}

// Emulating Kotlin's unary minBy
fun <T : Comparable<T>> List<SearchTree>.minMap(block: (SearchTree) -> T) = minBy { a,b -> block(a).compareTo(block(b))}.orNull