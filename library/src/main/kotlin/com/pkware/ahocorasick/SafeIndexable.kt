package com.pkware.ahocorasick

/**
 * Stores a collection of ints in a way where one can store and retrieve a value from any arbitrary positive index.
 */
internal abstract class SafeIndexable(val defaultValue: Int = DEFAULT_VALUE) {

    /**
     * The number of elements contained in the structure.
     */
    var size: Int = 0
        protected set

    /**
     * Attempts to get the value and the specified index, or [defaultValue] if that index is not contained in the list.
     *
     * @return Value at given index, or [defaultValue] if [index] is greater than [size].
     */
    fun safeGet(index: Int): Int {
        if (index >= size) return defaultValue
        return this[index]
    }

    /**
     * Sets the value at the specified index, expanding the size of the underlying structure if necessary.
     *
     * It is assumed the index is never negative.
     *
     * @return `true` if the list size increased due to the given index, `false` otherwise.
     */
    abstract fun safeSet(index: Int, value: Int): Boolean

    /**
     * Sets the value at the specified index. No checks are done for index validity.
     *
     * @throws IndexOutOfBoundsException When [index] is greater than the size of the backing structure.
     */
    abstract operator fun set(index: Int, value: Int)

    /**
     * Gets the value at the specified index. No checks are done for index validity.
     *
     * @return Value at specified index.
     * @throws IndexOutOfBoundsException When [index] is greater than the size of the backing structure.
     */
    abstract operator fun get(index: Int): Int

    companion object {

        @JvmStatic
        val DEFAULT_VALUE = 0
    }
}
