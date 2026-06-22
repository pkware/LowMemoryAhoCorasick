package com.pkware.ahocorasick.benchmark

import com.pkware.ahocorasick.StringAhoCorasick
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

/**
 * Quantifies the byte[] -> String decode cost that String-only benchmarks ignore (Axis 3). The delta between
 * decodeThenParse and parsePreDecoded is the decode tax a future byte[] matcher would remove.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
class DecodeBenchmark {

    @Param("1000", "100000", "1000000")
    var dictionarySize: Int = 0

    @Param("ASCII", "UNICODE")
    var alphabet: Alphabet = Alphabet.ASCII

    @Param("SPARSE", "DENSE")
    var density: MatchDensity = MatchDensity.SPARSE

    private var inputUtf8: ByteArray = ByteArray(0)
    private var preDecoded: String = ""
    private var matcher: StringAhoCorasick = StringAhoCorasick()

    @Setup
    fun setup() {
        val corpus = CorpusGenerator.generate(SEED, dictionarySize, alphabet, INPUT_LENGTH, density)
        inputUtf8 = corpus.inputUtf8
        preDecoded = corpus.input
        matcher = StringAhoCorasick().apply {
            addAll(corpus.dictionary)
            build()
        }
    }

    @Benchmark
    fun decodeOnly(blackhole: Blackhole) {
        blackhole.consume(String(inputUtf8, Charsets.UTF_8))
    }

    @Benchmark
    fun decodeThenParse(blackhole: Blackhole) {
        val decoded = String(inputUtf8, Charsets.UTF_8)
        matcher.parse(decoded).forEach(blackhole::consume)
    }

    @Benchmark
    fun parsePreDecoded(blackhole: Blackhole) {
        matcher.parse(preDecoded).forEach(blackhole::consume)
    }

    private companion object {
        const val SEED = 42L
        const val INPUT_LENGTH = 1_000_000
    }
}
