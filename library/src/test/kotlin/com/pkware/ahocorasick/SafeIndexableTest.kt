package com.pkware.ahocorasick

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class SafeIndexableTest {

    @ParameterizedTest
    @MethodSource("safeIndexableArgs")
    internal fun `throws error when using negative index`(list: SafeIndexable) {
        assertThrows<IndexOutOfBoundsException> { list[-1] }
        assertThrows<IndexOutOfBoundsException> { list[-1] = 10 }
    }

    @ParameterizedTest
    @MethodSource("safeIndexableArgs")
    internal fun `safeSet expands the size of the array if necessary`(list: SafeIndexable) {

        list.safeSet(100, 115)

        assertThat(list.size).isEqualTo(101)
        assertThat(list[100]).isEqualTo(115)
    }

    @ParameterizedTest
    @MethodSource("safeIndexableArgs")
    internal fun `safeGet returns the default value with an invalid index`(list: SafeIndexable) {
        assertThat(list.safeGet(1_000_000)).isEqualTo(SafeIndexable.DEFAULT_VALUE)
    }

    @ParameterizedTest
    @MethodSource("safeIndexableArgs")
    internal fun `list size always grows to accommodate the wanted index`(list: SafeIndexable) {

        list.safeSet(1, 1)
        assertThat(list[1]).isEqualTo(1)

        list.safeSet(50, 50)
        assertThat(list[50]).isEqualTo(50)
    }

    @ParameterizedTest
    @MethodSource("safeIndexableArgs")
    internal fun `safe set changes the size of the list`(list: SafeIndexable): Unit = with(list) {

        safeSet(99, 7)
        assertThat(size).isEqualTo(100)

        safeSet(10, 7)
        assertThat(size).isEqualTo(100)
    }

    @ParameterizedTest
    @MethodSource("safeIndexableArgs")
    internal fun `array resize causes sets unset values to the default value`(list: SafeIndexable) {

        list.safeSet(5, 7)

        for (i in 0 until 5) {
            assertThat(list[i]).isEqualTo(SafeIndexable.DEFAULT_VALUE)
        }
    }

    companion object {

        @JvmStatic
        fun safeIndexableArgs(): Stream<Arguments> = Stream.of(
            Arguments.arguments(UnboxedIntList()),
            Arguments.arguments(DoubleArrayIntList()),
        )
    }
}
