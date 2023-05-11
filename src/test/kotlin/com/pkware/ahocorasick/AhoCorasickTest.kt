package com.pkware.ahocorasick

import com.google.common.truth.Truth.assertThat
import com.pkware.ahocorasick.AhoCorasickOption.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.Locale
import java.util.stream.Stream
import kotlin.streams.toList

class AhoCorasickTest {

    @ParameterizedTest
    @MethodSource("emptyAhoCorasickStructures")
    internal fun `can build with no added strings`(ahoCorasick: StringAhoCorasickWrapper) {
        assertDoesNotThrow { ahoCorasick.build() }
    }

    @ParameterizedTest
    @MethodSource("emptyAhoCorasickStructures")
    internal fun `can search words with no added strings`(ahoCorasick: StringAhoCorasickWrapper) {
        ahoCorasick.build()
        assertThat(ahoCorasick.parse("nothing expected to be found")).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("emptyAhoCorasickStructures")
    internal fun `throws error when build is called twice`(ahoCorasick: StringAhoCorasickWrapper) {
        ahoCorasick.build()
        assertThrows<IllegalStateException> { ahoCorasick.build() }
    }

    @ParameterizedTest
    @MethodSource("emptyAhoCorasickStructures")
    internal fun `throws error when adding empty string`(ahoCorasick: StringAhoCorasickWrapper) {
        assertThrows<IllegalArgumentException> { ahoCorasick.buildWith("") }
    }

    @ParameterizedTest
    @MethodSource("emptyAhoCorasickStructures")
    internal fun `throws error when attempting to find strings before building`(ahoCorasick: StringAhoCorasickWrapper) {
        assertThrows<IllegalStateException> { ahoCorasick.parse("Should throw") }
    }

    @ParameterizedTest
    @MethodSource("emptyAhoCorasickStructures")
    internal fun `throws error when objects are added after built has been called`(
        ahoCorasick: StringAhoCorasickWrapper
    ) {
        ahoCorasick.build()
        assertThrows<IllegalStateException> { ahoCorasick.addString("value") }
    }

    @ParameterizedTest
    @MethodSource("emptyAhoCorasickStructures")
    internal fun `detects multiple words ending at same location`(ahoCorasick: StringAhoCorasickWrapper) {

        ahoCorasick.buildWith("bobcat", "cat", "at")

        val results = ahoCorasick.parse("I have a bobcat")
        assertThat(results).containsExactly("bobcat", "cat", "at")
    }

    @ParameterizedTest
    @MethodSource("emptyAhoCorasickStructures")
    internal fun `words are found in ascending order by ending position, and descending size from same position`(
        ahoCorasick: StringAhoCorasickWrapper
    ) {
        ahoCorasick.buildWith("cat", "at", "catapult", "tap", "a", "t")

        val results = ahoCorasick.parse("catapult")
        assertThat(results).containsExactly("a", "cat", "at", "t", "a", "tap", "catapult", "t").inOrder()
    }

    @ParameterizedTest
    @MethodSource("emptyAhoCorasickStructures")
    internal fun `detects partially overlapping words`(ahoCorasick: StringAhoCorasickWrapper) {

        ahoCorasick.buildWith("baby", "byte")

        val results = ahoCorasick.parse("babyte")
        assertThat(results).containsExactly("baby", "byte")
    }

    @ParameterizedTest
    @MethodSource("emptyAhoCorasickStructures")
    internal fun `handles non ASCII characters`(ahoCorasick: StringAhoCorasickWrapper) {

        ahoCorasick.buildWith("❤️", "✨", "🐱")

        val results = ahoCorasick.parse("Hows ❤️ it ✨ going 🐱 bestie")
        assertThat(results).containsExactly("❤️", "✨", "🐱")
    }

    @ParameterizedTest
    @MethodSource("emptyAhoCorasickStructures")
    internal fun `is case sensitive by default`(ahoCorasick: StringAhoCorasickWrapper) {

        ahoCorasick.buildWith("cAt", "CaT")

        val results = ahoCorasick.parse("CAT CaT CAt Cat cAT caT cAt cat")
        assertThat(results).containsExactly("cAt", "CaT")
    }

    @ParameterizedTest
    @MethodSource("caseInsensitiveAhoCorasickStructures")
    internal fun `supports case insensitive mode`(ahoCorasick: StringAhoCorasickWrapper) {

        ahoCorasick.buildWith("cat")

        val results = ahoCorasick.parse("CAT CaT CAt Cat cAT caT cAt cat").map {
            // Generic version will match original casing, optimized will match casing in input
            it.lowercase(Locale.ROOT)
        }
        assertThat(results).containsExactly("cat", "cat", "cat", "cat", "cat", "cat", "cat", "cat")
    }

    @ParameterizedTest
    @MethodSource("wholeWordAhoCorasickStructures")
    internal fun `supports word boundary mode`(ahoCorasick: StringAhoCorasickWrapper) {

        ahoCorasick.buildWith("Expected", "Double Expected", "Exp")

        val results = ahoCorasick.wrappedAhoCorasick.parse(
            "Double Expected\tnotExpected notDouble\rExpected Expected\nExpectedNot Exp"
        ).map { it.start }.toList()

        assertThat(results).containsExactly(0, 7, 38, 47, 68)
    }

    @ParameterizedTest
    @MethodSource("emptyAhoCorasickStructures")
    internal fun `results supply correct indexes`(ahoCorasick: StringAhoCorasickWrapper) {

        ahoCorasick.buildWith("cat", "at", "a")

        val results = ahoCorasick.wrappedAhoCorasick.parse("cat at a house").toList()
        assertThat(results).containsExactly(
            AhoCorasickResult(0, 3, "cat"),
            AhoCorasickResult(1, 3, "at"),
            AhoCorasickResult(1, 2, "a"),
            AhoCorasickResult(4, 6, "at"),
            AhoCorasickResult(4, 5, "a"),
            AhoCorasickResult(7, 8, "a"),
        )
    }

    @ParameterizedTest
    @MethodSource("emptyAhoCorasickStructures")
    internal fun `node count is calculated corrected`(ahoCorasick: StringAhoCorasickWrapper) {

        ahoCorasick.buildWith("fire hydrant", "fire truck")

        // Start with one for the root node
        val expected = 1 + "fire hydrant".length + "truck".length

        assertThat(ahoCorasick.wrappedAhoCorasick.nodes).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("emptyAhoCorasickStructures")
    internal fun `isBuilt correctly reflects state`(ahoCorasick: StringAhoCorasickWrapper) {

        assertThat(ahoCorasick.wrappedAhoCorasick.isBuilt).isFalse()
        ahoCorasick.buildWith("irrelevant value")
        assertThat(ahoCorasick.wrappedAhoCorasick.isBuilt).isTrue()
    }

    @ParameterizedTest
    @MethodSource("emptyAhoCorasickStructures")
    internal fun `contains determines if a key exists at all build stages`(ahoCorasick: StringAhoCorasickWrapper) {

        ahoCorasick.addString("iExist")
        assertThat(ahoCorasick.wrappedAhoCorasick.contains("iExist")).isTrue()
        assertThat(ahoCorasick.wrappedAhoCorasick.contains("iDontExist")).isFalse()

        ahoCorasick.build()
        assertThat(ahoCorasick.wrappedAhoCorasick.contains("iExist")).isTrue()
        assertThat(ahoCorasick.wrappedAhoCorasick.contains("iDontExist")).isFalse()
    }

    @ParameterizedTest
    @MethodSource("emptyAhoCorasickStructures")
    internal fun `offset greater than length of double array is handled while building`(
        ahoCorasick: StringAhoCorasickWrapper
    ) {
        // The below setup would cause an index out of bounds exception if one did not safely get the value at an index.
        ahoCorasick.addString("cab")
        ahoCorasick.addString("aa")

        assertDoesNotThrow { ahoCorasick.build() }
    }

    @ParameterizedTest
    @MethodSource("caseInsensitiveAhoCorasickStructures")
    internal fun `shifts ranges of results when lowercase text expands`(ahoCorasick: StringAhoCorasickWrapper) {

        ahoCorasick.addString("a")
        ahoCorasick.build()

        // When converted to lowercase, İ will cause the string to go from a length of 11 to 16.
        // If not handled correctly, this will cause problems with case-insensitive AhoCorasick structures.
        val expandingString = "İ a İİİ a İ"
        val results = ahoCorasick.wrappedAhoCorasick.parse(expandingString).toList()

        assertThat(results[0].start).isEqualTo(2)
        assertThat(results[0].end).isEqualTo(3)
        assertThat(results[0].value).isEqualTo("a")

        assertThat(results[1].start).isEqualTo(8)
        assertThat(results[1].end).isEqualTo(9)
        assertThat(results[1].value).isEqualTo("a")
    }

    @ParameterizedTest
    @MethodSource("caseInsensitiveAhoCorasickStructures")
    internal fun `u0307 character at start of string does NOT cause issues`(ahoCorasick: StringAhoCorasickWrapper) {

        ahoCorasick.addString("a")
        ahoCorasick.build()

        assertThat(ahoCorasick.parse("\u0307İ a").first()).isEqualTo("a")
    }

    @Test
    internal fun `generic structure handles keys that expand when converted to lowercase`() {

        val ahoCorasick = StringAhoCorasick(setOf(CASE_INSENSITIVE))

        ahoCorasick.apply {
            add("İ")
            add("I")
            add("\u0307")
            add("_")
            add("_İ_i\u0307_İ_")
            add("_I")
            build()
        }

        val results = ahoCorasick.parse("_İ_i\u0307_İ_").toList()
        assertThat(results).containsExactly(
            AhoCorasickResult(0, 1, "_"),
            AhoCorasickResult(0, 2, "_İ"),
            AhoCorasickResult(1, 2, "İ"),
            AhoCorasickResult(1, 2, "İ"),
            AhoCorasickResult(1, 2, "İ"),
            AhoCorasickResult(2, 3, "_"),
            AhoCorasickResult(2, 4, "_i"),
            AhoCorasickResult(3, 4, "i"),
            AhoCorasickResult(3, 5, "i\u0307"),
            AhoCorasickResult(4, 5, "\u0307"),
            AhoCorasickResult(5, 6, "_"),
            AhoCorasickResult(5, 7, "_İ"),
            AhoCorasickResult(6, 7, "İ"),
            AhoCorasickResult(6, 7, "İ"),
            AhoCorasickResult(6, 7, "İ"),
            AhoCorasickResult(0, 8, "_İ_i\u0307_İ_"),
            AhoCorasickResult(7, 8, "_"),
        ).inOrder()
    }

    @Test
    internal fun `test2`() {

        val ahoCorasick = AhoCorasick<String>(setOf(CASE_INSENSITIVE))

        ahoCorasick.apply {
            add("İ", "İ")
            add("I", "I")
            add("\u0307", "\u0307")
            add("_", "_")
            add("_İ_i\u0307_İ_", "_İ_i\u0307_İ_")
            add("_I", "_I")
            build()
        }

        val results = ahoCorasick.parse("_İ_i\u0307_İ_").toList()
        assertThat(results).containsExactly(
            AhoCorasickResult(0, 1, "_"),
            AhoCorasickResult(0, 2, "_I"),
            AhoCorasickResult(1, 2, "I"),
            AhoCorasickResult(1, 2, "İ"),
            AhoCorasickResult(1, 2, "\u0307"),
            AhoCorasickResult(2, 3, "_"),
            AhoCorasickResult(2, 4, "_I"),
            AhoCorasickResult(3, 4, "I"),
            AhoCorasickResult(3, 5, "İ"),
            AhoCorasickResult(4, 5, "\u0307"),
            AhoCorasickResult(5, 6, "_"),
            AhoCorasickResult(5, 7, "_I"),
            AhoCorasickResult(6, 7, "I"),
            AhoCorasickResult(6, 7, "İ"),
            AhoCorasickResult(6, 7, "\u0307"),
            AhoCorasickResult(0, 8, "_İ_i\u0307_İ_"),
            AhoCorasickResult(7, 8, "_"),
        ).inOrder()
    }

    @Test
    internal fun `generic structure replaces value on duplicate keys`() {

        val genericAhoCorasick = AhoCorasick<Int>().apply {
            add("lucky", 0)
            add("lucky", 7)
            build()
        }

        assertThat(genericAhoCorasick.valueOf("lucky")).isEqualTo(7)
        assertThat(genericAhoCorasick.parse("lucky").toList()[0].value).isEqualTo(7)
    }

    @Test
    internal fun `generic structure valueOf works at various build stages`() {

        val genericAhoCorasick = AhoCorasick<Int>().apply {
            add("existingKey", 7)
        }

        assertThat(genericAhoCorasick.valueOf("existingKey")).isEqualTo(7)
        assertThat(genericAhoCorasick.valueOf("nonExistingKey")).isEqualTo(null)

        genericAhoCorasick.build()

        assertThat(genericAhoCorasick.valueOf("existingKey")).isEqualTo(7)
        assertThat(genericAhoCorasick.valueOf("nonExistingKey")).isEqualTo(null)
    }

    @Test
    fun `generic structure supports values differing from keys`() {

        val genericAhoCorasick = AhoCorasick<String>().apply {
            add("np", "no problem")
            add("ty", "thank you")
            build()
        }

        val results = genericAhoCorasick.parse("It was np, ty though.").map { it.value }.toList()
        assertThat(results).containsExactly("no problem", "thank you")
    }

    @Test
    fun `generic structure supports replacing values`(): Unit = with(AhoCorasick<String>()) {

        add("key", "original")

        // Replace existing value
        assertThat(replace("key", "newValue")).isTrue()
        assertThat(valueOf("key")).isEqualTo("newValue")

        // Replacement fails when value does not exist
        assertThat(replace("newKey", "newValue")).isFalse()
        assertThat(contains("newKey")).isFalse()

        // Replacement with insertion if value does not exist
        assertThat(replace("newKey", "newKeyValue", true)).isFalse()
        assertThat(contains("newKey")).isTrue()

        build()

        // Replacement fails after build has been called
        assertThrows<IllegalStateException> { replace("anotherKey", "newValue", true) }
        // Insertion via replacement fails after build has been called
        assertThrows<IllegalStateException> { replace("newKey", "differentValue") }

        val results = parse("key newKey").map { it.value }.toList()
        assertThat(results).containsExactly("newValue", "newKeyValue")
    }

    @Test
    fun `string structure returns same casing as input when case insensitive`() {

        val ahoCorasick = StringAhoCorasick(setOf(CASE_INSENSITIVE)).apply {
            add("np")
            add("ty")
            add("PARTY")
            build()
        }

        val results = ahoCorasick.parse("Thanks for inviting me to the pARTy. np.").map { it.value }.toList()
        assertThat(results).containsExactly("pARTy", "Ty", "np")
    }

    companion object {

        @JvmStatic
        fun emptyAhoCorasickStructures(): Stream<Arguments> = Stream.of(
            arguments(StringAhoCorasickWrapper(AhoCorasick())),
            arguments(StringAhoCorasickWrapper(StringAhoCorasick())),
        )

        @JvmStatic
        fun caseInsensitiveAhoCorasickStructures(): Stream<Arguments> = Stream.of(
            arguments(StringAhoCorasickWrapper(AhoCorasick(setOf(CASE_INSENSITIVE)))),
            arguments(StringAhoCorasickWrapper(StringAhoCorasick(setOf(CASE_INSENSITIVE)))),
        )

        @JvmStatic
        fun wholeWordAhoCorasickStructures(): Stream<Arguments> = Stream.of(
            arguments(StringAhoCorasickWrapper(AhoCorasick(setOf(WHOLE_WORDS_ONLY)))),
            arguments(StringAhoCorasickWrapper(StringAhoCorasick(setOf(WHOLE_WORDS_ONLY)))),
        )
    }

    /**
     * Wraps around a generic form of the Aho-Corasick structure, as well as an optimized version for strings.
     *
     * This wrapper is necessary to conform the two structures to a common interface. The generic version allows for any
     * value to be associated with a string key, while in the optimized string version the key is also the value, making
     * its add function require only one argument. This wrapper's [addString] function only takes a single string, and
     * simply passes that value as both the key and value for the generic version of the algorithm.
     */
    internal class StringAhoCorasickWrapper {

        val wrappedAhoCorasick: AhoCorasickBase<String>
        val addString: (string: String) -> Unit

        constructor(ahoCorasick: StringAhoCorasick) {
            this.wrappedAhoCorasick = ahoCorasick
            this.addString = ahoCorasick::add
        }

        constructor(ahoCorasick: AhoCorasick<String>) {
            this.wrappedAhoCorasick = ahoCorasick
            this.addString = { string -> ahoCorasick.add(string, string) }
        }

        fun build() = wrappedAhoCorasick.build()

        fun parse(input: String): List<String> = wrappedAhoCorasick.parse(input).map { it.value }.toList()

        fun buildWith(vararg strings: String) = apply {
            strings.forEach(addString)
            wrappedAhoCorasick.build()
        }
    }
}
