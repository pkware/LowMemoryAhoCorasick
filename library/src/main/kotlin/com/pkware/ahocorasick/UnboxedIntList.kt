package com.pkware.ahocorasick

import java.lang.IllegalArgumentException
import kotlin.NoSuchElementException
import kotlin.jvm.Throws
import kotlin.math.ceil
import kotlin.math.max

/**
 * List which dynamically resizes to store unboxed `int` values.
 *
 * The maximum size is [Int.MAX_VALUE]. This structure is __not__ thread safe.
 *
 * @param initialCapacity The initial capacity of the structure. Must be greater than `0`.
 * @param expansionRate The rate at which the list expands. A trade-off exists between speed and memory used, where
 *        a higher value causes the list to be resized less, and therefore faster for appending new elements, while a
 *        lower value insures less space is wasted. Must be greater than `1.0`
 * @param defaultValue Value used for indexes in the list which have not yet been set, or are greater than the size
 *        of the list. A default value is necessary so boxed `int`s do not need to be used to store `null` values which
 *        would require four times the memory.
 * @throws IllegalArgumentException When `initialCapacity` or [expansionRate] are invalid.
 */
internal class UnboxedIntList(
    initialCapacity: Int = 16,
    private val expansionRate: Double = DEFAULT_EXPANSION_RATE,
    defaultValue: Int = DEFAULT_VALUE,
) : SafeIndexable(defaultValue) {

    init {
        require(initialCapacity >= 1) { "Initial capacity must be at least one!" }
        require(expansionRate > 1.0) { "Expansion rate must be strictly greater than 1.0!" }
    }

    /**
     * Array used to store the elements in this list in an unboxed way.
     *
     * Resizes based on [expansionRate].
     */
    private var array = IntArray(max(initialCapacity, 1)) { defaultValue }

    override operator fun get(index: Int) = array[index]

    override operator fun set(index: Int, value: Int): Unit = array.set(index, value)

    override fun safeSet(index: Int, value: Int): Boolean {

        if (index >= array.size) resize(index)

        array[index] = value
        val oldSize = size
        size = max(size, index + 1)

        return index >= oldSize
    }

    /**
     * Adds a value to the list, resizing the underlying array by a factor of [expansionRate] if necessary.
     *
     * @param value Value to add to the end of the list.
     */
    fun add(value: Int) {
        if (size == array.size) resize(array.size)
        array[size++] = value
    }

    /**
     * Returns the value at the end of the list, reducing the size of the list by one.
     *
     * @return Value at the end of the list.
     * @throws NoSuchElementException When the list is empty.
     */
    @Throws(NoSuchElementException::class)
    fun pop(): Int {
        if (size == 0) throw NoSuchElementException()

        return array[--size]
    }

    /**
     * Clears the list.
     */
    fun clear() {
        size = 0
    }

    /**
     * Resizes [array], with [wantedIndex] being used as the size to multiply by [expansionRate].
     *
     * Manually specifying the size to use for expansion as opposed to using [array]s current size prevents the
     * situation where expanding the list by [expansionRate] might still be too small to accommodate a wanted index.
     *
     * @param wantedIndex The index requested that caused the list to be resized. Used to determine the new size of the
     *        list.
     */
    private fun resize(wantedIndex: Int) {

        val newArray = IntArray(ceil(wantedIndex * expansionRate).toInt()) { defaultValue }
        System.arraycopy(array, 0, newArray, 0, array.size)

        array = newArray
    }

    private companion object {

        /**
         * The default expansion rate for an [UnboxedIntList].
         *
         * Two common expansion rates are 1.5 and 2. While 1.5 grows slower, it allows for previously allocated memory
         * to be reused. This is because values greater or equal than two have the unique property where old memory will
         * never be able to be reused, since the sum of all previous array allocation sizes will be less than the new
         * size. Eg: If an array was resized from 8 to 16 with an expansion rate of two, `1 + 2 + 4 + 8` < `16`.
         */
        const val DEFAULT_EXPANSION_RATE = 1.5
    }
}
