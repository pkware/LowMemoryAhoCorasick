package com.pkware.ahocorasick.benchmark

import kotlin.random.Random

/**
 * Deterministic, seeded generator of benchmark [Corpus] data.
 *
 * All randomness comes from a single [Random] seeded by `seed`; there is no other entropy source, so a given
 * (seed + params) tuple always produces an identical corpus.
 */
object CorpusGenerator {

    private const val MIN_PATTERN_LENGTH = 3
    private const val MAX_PATTERN_LENGTH = 12
    private const val SPARSE_INJECTION_GAP = 100

    /** Generates the dictionary alone (used by build benchmarks, which do not need an input haystack). */
    fun generateDictionary(seed: Long, dictionarySize: Int, alphabet: Alphabet, density: MatchDensity): List<String> =
        generateDictionary(Random(seed), dictionarySize, alphabet, density)

    /** Generates a full corpus: dictionary + input haystack (as [String] and UTF-8 bytes). */
    fun generate(
        seed: Long,
        dictionarySize: Int,
        alphabet: Alphabet,
        inputLength: Int,
        density: MatchDensity,
    ): Corpus {
        val random = Random(seed)
        val dictionary = generateDictionary(random, dictionarySize, alphabet, density)
        val input = buildInput(random, dictionary, alphabet, inputLength, density)
        return Corpus(dictionary, input, input.toByteArray(Charsets.UTF_8))
    }

    private fun generateDictionary(
        random: Random,
        dictionarySize: Int,
        alphabet: Alphabet,
        density: MatchDensity,
    ): List<String> {
        val words = LinkedHashSet<String>()

        if (density == MatchDensity.DENSE) {
            // Suffix-enrich: for each base word, also add its suffixes down to length 1. A single occurrence
            // of a base word then yields one match per suffix at consecutive end positions, driving density
            // above one match per char once base words are packed contiguously (Task 2.5).
            while (words.size < dictionarySize) {
                val length = random.nextInt(MIN_PATTERN_LENGTH, MAX_PATTERN_LENGTH + 1)
                val base = randomWord(random, alphabet, length)
                var start = 0
                while (start < base.length && words.size < dictionarySize) {
                    words.add(base.substring(start))
                    start++
                }
            }
        } else {
            while (words.size < dictionarySize) {
                val length = random.nextInt(MIN_PATTERN_LENGTH, MAX_PATTERN_LENGTH + 1)
                words.add(randomWord(random, alphabet, length))
            }
        }

        return words.toList()
    }

    private fun buildInput(
        random: Random,
        dictionary: List<String>,
        alphabet: Alphabet,
        inputLength: Int,
        density: MatchDensity,
    ): String {
        if (inputLength == 0) return ""

        return when (density) {
            MatchDensity.SPARSE -> buildSparseInput(random, dictionary, alphabet, inputLength)
            MatchDensity.DENSE -> buildDenseInput(random, dictionary, inputLength)
        }
    }

    /**
     * Mostly non-matching filler with a dictionary word injected roughly every [SPARSE_INJECTION_GAP] chars.
     * Filler is drawn one char at a time so it rarely forms accidental matches.
     */
    private fun buildSparseInput(
        random: Random,
        dictionary: List<String>,
        alphabet: Alphabet,
        inputLength: Int,
    ): String {
        val chars = alphabet.characters
        val sb = StringBuilder(inputLength)
        while (sb.length < inputLength) {
            if (sb.length % SPARSE_INJECTION_GAP == 0 && dictionary.isNotEmpty()) {
                sb.append(dictionary[random.nextInt(dictionary.size)])
            } else {
                sb.append(chars[random.nextInt(chars.size)])
            }
        }
        return sb.substring(0, inputLength)
    }

    /**
     * Dictionary words packed back-to-back. Combined with the suffix-enriched DENSE dictionary (Task 2.4),
     * each packed word emits a match per suffix, pushing density above one match per char.
     */
    private fun buildDenseInput(random: Random, dictionary: List<String>, inputLength: Int): String {
        val sb = StringBuilder(inputLength)
        while (sb.length < inputLength) {
            sb.append(dictionary[random.nextInt(dictionary.size)])
        }
        return sb.substring(0, inputLength)
    }

    private fun randomWord(random: Random, alphabet: Alphabet, length: Int): String {
        val chars = alphabet.characters
        return buildString(length) {
            repeat(length) { append(chars[random.nextInt(chars.size)]) }
        }
    }
}
