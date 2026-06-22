package com.pkware.ahocorasick.benchmark

/**
 * A generated benchmark workload: a dictionary plus an input haystack in both [String] and UTF-8 [ByteArray]
 * forms. The UTF-8 form is precomputed so decode-cost benchmarks measure decoding, not generation.
 *
 * @property dictionary the patterns added to the matcher.
 * @property input the haystack to search, as a [String].
 * @property inputUtf8 the UTF-8 encoding of [input], precomputed so decode-cost benchmarks need not re-encode.
 */
class Corpus(val dictionary: List<String>, val input: String, val inputUtf8: ByteArray)
