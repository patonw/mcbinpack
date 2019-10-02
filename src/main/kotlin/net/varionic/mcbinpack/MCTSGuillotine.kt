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

class SearchTree(val partial: Bin, val work: List<Item>, val label: String = "", val depth: Int = 0) {
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

    fun simulate(): Pair<Bin,Int> {
        val result = work.fold(partial) { bin, item -> sampler.insertItem(bin, item) }
        val score = lossFunc(result)
        //samples.add(score)

        if (score < bestScore) {
//            if (log.isInfoEnabled(marker)) log.info(marker, "New best $score @ $label with ${work.size()} items left to go")
            bestScore = score
            solution = result
        }

        return result to score
    }

    fun simulate(times: Int): List<Int> {
        val scores = (1..times).map { simulate() }.map { it.second }.toVavrList()
        val newStats = StatPad.of(scores)

        localStats += newStats

        return scores
    }

    fun simulate(times: Int, quota: AtomicInteger) = simulate(times).also { quota.addAndGet(-times) }

    fun leafDepths(): List<Int> {
        if (children.isEmpty())
            return List.of(0)
        return children.flatMap { it.leafDepths() }.map { it + 1}.toVavrList()
    }
}

class MCTSGuillotine(bin: Bin, work: List<Item>, val rounds: Int = 200, val decay: Double = rounds/10.0, val quota: Int = 500, val onBest: suspend (Bin, Int) -> Unit = { _, _ -> Unit }, val onProgress: suspend (Int, Int) -> Unit = { _, _ -> Unit }) : Guillotine() {
    companion object {
        val log = LoggerFactory.getLogger(MCTSGuillotine::class.java)
        val marker = MarkerFactory.getMarker("MCTS_NODE")
    }

    val sampler = RandomGuillotineFF()
    val root = SearchTree(bin, work)
    var focus = 0.5 // Controls how often best performing child is selected. Increases each round.

    /**
     * Expands a node by creating children for possible actions from partial solution.
     */
    fun expand(node: SearchTree) {
        if (node.work.isEmpty)
            return

        val item = node.work.head()
        val remainder = node.work.tail()

        val vhChild = sampler.insertItem(node.partial, item, this::makeVHSplit)
        node.children.add(SearchTree(vhChild, remainder, "${node.label}/V", node.depth+1))

        val vhtChild = sampler.insertItem(node.partial, item.t(), this::makeVHSplit)
        node.children.add(SearchTree(vhtChild, remainder, "${node.label}/V'", node.depth+1))

        val hvChild = sampler.insertItem(node.partial, item, this::makeVHSplit)
        node.children.add(SearchTree(hvChild, remainder, "${node.label}/H", node.depth+1))

        val hvtChild = sampler.insertItem(node.partial, item.t(), this::makeVHSplit)
        node.children.add(SearchTree(hvtChild, remainder, "${node.label}/H'", node.depth+1))

        node.children.add(SearchTree(node.partial.reject(item), remainder, "${node.label}/R", node.depth+1))
    }

    suspend fun step(node: SearchTree, quota: AtomicInteger) {
        // TODO Should return some kind of statistic to reflect potential performance of node that can be merged in constant time
//        if (log.isDebugEnabled(marker)) log.debug(marker, "Stepping into [${node.bestScore}]${node.label} with quota $quota")

        // Terminal state. Nothing left to do here except compute the score.
        if (node.work.isEmpty) {
            if (node.bestScore == Int.MAX_VALUE)
                node.bestScore = node.lossFunc(node.partial)

            return
        }

        // Exhausted our work quota for this round. Unroll stack and start next round.
        if (quota.get() < 0)
            return

        // If this is a non-terminal state and the current node is a leaf, expand it
        // and run some simulations to populate children
        if (node.localStats.n >= node.depth && node.children.isEmpty()) {
//            if (log.isDebugEnabled) log.debug("Expanding ${node.label}")
            expand(node)

            node.children.forEach {
                it.simulate(3, quota)
            }
        }

        // Handle children based on selection criteria until we've exhausted work quota (or arbitrarily quit)
        while (true) {
            val child = selectChild(node) ?: break

            // Do some minimum work on each interior node to keep things moving
            // if (node.localStats.n <= node.descStats.n / 10)
            //if (Random.nextDouble() > node.cutoff)
                child.simulate(3, quota)

            // Recurse into subtree
            step(child, quota)

            // TODO track scores from all simulations & prune underperforming children using some statistical measure

            if (quota.get() < 0)
                break

            if (Random.nextBoolean())
                break
        }

        node.children.forEach { child ->
            // Propagate results back up the tree
            if (child.bestScore < node.bestScore) {
                node.bestScore = child.bestScore
                node.solution = child.solution

                if (node === root)
                    onBest(child.solution!!, child.bestScore)
            }
        }

        node.descStats = node.children.map { it.descStats }.fold(node.localStats) { a,b -> a+b }
//        if (log.isDebugEnabled(marker)) {
//            log.debug(marker, "Stats for ${node.label}")
//            log.debug(marker, "Subtree Stats are ${node.descStats} with lcb ${node.descStats.lcb()}")
//            log.debug(marker, "Local Stats are ${node.localStats} with lcb ${node.localStats.lcb()}")
//        }

//        if (log.isDebugEnabled) log.debug("Finished [${node.bestScore}]${node.label} with quota $quota")
    }

    // Does using the LCB really make sense here? The minimal score is necessarily an outlier.
    // Just because this partial solution is worse than its peers doesn't mean its not part of the optimum.
    fun selectChild(node: SearchTree): SearchTree? {
        val minChild = node.children.minBy { it.bestScore }

//        val p = 1.0 - exp(-node.descStats.n/10000.0)

        // Probably doesn't have much effect
        val p = if (minChild != null && minChild.descStats.min > node.localStats.min)
            0.5
        else
            focus

        val x = Random.nextDouble()

        if (x < 0.01)
            log.debug("Greedy probability is $p at ${node.depth} with ${node.descStats.n} samples")

        return when {
            x > p -> node.children.shuffled().firstOrNull()
            Random.nextBoolean() -> node.children.minBy { it.localStats.lcb() }
            else -> minChild
        }
    }

    private fun energy(minChild: SearchTree, node: SearchTree): Double {
        val delta = (minChild.descStats.lcb()) - node.descStats.lcb()
        val std = if (node.localStats.vrc > 0) sqrt(node.localStats.vrc) else 1.0
        val energy = delta / std

        if (Random.nextDouble() < 0.01)
            log.debug("$delta Energy is $energy")
        return energy
    }

    suspend fun solve(): Bin {
        onProgress(0, rounds)
        (1..rounds).forEach {
            log.info("Starting round $it. Focus = $focus")
            val ctr = AtomicInteger(quota)
            step(root, ctr)
            log.info("Finished round $it. Quota = $ctr")
            focus = 1 - exp(  - (it)/decay)
            onProgress(it, rounds)
            yield()
        }

        log.info("Finished with solution of ${root.solution?.countItems()} items and ${root.solution?.rejects?.length()} rejects")

        if (log.isDebugEnabled) {
            val leaves = root.leafDepths()
            log.debug("Leaves n=${leaves.length()} depths min=${leaves.min()} max=${leaves.max()} avg=${leaves.average()}")
            val path = optimalPath(root).map { it.label to (it.descStats + it.localStats) }.zipWithIndex()
            log.debug("Optimal path: ${path}")

            val statsGen1 = root.children.map { Tuple.of(it.label, it.descStats, it.descStats.lcb()) }
            log.debug("First generation stats: $statsGen1")
            log.debug("First generation local: ${root.children.map { Tuple.of(it.label, it.localStats, it.localStats.lcb()) }}")
        }

        return root.solution!!
    }

    fun optimalPath(node: SearchTree): List<SearchTree> {
        val child = node.children.minBy { it.bestScore } ?: return List.of(node)
        val tail = optimalPath(child)
        return tail.prepend(node)
    }
}
