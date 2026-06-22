package com.pkware.ahocorasick

/**
 * An Aho-Corasick structure optimized for strings which uses a low amount of memory.
 *
 * One can populate this structure by repeatedly calling [add] and then calling [build] once all strings have been
 * added.
 *
 * This structure builds on top of [AhoCorasickBase], using only ~5 `int`s per entry by having each string added to this
 * structure associated with its length. Given the location a match was found at, along with a length, the original key
 * can then be calculated again and returned in a [AhoCorasickResult]. This prevents one from having to keep any of the
 * original strings in memory. This does mean that if [isCaseSensitive] is `false`, __the result will have the casing in
 * the input text__. Eg: With the key `cat`, and the input `CaT`, the value returned would be `CaT`. If one needs to
 * preserve casing, the generic form of this structure, [AhoCorasick], should be used at the cost of more memory.
 *
 * @param options Options which change the default key matching behavior.
*/
public class StringAhoCorasick @JvmOverloads constructor(options: Set<AhoCorasickOption> = emptySet()) :
    AhoCorasickBase<String>(options) {

    /**
     * Adds a string to this structure. Null-op on duplicate string.
     */
    public fun add(string: String): Unit = addEntry(string, string.length)

    /**
     * Adds all strings to this structure. Null-op for strings that are duplicates.
     */
    public fun addAll(strings: Iterable<String>): Unit = strings.forEach(::add)

    override fun generateResult(index: Int, value: Int, input: String): AhoCorasickResult<String> {

        val start = index - value
        return AhoCorasickResult(start, index, input.substring(start, index))
    }
}
