package net.varionic.mcbinpack

import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
data class SampleResult(val width: Int, val height: Int, val score: Int, val splits: List<Divider>, val items: List<Rect>, val rejects: List<Rect>)

data class MCTSParams(
        val rounds: Int = 300,
        val quota: Int = 500,
        val batchSize: Int = 3,
        val cores: Int = 1,
        val scheduler: String = "sigmoid",
        val alpha: Double = 0.5,
        val gamma: Double = 8.0)

fun MCTSParams.makeScheduler(): (Double) -> Double {
    return when (scheduler.toLowerCase()) {
        "constant" -> MCTSGuillotine.constant(alpha)
        "linear" -> MCTSGuillotine.linear(alpha)
        "quadratic" -> MCTSGuillotine.quadratic(alpha, gamma)
        "sigmoid" -> MCTSGuillotine.sigmoid(alpha, gamma)
        "slowramp" -> MCTSGuillotine.slowRamp(alpha, gamma)
        else -> MCTSGuillotine.exponential(alpha, gamma)
    }
}

data class SampleQuery(
        val solver: String = "random-guillotine",
        val params: MCTSParams? = null,
        val width: Int = 200,
        val height: Int = 200,
        val items: List<Item>? = null)

class JsonRenderer: BaseRenderer<SampleResult>() {
    override fun render(bin: Bin): SampleResult {
        val splits = renderDividers(bin)
        val items = renderItems(bin)
        val score = computeScore(bin)
        val rejects = renderRejects(bin)
        return SampleResult(bin.width, bin.height, score, splits, items, rejects)
    }
}