package net.varionic.mcbinpack

import io.vavr.Tuple
import io.vavr.collection.List
import io.vavr.kotlin.toVavrList
import kotlinx.coroutines.yield
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

class SearchTree(val partial: Bin, val items: List<Item>, val label: String = "", val depth: Int = 0) {
    companion object {
        val log = LoggerFactory.getLogger(SearchTree::class.java)
        val marker = MarkerFactory.getMarker("MCTS_NODE")
    }

    val sampler = RandomGuillotineFF()
    val lossFunc = ::computeScore
    val children: MutableList<SearchTree> = mutableListOf()
    var bestScore: Int = Int.MAX_VALUE
    var solution: Bin? = null
    var descStats: StatPad = StatPad.empty
    var localStats: StatPad = StatPad.empty
    val cutoff = 1.0/(depth+1)  // Artificially push work further from root.

    private fun simulate(): Pair<Bin,Int> {
        val result = items.fold(partial) { bin, item -> sampler.insertItem(bin, item) }
        val score = lossFunc(result)
        //samples.add(score)

        if (score < bestScore) {
//            if (log.isInfoEnabled(marker)) log.info(marker, "New best $score @ $label with ${work.size()} items left to go")
            bestScore = score
            solution = result
        }

        return result to score
    }

    private fun simulate(times: Int): List<Int> {
        val scores = (1..times).map { simulate() }.map { it.second }.toVavrList()
        val newStats = StatPad.of(scores)

        localStats += newStats

        return scores
    }

    fun simulate(times: Int, quota: AtomicInteger) = simulate(times).also { quota.addAndGet(-times) }

    // For profiling tree structure
    fun leafDepths(): List<Int> {
        if (children.isEmpty())
            return List.of(0)
        return children.flatMap { it.leafDepths() }.map { it + 1}.toVavrList()
    }
}

class MCTSGuillotine(
        val sampler: Guillotine = RandomGuillotineFF(),
        val rounds: Int = 300,
        val quota: Int = 500,
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
            return { alpha + beta * (2.0 / (1 + exp(-gamma * it )) - 1)}
        }

        // Logistic-ish but with slow ramp up and configurable initial value. [alpha, 1)
        fun slowRamp(alpha: Double = 0.5, gamma: Double = 8.0): (Double) -> Double {
            val beta = 1 - alpha
            return { alpha + beta / (1 + exp(-gamma * (2.0*it - 1)))}
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

        val vhChild = sampler.insertItem(node.partial, item, this::makeVHSplit)
        node.children.add(SearchTree(vhChild, remainder, "${node.label}/V", node.depth+1))

        val vhtChild = sampler.insertItem(node.partial, item.t(), this::makeVHSplit)
        node.children.add(SearchTree(vhtChild, remainder, "${node.label}/V'", node.depth+1))

        val hvChild = sampler.insertItem(node.partial, item, this::makeHVSplit)
        node.children.add(SearchTree(hvChild, remainder, "${node.label}/H", node.depth+1))

        val hvtChild = sampler.insertItem(node.partial, item.t(), this::makeHVSplit)
        node.children.add(SearchTree(hvtChild, remainder, "${node.label}/H'", node.depth+1))

        node.children.add(SearchTree(node.partial.reject(item), remainder, "${node.label}/R", node.depth+1))
    }

    // Gather debugging information
    fun optimalPath(node: SearchTree): List<SearchTree> {
        val child = node.children.minBy { it.bestScore } ?: return List.of(node)
        val tail = optimalPath(child)
        return tail.prepend(node)
    }

    override suspend fun solve(bin: Bin, items: List<Item>) = Solver(bin, items).solve()

    inner class Solver(bin: Bin, items: List<Item>) {
        private val root = SearchTree(bin, items)
        private var focus = 0.5 // Controls how often best performing child is selected. Increases each round.

        private suspend fun step(node: SearchTree, quota: AtomicInteger) {
            // Terminal state. Nothing left to do here except compute the score.
            if (node.items.isEmpty) {
                if (node.bestScore == Int.MAX_VALUE)
                    node.bestScore = node.lossFunc(node.partial)

                return
            }

            // Exhausted our items quota for this round. Unroll stack and start next round.
            if (quota.get() <= 0)
                return

            if (node != root && exp(-node.localStats.n / 100.0) > Random.nextDouble())
                node.simulate(batchSize, quota)

            // If this is a non-terminal state and the current node is a leaf, expand it
            // and run some simulations to populate stats for child selection.
            if (node.children.isEmpty()) {
                // However, don't expand unless node has sufficient samples to prevent creating
                // lots of one-off nodes that are never reached more than a few times.
                if (node.localStats.n >= node.depth)
                    expand(node)
            }

            // Only examine one child per traversal, but skip simulating nodes that already have a lot of local samples
            val child = selectChild(node) ?: return

            // Recursive call
            step(child, quota)

            // Propagate results back up the tree
            if (child.bestScore < node.bestScore) {
                node.bestScore = child.bestScore
                node.solution = child.solution

                if (node === root) {
                    onBest(child.solution!!, child.bestScore)
                }
            }

            // TODO remove this debugging logic
            if (child.bestScore < root.bestScore) {
                log.debug(">>> New global best ${child.bestScore} at ${child.label} <<<")

                root.bestScore = child.bestScore
                root.solution = child.solution
                onBest(child.solution!!, child.bestScore)
            }

            node.descStats = node.children.map { it.descStats }.fold(node.localStats) { a,b -> a+b }
        }

        fun selectChild(node: SearchTree): SearchTree? = when {
            Random.nextDouble() > focus -> node.children.shuffled().firstOrNull()
            Random.nextBoolean() -> node.children.minBy { it.bestScore }
            else -> node.children.minBy { it.localStats.lcb() }
        }

        suspend fun solve(): Bin {
            onProgress(0, rounds)
            (1..rounds).forEach {
                log.info("Starting round $it. Focus = $focus")
                val ctr = AtomicInteger(quota)
                while (ctr.get() > 0)
                    step(root, ctr)
                log.info("Finished round $it. Quota = $ctr")
                focus = sched(it.toDouble()/rounds)
                onProgress(it, rounds)
                yield()


                if (log.isDebugEnabled && it % 25 == 0) {
                    val leaves = root.leafDepths()
                    log.debug("Leaves n=${leaves.length()} depths min=${leaves.min()} max=${leaves.max()} avg=${leaves.average()}")
                }
            }

            log.info("Finished with solution of ${root.solution?.countItems()} items and ${root.solution?.rejects?.length()} rejects")

            if (log.isDebugEnabled) {
                val leaves = root.leafDepths()
                log.debug("Leaves n=${leaves.length()} depths min=${leaves.min()} max=${leaves.max()} avg=${leaves.average()}")
                val path = optimalPath(root).map { Tuple.of(it.label, it.localStats, it.localStats.lcb()) }.zipWithIndex()
                log.debug("Optimal path: $path")

                val statsGen1 = root.children.map { Tuple.of(it.label, it.descStats, it.descStats.lcb()) }
                log.debug("First generation stats: $statsGen1")
                log.debug("First generation local: ${root.children.map { Tuple.of(it.label, it.localStats, it.localStats.lcb()) }}")
            }

            return root.solution!!
        }
    }
}
