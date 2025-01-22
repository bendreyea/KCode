package benchmarks

import kotlinx.benchmark.*
import org.editor.core.PieceTable
import kotlin.random.Random

@State(Scope.Thread)
class PieceTableBenchmark {

    private lateinit var pieceTable: PieceTable
    private lateinit var sampleData: ByteArray

    @Setup
    fun setup() {
        pieceTable = PieceTable.create()
        sampleData = ByteArray(1024) { Random.nextBytes(1)[0] } // Generate 1 KB of random data
    }

    @Benchmark
    fun benchmarkInsert() {
        pieceTable.insert(0, sampleData)
    }

    @Benchmark
    fun benchmarkDelete() {
        pieceTable.delete(0, sampleData.size / 2)
    }

    @Benchmark
    fun benchmarkGet() {
        pieceTable.get(0, sampleData.size / 4)
    }

    @Benchmark
    fun benchmarkLength() {
        pieceTable.length()
    }
}
