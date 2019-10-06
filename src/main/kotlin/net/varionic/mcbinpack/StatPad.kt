package net.varionic.mcbinpack

import io.vavr.collection.List
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

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

    fun lcb(z: Double = 2.0) = if (n > 1) mean - z * sqrt(vrc / n) else Double.NEGATIVE_INFINITY
}