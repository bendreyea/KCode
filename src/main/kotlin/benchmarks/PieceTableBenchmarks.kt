package org.editor.benchmarks

import kotlinx.benchmark.*
import org.editor.core.PieceTable
import org.editor.core.Rope
import kotlin.random.Random

@State(Scope.Thread)
@BenchmarkMode(Mode.All)
open class PieceTableBenchmark {

    private lateinit var pieceTable: PieceTable
    private lateinit var rope: Rope
    private lateinit var sampleData: ByteArray

    @Param("1024", "4096", "16384")
    var dataSize: Int = 0

    @Setup
    fun setup() {
        pieceTable = PieceTable.create()
        rope = Rope.create()
        sampleData = ByteArray(dataSize) { Random.nextBytes(1)[0] }
        // Preload data for delete/get benchmarks
        pieceTable.insert(0, sampleData)
        rope.insert(0, sampleData)
    }

    @Benchmark
    fun benchmarkInsert(blackhole: Blackhole) {
        pieceTable.insert(0, sampleData)
        blackhole.consume(pieceTable) // Prevent dead code elimination
    }

    @Benchmark
    fun benchmarkInsertRope(blackhole: Blackhole) {
        rope.insert(0, sampleData)
        blackhole.consume(rope)
    }

    @Benchmark
    fun benchmarkDelete(blackhole: Blackhole) {
        pieceTable.delete(0, dataSize / 2)
        blackhole.consume(pieceTable)
    }

    @Benchmark
    fun benchmarkDeleteRope(blackhole: Blackhole) {
        rope.delete(0, dataSize / 2)
        blackhole.consume(rope)
    }

    @Benchmark
    fun benchmarkGet(blackhole: Blackhole) {
        val result = pieceTable.get(0, dataSize / 4)
        blackhole.consume(result)
    }

    @Benchmark
    fun benchmarkGetRope(blackhole: Blackhole) {
        val result = rope.get(0, dataSize / 4)
        blackhole.consume(result)
    }
}
