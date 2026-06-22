package com.pkware.ahocorasick.benchmark

/**
 * Character repertoire a [CorpusGenerator] draws from.
 *
 * [ASCII] stays within U+0000..U+007F (one UTF-8 byte per char). [UNICODE] mixes ASCII letters with
 * Basic-Multilingual-Plane code points above U+007F — accented Latin (2-byte UTF-8) and CJK (3-byte UTF-8)
 * — so multi-byte UTF-8 behavior is exercised.
 *
 * Only single-`Char` BMP code points are used (no surrogate pairs / supplementary code points). This is
 * deliberate: the generator builds patterns and their suffixes via `substring`, which would split a
 * surrogate pair into lone, unencodable surrogates and break UTF-8 round-tripping. BMP coverage still
 * exercises the 2- and 3-byte UTF-8 paths that matter for the decode-cost (Axis 3) work.
 *
 * @property characters the pool of characters that generated words are drawn from.
 */
enum class Alphabet(val characters: List<Char>) {
    /** ASCII letters `a`–`z`; every character encodes to a single UTF-8 byte. */
    ASCII(('a'..'z').toList()),

    /** ASCII letters plus BMP accented Latin (2-byte UTF-8) and CJK (3-byte UTF-8) characters. */
    UNICODE(
        buildList {
            addAll('a'..'z')
            addAll('À'..'ÿ') // accented Latin, 2-byte UTF-8
            addAll('一'..'丯') // CJK Unified Ideographs sample, 3-byte UTF-8
        },
    ),
}

/**
 * Match density regime of a generated corpus.
 *
 * [matchesPerChar] is the nominal target used when generating the input. The generator aims for it; the
 * exact value is not contractual (Aho-Corasick suffix matches make density approximate). What is
 * contractual is that [SPARSE] and [DENSE] are clearly distinct regimes — see CorpusGeneratorTest.
 *
 * @property matchesPerChar the nominal target match rate for this regime.
 */
enum class MatchDensity(val matchesPerChar: Double) {
    /** Roughly one match per 100 characters — a match-sparse haystack. */
    SPARSE(0.01),

    /** Several matches per character — a match-dense haystack. */
    DENSE(2.5),
}
