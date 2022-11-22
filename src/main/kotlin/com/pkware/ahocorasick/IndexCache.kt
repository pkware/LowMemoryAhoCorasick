package com.pkware.ahocorasick

/**
 * Stores various indexes which could be used to insert nodes in a [AhoCorasickBase] structure.
 *
 * Indexes are stored in a queue like structure, where older indexes will be returned first.
 *
 * One of the major aspect of the [double array structure](https://www.co-ding.com/assets/pdf/dat.pdf) is that a node's
 * children may have to occasionally move in order to make room for children of another node. This ends up leaving space
 * behind, increasing the sparsity of the structure. To get around this problem, [AhoCorasickBase] uses this structure
 * to store these left behind indexes (as long as it does not plan on checking those indexes in the future). This
 * keeps the structure extremely dense, as long as the number of nodes moved is less than the total number of new nodes
 * added which have less than two children, which is usually the case.
 *
 * @param store The underlying structure of a [AhoCorasickBase]. Used to handle the rare case where an index in this
 *        cache happened to be used by another node.
 * @param maxSize Provides a limit on how many indexes can be cached. The total memory required is ([VALUES_PER_NODE]
 *        + 1) * [maxSize] ints.
 * @param failureTolerance How many times an index is attempted to be used before being dropped. This gracefully
 *        handles a rare case where a character with a small code point (like a space) is moved, and no other character
 *        is small enough to use the index. Dropping the index from the cache prevents future, most likely worthless,
 *        time spent checking against that index.
 */
internal class IndexCache(
    private val store: AhoCorasickStore,
    private val maxSize: Int = 128,
    private val failureTolerance: Int = 10,
) {
    /**
     * The data associated with this cache.
     *
     * The data for each entry in the cache is laid out as follows:
     * 1. Index of the next cache entry
     * 2. Index of the previous cache entry
     * 3. Number of times cache was attempted to be used
     * 4. Value associated with the cache entry
     */
    private val cacheData = IntArray(maxSize * VALUES_PER_NODE)

    /**
     * Array which acts as a stack of unused cache entries, allowing the reuse of entries in [cacheData].
     *
     * An array is used as opposed to an actual stack structure to prevent needless boxing of ints. The values in the
     * queue are multiplies of [VALUES_PER_NODE],  which allows the retrieval of cache values via a simple offset as
     * opposed to having to do an additional multiplication each time access is required.
     */
    private val unusedQueue = IntArray(maxSize) { it * VALUES_PER_NODE }

    /**
     * The number of unused cache indexes. Never bigger than the size of [unusedQueue].
     */
    private var unusedCacheSize = maxSize

    /**
     * The starting node in this cache, or [NULL_ENTRY] if the cache is empty.
     */
    private var head = NULL_ENTRY

    /**
     * The ending node in this cache, or [NULL_ENTRY] if the cache is empty.
     *
     * Used to append nodes in constant time.
     */
    private var tail = NULL_ENTRY

    /**
     * The number of indexes stored in this cache.
     */
    val size: Int get() = maxSize - unusedCacheSize

    /**
     * Adds an index to this cache provided the size of this structure is less than [maxSize].
     *
     * @param index Index to add to the cache.
     * @return `true` if the index was successfully added to the cache, `false` otherwise.
     */
    fun add(index: Int): Boolean {

        if (unusedCacheSize == 0) return false

        val newIndex = unusedQueue[--unusedCacheSize]

        setValue(newIndex, index)
        resetMisses(newIndex)

        if (size == 1) {
            head = newIndex
            setPreviousIndex(newIndex, NULL_ENTRY)
        } else {
            setPreviousIndex(newIndex, tail)
            setNextIndex(tail, newIndex)
        }

        setNextIndex(newIndex, NULL_ENTRY)
        tail = newIndex
        return true
    }

    /**
     * Returns and removes an index from this cache that can be used for the given offset.
     *
     * If no valid indexes exist for the offset, `0` is returned instead, as index will never be available given
     * a non-zero offset.
     *
     * @param offset Offset to find an index for. A successfully returned index will never be less than this value.
     *        Expected to be greater than `0`.
     * @return An empty index in [store] which is greater than [offset]. `0` if no such index exists.
     */
    fun popIndex(offset: Int): Int {

        var currentNodeIndex = head
        while (currentNodeIndex != NULL_ENTRY) {

            val nodeValue = getValue(currentNodeIndex)

            if (store.getParent(nodeValue) != AhoCorasickStore.RESERVED_VALUE) {
                removeNode(currentNodeIndex)
            } else if (nodeValue >= offset) {
                removeNode(currentNodeIndex)
                return nodeValue
            } else if (incrementMisses(currentNodeIndex) == failureTolerance) {
                // If no offset was able to use this offset after a specified number of tries, assume the index is too small.
                removeNode(currentNodeIndex)
            }

            currentNodeIndex = getNextIndex(currentNodeIndex)
        }

        return 0 // 0 is returned on failure since a non-zero offset could not possibly use that index.
    }

    /**
     * Removes the node from this [IndexCache].
     *
     * Assumes the node is part of this [IndexCache].
     *
     * @param nodeIndex Index of the node to remove from the cache.
     */
    private fun removeNode(nodeIndex: Int) {

        val nextNodeIndex = getNextIndex(nodeIndex)
        val previousNodeIndex = getPreviousIndex(nodeIndex)

        if (getNextIndex(head) == nextNodeIndex) head = nextNodeIndex
        if (getPreviousIndex(tail) == previousNodeIndex) tail = previousNodeIndex

        if (previousNodeIndex != NULL_ENTRY) setNextIndex(previousNodeIndex, nextNodeIndex)
        if (nextNodeIndex != NULL_ENTRY) setPreviousIndex(nextNodeIndex, previousNodeIndex)

        unusedQueue[unusedCacheSize++] = nodeIndex
    }

    /**
     * Returns the index of the previous entry in the cache, or [NULL_ENTRY] if the current entry is [head].
     */
    private fun getPreviousIndex(nodeIndex: Int) = cacheData[nodeIndex + PREVIOUS_OFFSET]

    /**
     * Returns the index of the next entry in the cache, or [NULL_ENTRY] if the current entry is [tail].
     */
    private fun getNextIndex(nodeIndex: Int) = cacheData[nodeIndex]

    /**
     * Returns the value store in the current cache entry.
     */
    private fun getValue(nodeIndex: Int) = cacheData[nodeIndex + VALUE_OFFSET]

    /**
     * Stores the index of the previous entry in the cache.
     *
     * [NULL_ENTRY] is used to indicate no such entry exists.
     */
    private fun setPreviousIndex(nodeIndex: Int, value: Int) = cacheData.set(nodeIndex + PREVIOUS_OFFSET, value)

    /**
     * Stores the index of the next entry in the cache.
     *
     * [NULL_ENTRY] is used to indicate no such entry exists.
     */
    private fun setNextIndex(nodeIndex: Int, value: Int) = cacheData.set(nodeIndex, value)

    /**
     * Sets the value of the cache entry.
     */
    private fun setValue(nodeIndex: Int, value: Int) = cacheData.set(nodeIndex + VALUE_OFFSET, value)

    /**
     * Sets the misses of the current cache entry to zero.
     */
    private fun resetMisses(nodeIndex: Int) = cacheData.set(nodeIndex + MISSES_OFFSET, 0)

    /**
     * Increments the number of misses associated with a cache entry by one.
     *
     * @return The number of misses associated with a cache entry after incrementing.
     */
    private fun incrementMisses(nodeIndex: Int) = ++cacheData[nodeIndex + MISSES_OFFSET]

    private companion object {

        /**
         * The number of values associated with each cache entry.
         */
        const val VALUES_PER_NODE = 4

        /**
         * The offset for the index in [cacheData] containing the index to the previous cache entry.
         */
        const val PREVIOUS_OFFSET = 1

        /**
         * The offset for the index in [cacheData] containing how many times a cache entry was missed.
         */
        const val MISSES_OFFSET = 2

        /**
         * The offset for the index in [cacheData] containing the value of a cache entry.
         */
        const val VALUE_OFFSET = 3

        /**
         * Value indicating that a cache entry doesn't exist.
         */
        const val NULL_ENTRY = Int.MIN_VALUE
    }
}
