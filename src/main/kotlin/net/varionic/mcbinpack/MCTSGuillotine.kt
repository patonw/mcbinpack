package net.varionic.mcbinpack

import io.vavr.Tuple
import io.vavr.collection.List
import io.vavr.kotlin.toVavrList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

// Emulating Kotlin's unary minBy
fun <T : Comparable<T>> List<SearchTree>.minMap(block: (SearchTree) -> T) = minBy { a,b -> block(a).compareTo(block(b))}.orNull

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

class MCTSGuillotine(
        val sampler: Guillotine = RandomGuillotineFF(),
        val rounds: Int = 300,
        val quota: Int = 500,
        val cores: Int = 1,
        val batchSize: Int = 3,
        val sched: (Double) -> Double = quadratic(),
        val onBest: suspend (Bin, Int) -> Unit = { _, _ -> Unit },
        val onProgress: suspend (Int, Int) -> Unit = { _, _ -> Unit }
) : Guillotine() {
    companion object {
        val log = LoggerFactory.getLogger(MCTSGuillotine::class.java)
        val marker = MarkerFactory.getMarker("MCTS_NODE")

        // Some annealing schedules

        fun constant(alpha: Double = 1.0): (Double) -> Double = { alpha }

        fun linear(alpha: Double): (Double) -> Double {
            val beta = 1 - alpha
            return { alpha + beta * it }
        }

        // Grows pretty slowly: [alpha, 1)
        fun quadratic(alpha: Double = 0.5, gamma: Double = 100.0): (Double) -> Double {
            val beta = 1 - alpha
            return { alpha + beta * it / sqrt(1 + it * it) }
        }

        // Grows quite fast: [alpha, 1)
        fun exponential(alpha: Double = 0.0, gamma: Double = 8.0): (Double) -> Double {
            val beta = 1 - alpha
            return { 1.0 - beta * exp(-gamma * it) }
        }

        // Logistic annealing schedule: [0.5, 1)
        fun sigmoid(alpha: Double = 0.5, gamma: Double = 8.0): (Double) -> Double {
            val beta = 1 - alpha
            return { alpha + beta * (2.0 / (1 + exp(-gamma * it)) - 1) }
        }

        // Logistic-ish but with slow ramp up and configurable initial value. [alpha, 1)
        fun slowRamp(alpha: Double = 0.5, gamma: Double = 8.0): (Double) -> Double {
            val beta = 1 - alpha
            return { alpha + beta / (1 + exp(-gamma * (2.0 * it - 1))) }
        }
    }

    /**
     * Expands a node by creating children for possible actions from partial solution.
     */
    fun expand(node: SearchTree) {
        if (node.items.isEmpty)
            return

        val item = node.items.head()
        val remainder = node.items.tail()
        val old = node.childRef.get()
        var result = old

        node.partial.vacancies.filter {
            it.width >= item.width && it.height >= item.height
        }.forEach { target ->
            val vhChild = sampler.insertItem(node.partial, item, target, Guillotine.Companion::makeVHSplit)
            result = result.prepend(SearchTree(vhChild, remainder, "${node.label}/V", node.depth + 1))

            val hvChild = sampler.insertItem(node.partial, item, target, Guillotine.Companion::makeHVSplit)
            result = result.prepend(SearchTree(hvChild, remainder, "${node.label}/H", node.depth + 1))
        }

        node.partial.vacancies.filter {
            it.width >= item.height && it.height >= item.width
        }.forEach { target ->
            val vhtChild = sampler.insertItem(node.partial, item.t(), target, Guillotine.Companion::makeVHSplit)
            result = result.prepend(SearchTree(vhtChild, remainder, "${node.label}/V'", node.depth + 1))

            val hvtChild = sampler.insertItem(node.partial, item.t(), target, Guillotine.Companion::makeHVSplit)
            result = result.prepend(SearchTree(hvtChild, remainder, "${node.label}/H'", node.depth + 1))
        }

        result = result.prepend(SearchTree(node.partial.reject(item), remainder, "${node.label}/R", node.depth + 1))

        node.childRef.compareAndSet(old, result)
    }

    // Gather debugging information
    fun optimalPath(node: SearchTree): List<SearchTree> {
        val child = node.children.minMap { it.bestScore } ?: return List.of(node)
        val tail = optimalPath(child)
        return tail.prepend(node)
    }

    override suspend fun solve(bin: Bin, items: List<Item>) = Solver(bin, items).solve()

    inner class Solver(bin: Bin, items: List<Item>) {
        private val root = SearchTree(bin, items)
        private var focus = 0.5 // Controls how often best performing child is selected. Increases each round.

        private suspend fun step(node: SearchTree, quota: AtomicInteger, load: AtomicInteger) {
            // Terminal state. Nothing left to do here except compute the score.
            if (node.items.isEmpty) {
                // TODO double check this
                if (node.best.get().first == Int.MAX_VALUE)
                    node.best.set(node.lossFunc(node.partial) to node.partial)

                return
            }

            // Exhausted our items quota for this round. Unroll stack and start next round.
            if (quota.get() <= 0)
                return

            if (node != root && exp(-node.localStats.get().n / 100.0) > Random.nextDouble()) {
                node.simulate(batchSize, quota)
                load.addAndGet(batchSize)
            }

            if (node.children.isEmpty) {
                // However, in order to control tree width, don't expand unless node has
                // sufficient samples. That is, we've encountered this node several times.
                if (node.localStats.get().n >= node.depth * batchSize)
                    expand(node)
            }

            if (quota.get() > 0)
                stepDown(node, quota, load)

            // TODO really need some pruning
        }

        private suspend fun stepDown(node: SearchTree, quota: AtomicInteger, load: AtomicInteger) {
            // Only examine one child per traversal, but skip simulating nodes that already have a lot of local samples
            val child = selectChild(node) ?: return

            // Recursive call
            step(child, quota, load)

            val childBest = child.best.get()
            val (childScore, childSolution) = childBest

            while (true) {
                val lastBest = node.best.get()

                if (childScore >= lastBest.first)
                    break

                if (node.best.compareAndSet(lastBest, childBest)) {
                    if (node == root)
                        onBest(childSolution, childScore)

                    break
                } else
                    log.debug("Retrying to propagate best score at ${node.label}")
            }

            node.descStats = node.children.map { it.descStats }.fold(node.localStats.get()) { a, b -> a + b }
        }

        // TODO use likelihood of node.bestScore to sort children
        fun selectChild(node: SearchTree): SearchTree? = when {
            Random.nextDouble() > focus -> node.children.shuffled().firstOrNull()
            Random.nextBoolean() -> node.children.minMap { it.bestScore }
            else -> node.children.minMap { it.localStats.get().lcb() }
        }

        suspend fun solve(): Bin {
            onProgress(0, rounds)
            (1..rounds).forEach {
                log.info("Starting round $it. Cores = $cores. Focus = $focus")
                val ctr = AtomicInteger(quota)
                if (cores > 1)
                    doSolveMultiCore(it, ctr)
                else
                    doSolveSingleCore(it, 0, ctr)

                log.info("Finished round $it. Quota = $ctr")
                focus = sched(it.toDouble() / rounds)
                onProgress(it, rounds)
                yield()

                if (log.isDebugEnabled && it % 25 == 0) {
                    val leaves = root.leafDepths()
                    log.debug("Leaves n=${leaves.length()} depths min=${leaves.min()} max=${leaves.max()} avg=${leaves.average()}")
                }
            }

            log.info("Finished with solution of ${root.solution.countItems()} items and ${root.solution.rejects.length()} rejects")

            if (log.isDebugEnabled) {
                val leaves = root.leafDepths()
                log.debug("Leaves n=${leaves.length()} depths min=${leaves.min()} max=${leaves.max()} avg=${leaves.average()}")
                val path = optimalPath(root).map { Tuple.of(it.label, it.localStats, it.localStats.get().lcb()) }.zipWithIndex()
                log.debug("Optimal path: $path")

                val statsGen1 = root.children.map { Tuple.of(it.label, it.descStats, it.descStats.lcb()) }
                log.debug("First generation stats: $statsGen1")
                log.debug("First generation local: ${root.children.map { Tuple.of(it.label, it.localStats, it.localStats.get().lcb()) }}")
            }

            return root.solution
        }

        private suspend fun doSolveMultiCore(iteration: Int, ctr: AtomicInteger) {
            coroutineScope {
                (1..cores).forEach { core ->
                    launch {
                        doSolveSingleCore(iteration, core, ctr)
                    }
                }
            }
        }

        private suspend fun doSolveSingleCore(iteration: Int, core: Int, ctr: AtomicInteger) {
            val load = AtomicInteger(0)

            if (iteration % 50 == 1)
                log.debug("Running solver step $iteration core $core in ${Thread.currentThread().name}")

            while (ctr.get() > 0)
                step(root, ctr, load)

            if (iteration % 50 == 1)
                log.debug("Core $core was responsible for ${load.get()} of he quota")
        }
    }
}
