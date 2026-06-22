package com.pkware.ahocorasick.benchmark

import com.pkware.ahocorasick.StringAhoCorasick
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

/** Measures the time to add all dictionary entries and call build(). */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
class BuildBenchmark {

    @Param("1000", "100000", "1000000")
    var dictionarySize: Int = 0

    @Param("ASCII", "UNICODE")
    var alphabet: Alphabet = Alphabet.ASCII

    @Param("SPARSE", "DENSE")
    var density: MatchDensity = MatchDensity.SPARSE

    private var dictionary: List<String> = emptyList()

    @Setup(Level.Trial)
    fun setup() {
        dictionary = CorpusGenerator.generateDictionary(SEED, dictionarySize, alphabet, density)
    }

    @Benchmark
    fun build(blackhole: Blackhole) {
        val matcher = StringAhoCorasick()
        matcher.addAll(dictionary)
        matcher.build()
        blackhole.consume(matcher)
    }

    private companion object {
        const val SEED = 42L
    }
}
