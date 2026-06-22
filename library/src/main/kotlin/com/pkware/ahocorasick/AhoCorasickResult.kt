package com.pkware.ahocorasick

/**
 * Stores information about a match found in an Aho-Corasick structure.
 *
 * @param start The starting index of the key in the text given to the Aho-Corasick.
 * @param end The ending index of the key in the text given to the Aho-Corasick.
 * @param value The value of the match.
 */
public data class AhoCorasickResult<T>(public val start: Int, public val end: Int, public val value: T)
