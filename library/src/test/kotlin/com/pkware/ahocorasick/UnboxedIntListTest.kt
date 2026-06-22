package com.pkware.ahocorasick

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.IllegalArgumentException

class UnboxedIntListTest {

    @ParameterizedTest
    @ValueSource(ints = [-100, -1, 0])
    fun `throws error when initial capacity is less than one`(value: Int) {
        assertThrows<IllegalArgumentException> { UnboxedIntList(initialCapacity = value) }
    }

    @ParameterizedTest
    @ValueSource(doubles = [-1.0, 0.0, 1.0])
    fun `throws error when expansion rate is less than one`(value: Double) {
        assertThrows<IllegalArgumentException> { UnboxedIntList(expansionRate = value) }
    }

    @Test
    fun `custom default value is respected`() {

        val list = UnboxedIntList(defaultValue = 42)
        assertThat(list.safeGet(1_000)).isEqualTo(42)
    }

    @Test
    fun `initial capacity determines size of underlying array`() {

        val list = UnboxedIntList(initialCapacity = 1)
        assertThrows<IndexOutOfBoundsException> { list[1] }
    }

    @Test
    fun `list size always grows to accommodate the wanted index`() {

        val list = UnboxedIntList(1, 1.00001).apply {
            add(0)
            add(1)
        }

        assertThat(list[1]).isEqualTo(1)

        list.safeSet(50, 50)
        assertThat(list[50]).isEqualTo(50)
    }

    @Test
    fun `size accurately reflects the size of the list`(): Unit = with(UnboxedIntList()) {

        add(1)
        add(-2)
        add(3)
        assertThat(size).isEqualTo(3)

        safeSet(99, 7)
        assertThat(size).isEqualTo(100)

        pop()
        assertThat(size).isEqualTo(99)

        clear()
        assertThat(size).isEqualTo(0)
    }

    @Test
    fun `pop removes the last element`(): Unit = with(UnboxedIntList()) {

        for (i in 0..10) add(i)

        assertThat(pop()).isEqualTo(10)
        assertThat(size).isEqualTo(10) // Zero indexed
        assertThat(pop()).isEqualTo(9)
    }

    @Test
    fun `popping empty list throws exception`() {
        assertThrows<NoSuchElementException> { UnboxedIntList().pop() }
    }
}
