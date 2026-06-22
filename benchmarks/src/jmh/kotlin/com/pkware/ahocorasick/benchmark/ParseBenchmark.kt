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

/** Measures parse throughput over the current Stream API (the baseline the alloc-free API work improves). */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
class ParseBenchmark {

    @Param("1000", "100000", "1000000")
    var dictionarySize: Int = 0

    @Param("ASCII", "UNICODE")
    var alphabet: Alphabet = Alphabet.ASCII

    @Param("SPARSE", "DENSE")
    var density: MatchDensity = MatchDensity.SPARSE

    private var input: String = ""
    private var matcher: StringAhoCorasick = StringAhoCorasick()

    @Setup
    fun setup() {
        val corpus = CorpusGenerator.generate(
            seed = SEED,
            dictionarySize = dictionarySize,
            alphabet = alphabet,
            inputLength = INPUT_LENGTH,
            density = density,
        )
        input = corpus.input
        matcher = StringAhoCorasick().apply {
            addAll(corpus.dictionary)
            build()
        }
    }

    @Benchmark
    fun parse(blackhole: Blackhole) {
        matcher.parse(input).forEach(blackhole::consume)
    }

    private companion object {
        const val SEED = 42L
        const val INPUT_LENGTH = 1_000_000
    }
}
