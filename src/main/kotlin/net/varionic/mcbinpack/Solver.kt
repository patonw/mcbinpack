package net.varionic.mcbinpack

import io.vavr.collection.List

interface Solver {
    suspend fun solve(bin: Bin, items: List<Item>): Bin
}