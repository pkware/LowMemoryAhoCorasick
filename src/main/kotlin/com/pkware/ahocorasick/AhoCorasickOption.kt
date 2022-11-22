package com.pkware.ahocorasick

/**
 * Options which change the behavior of [AhoCorasickBase].
 */
public enum class AhoCorasickOption {

    /**
     * Indicates that keys should be treated in a case-insensitive manner.
     *
     * Eg: The key `CaT` is treated as equal to `cat`.
     */
    CASE_INSENSITIVE,

    /**
     * Indicates only keys surrounded by white space should be found.
     *
     * White space is defined as any character which matches the regex `\s`, or the start and end of a string (eg:
     * `Hello` in `Hello There` is surrounded by white space). Keys that start or end in white space will need
     * _additional_ white space around them in order to be found.
     */
    WHOLE_WORDS_ONLY,
}
