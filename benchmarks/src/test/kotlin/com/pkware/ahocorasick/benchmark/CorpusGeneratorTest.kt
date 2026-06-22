package com.pkware.ahocorasick.benchmark

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CorpusGeneratorTest {

    @Test
    fun `same seed and params produce identical corpus`() {
        val a = CorpusGenerator.generate(
            seed = 42L,
            dictionarySize = 500,
            alphabet = Alphabet.ASCII,
            inputLength = 10_000,
            density = MatchDensity.SPARSE,
        )
        val b = CorpusGenerator.generate(
            seed = 42L,
            dictionarySize = 500,
            alphabet = Alphabet.ASCII,
            inputLength = 10_000,
            density = MatchDensity.SPARSE,
        )

        assertThat(b.dictionary).isEqualTo(a.dictionary)
        assertThat(b.input).isEqualTo(a.input)
        assertThat(b.inputUtf8.toList()).isEqualTo(a.inputUtf8.toList())
    }

    @Test
    fun `different seed produces different input`() {
        val a = CorpusGenerator.generate(7L, 500, Alphabet.ASCII, 10_000, MatchDensity.SPARSE)
        val b = CorpusGenerator.generate(8L, 500, Alphabet.ASCII, 10_000, MatchDensity.SPARSE)

        assertThat(b.input).isNotEqualTo(a.input)
    }

    @Test
    fun `ASCII corpus contains only single-byte characters`() {
        val corpus = CorpusGenerator.generate(1L, 200, Alphabet.ASCII, 5_000, MatchDensity.SPARSE)

        assertThat(corpus.input.all { it.code <= 0x7F }).isTrue()
        assertThat(corpus.dictionary.all { word -> word.all { it.code <= 0x7F } }).isTrue()
        // All-ASCII text encodes to exactly one UTF-8 byte per char.
        assertThat(corpus.inputUtf8.size).isEqualTo(corpus.input.length)
    }

    @Test
    fun `UNICODE corpus contains multi-byte characters and round-trips through UTF-8`() {
        val corpus = CorpusGenerator.generate(1L, 200, Alphabet.UNICODE, 5_000, MatchDensity.SPARSE)

        assertThat(corpus.input.any { it.code > 0x7F }).isTrue()
        // UTF-8 of non-ASCII text is strictly larger than the char count.
        assertThat(corpus.inputUtf8.size).isGreaterThan(corpus.input.length)
        // inputUtf8 is the faithful UTF-8 encoding of input.
        assertThat(String(corpus.inputUtf8, Charsets.UTF_8)).isEqualTo(corpus.input)
    }

    @Test
    fun `dictionary has the requested size and valid patterns`() {
        val corpus = CorpusGenerator.generate(3L, 1_000, Alphabet.ASCII, 1_000, MatchDensity.SPARSE)

        assertThat(corpus.dictionary).hasSize(1_000)
        assertThat(corpus.dictionary.toSet()).hasSize(1_000) // no duplicates
        assertThat(corpus.dictionary.none { it.isEmpty() }).isTrue() // library rejects empty keys
    }

    @Test
    fun `dense dictionary is suffix-enriched`() {
        // A suffix-enriched dictionary contains, for some words, their shorter suffixes too. This is what
        // lets a single input region produce many matches (DENSE > 1 match/char in Task 2.5).
        val dense = CorpusGenerator.generateDictionary(5L, 2_000, Alphabet.ASCII, MatchDensity.DENSE)

        val denseSet = dense.toSet()
        val wordsWithSuffixInDictionary = dense.count { word ->
            word.length > 1 && word.substring(1) in denseSet
        }
        assertThat(wordsWithSuffixInDictionary).isGreaterThan(0)
    }

    private fun matchesPerChar(corpus: Corpus): Double {
        val matcher = com.pkware.ahocorasick.StringAhoCorasick().apply {
            addAll(corpus.dictionary)
            build()
        }
        val matches = matcher.parse(corpus.input).count()
        return matches.toDouble() / corpus.input.length
    }

    @Test
    fun `sparse corpus stays below 0,05 matches per char`() {
        val corpus = CorpusGenerator.generate(11L, 1_000, Alphabet.ASCII, 50_000, MatchDensity.SPARSE)
        val rate = matchesPerChar(corpus)
        assertThat(rate).isLessThan(0.05)
        // Lower bound guards against a regression that drops injection to zero.
        assertThat(rate).isGreaterThan(0.0)
    }

    @Test
    fun `dense corpus exceeds 1 match per char`() {
        val corpus = CorpusGenerator.generate(11L, 1_000, Alphabet.ASCII, 50_000, MatchDensity.DENSE)
        assertThat(matchesPerChar(corpus)).isGreaterThan(1.0)
    }

    @Test
    fun `dense is denser than sparse for the same size`() {
        val sparse = CorpusGenerator.generate(11L, 1_000, Alphabet.ASCII, 50_000, MatchDensity.SPARSE)
        val dense = CorpusGenerator.generate(11L, 1_000, Alphabet.ASCII, 50_000, MatchDensity.DENSE)
        assertThat(matchesPerChar(dense)).isGreaterThan(matchesPerChar(sparse))
    }
}
