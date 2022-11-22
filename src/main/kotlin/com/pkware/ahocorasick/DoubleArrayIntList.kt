package com.pkware.ahocorasick

import kotlin.math.max

/**
 * List which dynamically resizes to store unboxed `int` values and wastes little extra memory.
 *
 * The maximum size is [Int.MAX_VALUE]. This structure is __not__ thread safe.
 *
 * @param defaultValue Value used for indexes in the list which have not yet been set, or are greater than the size
 *        of the list. A default value is necessary so boxed `int`s do not need to be used to store `null` values which
 *        would require four times the memory.
 */
internal class DoubleArrayIntList(
    defaultValue: Int = DEFAULT_VALUE,
) : SafeIndexable(defaultValue) {

    /**
     * The number of non-null sub-arrays in [baseArray].
     */
    private var populatedSubArrays = 1

    /**
     * An array which points to various other sub arrays which store data.
     *
     * This allows the structure to grow using very little memory. Individual sub arrays can be added when needed, and
     * when [baseArray] grows the references to the various sub arrays are simply copied over to the new array.
     */
    private var baseArray: Array<IntArray?> = Array(1) { IntArray(SUBARRAY_SIZE) { defaultValue } }

    /**
     * The sum of all non-null sub array sizes.
     */
    private val internalSize: Int get() = populatedSubArrays * SUBARRAY_SIZE

    override operator fun get(index: Int) = baseArray[index shr SUBARRAY_BITS]!![index and SUBARRAY_MASK]

    override operator fun set(index: Int, value: Int) = baseArray[index shr SUBARRAY_BITS]!!.set(index and SUBARRAY_MASK, value)

    override fun safeSet(index: Int, value: Int): Boolean {

        if (index >= internalSize) resize(index)

        this[index] = value
        val oldSize = size
        size = max(size, index + 1)

        return index >= oldSize
    }

    /**
     * Resizes this structure to at least have the ability to store [wantedIndex].
     *
     * There are two scenarios that may occur. The first is that [wantedIndex] could fit inside a sub-array in
     * [baseArray]. In this case sub-arrays are simply allocated up to the point where [wantedIndex] would be stored.
     * The second case is where [baseArray] needs to expand. In this case [baseArray] doubles in size, and all sub-array
     * references are copied over. Sub-arrays are then filled until [wantedIndex] can be stored.
     *
     * @param wantedIndex The index requested that caused the list to be resized. Used to determine the new size of the
     *        list.
     */
    private fun resize(wantedIndex: Int) {

        if (wantedIndex >= baseArray.size * SUBARRAY_SIZE) {

            val newBaseSize = max(1, (wantedIndex.takeHighestOneBit() shr (SUBARRAY_BITS - 1)))
            val newBaseArray = Array(newBaseSize) {
                if (it < baseArray.size) baseArray[it] else null
            }

            baseArray = newBaseArray
        }

        val newFilledSubArrays = (wantedIndex / SUBARRAY_SIZE) + 1
        for (i in populatedSubArrays until newFilledSubArrays) {
            baseArray[i] = IntArray(SUBARRAY_SIZE) { defaultValue }
        }

        populatedSubArrays = newFilledSubArrays
    }

    private companion object {

        /**
         * The number of bits used to index a sub-array.
         *
         * Using a certain number of bits ensures the subarray size is a multiple of two, which allows efficient
         * indexing of [baseArray] via bit shifting.
         */
        const val SUBARRAY_BITS = 14

        /**
         * The size of each sub array.
         *
         * Guaranteed to be a power of two, allowing for indexing [baseArray] via bit shifting.
         */
        const val SUBARRAY_SIZE = 1 shl SUBARRAY_BITS

        /**
         * When `AND`ed to a [DoubleArrayIntList] index, gives the correct sub-array index.
         *
         * Possible because the size of a sub-array is always a power of two.
         */
        const val SUBARRAY_MASK = SUBARRAY_SIZE - 1
    }
}
