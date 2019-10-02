package net.varionic.mcbinpack

import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
data class SampleResult(val width: Int, val height: Int, val score: Int, val splits: List<Divider>, val items: List<Rect>, val rejects: List<Rect>)
data class SampleQuery(val width: Int, val height: Int, val items: List<Item>?, val solver: String = "random-guillotine")

class JsonRenderer: BaseRenderer<SampleResult>() {
    override fun render(bin: Bin): SampleResult {
        val splits = renderDividers(bin)
        val items = renderItems(bin)
        val score = computeScore(bin)
        val rejects = renderRejects(bin)
        return SampleResult(bin.width, bin.height, score, splits, items, rejects)
    }
}