package com.pkware.ahocorasick

/**
 * Stores the information required by the Aho-Corasick algorithm.
 *
 * Initially a [AhoCorasickStore] is backed by structures which use a small amount of memory as they grow. These
 * structures have the downside of having a large constant factor for their lookup time. [buildRuntimeStructures]
 * should be called once all wanted data has been added to the store in order to use structures with a faster lookup
 * time. These structures can still grow dynamically, but have the potential to waste much more memory than necessary
 * while doing so.
 *
 * Each node contains five values. The last two of these values are used for differing purposes depending on the build
 * stage for the Aho-Corasick structure. Each of these values are delegated to their own array, as opposed to having a
 * single large array. While the single array method is faster in practice due to locality of the data, the five array
 * method was used instead as it slashes peak memory usage relative to ending memory usage by a factor of five when
 * resizing. It also allows for the structure to store nearly [Int.MAX_VALUE] nodes as opposed to [Int.MAX_VALUE] `/ 5`.
 */
internal class AhoCorasickStore {

    /**
     * Stores the offset used to map to a node's children.
     */
    private var baseOffsets: SafeIndexable = DoubleArrayIntList(RESERVED_VALUE)

    /**
     * Stores the index of each node's parent.
     */
    private var parents: SafeIndexable = DoubleArrayIntList(RESERVED_VALUE)

    /**
     * Stores each nodes value, or [RESERVED_VALUE] if a node doesn't have a value.
     */
    private var values: SafeIndexable = DoubleArrayIntList(RESERVED_VALUE)

    /**
     * Stores a sibling nodes offset, before [AhoCorasickBase.build] is called, the index of the next node in a queue
     * during the build function, and a nodes failure transition post build function.
     */
    private var store1: SafeIndexable = DoubleArrayIntList(RESERVED_VALUE)

    /**
     * Stores the offset to a nodes first child before a call to [AhoCorasickBase.build], and the index to a node's
     * prefix node after [AhoCorasickBase.build] has been called.
     */
    private var store2: SafeIndexable = DoubleArrayIntList(RESERVED_VALUE)

    /**
     * Sets the base offset of a node at the given index.
     *
     * This is the value offset added to child offsets in order to derive their index.
     */
    fun setBaseOffset(index: Int, value: Int): Unit = baseOffsets.set(index, value)

    /**
     * Sets the parent index of the node at the given index.
     *
     * [RESERVED_VALUE] may be passed to indicate that no node exists at the given index.
     */
    fun setParent(index: Int, value: Int): Unit = parents.set(index, value)

    /**
     * Sets the value of a node at the given index.
     *
     * Any value besides [RESERVED_VALUE] indicates that the node contains a value associated with a key.
     */
    fun setValue(index: Int, value: Int): Unit = values.set(index, value)

    /**
     * Sets the offset of a sibling of the node at the given index.
     *
     * If a node has no siblings, [value] should equal the nodes offset. The values set by this function are overwritten
     * via [setQueueIndex] and [setFailureIndex] later on in the build process.
     */
    fun setNextSiblingOffset(index: Int, value: Int): Unit = store1.set(index, value)

    /**
     * Sets the failure index of the node at the given index.
     *
     * This failure index the part of the Aho-Corasick structure which allows one to transition to the appropriate
     * node if the current node does not have a child matching the next character. This setter overwrites the contents
     * set by [setNextSiblingOffset] and [setQueueIndex] later in the build process.
     */
    fun setFailureIndex(index: Int, value: Int): Unit = store1.set(index, value)

    /**
     * Sets the offset of the first child of the node at the given index.
     *
     * [RESERVED_VALUE] should be used to indicate that a node has no children. This value is overwritten by
     * [setPrefixIndex] late in the build process.
     */
    fun setChildOffset(index: Int, value: Int): Unit = store2.set(index, value)

    /**
     * Sets the index of the next node to be processed after the node at the given index.
     *
     * This setter overwrites information set by [setNextSiblingOffset], and has its contents overwritten later on in
     * the build process by [setFailureIndex].
     */
    fun setQueueIndex(index: Int, value: Int): Unit = store1.set(index, value)

    /**
     * Sets the index of the node whose path is the largest suffix of the node at the given index.
     *
     * [RESERVED_VALUE] should be used to indicate no key shares a suffix of the current node.
     */
    fun setPrefixIndex(index: Int, value: Int): Unit = store2.set(index, value)

    /**
     * Gets the base offset of a node at the given index.
     *
     * This is the value offset added to child offsets in order to derive their index. Behavior is undefined if a
     * node at the given index does not exist.
     */
    fun getBaseOffset(index: Int): Int = baseOffsets[index]

    /**
     * Gets the parent index of the node at the given index.
     *
     * [RESERVED_VALUE] indicates that no node exists at the given index. Behavior is undefined if the given index
     * is larger than the size of the
     */
    fun getParent(index: Int): Int = parents[index]

    /**
     * Gets the parent index of the node at the given index.
     *
     * Returns [RESERVED_VALUE] if no node exists at the given index.
     */
    fun safeGetParent(index: Int): Int = parents.safeGet(index)

    /**
     * Gets the value of a node at the given index.
     *
     * Any value besides [RESERVED_VALUE] indicates that the node contains a value associated with a key. Behavior is
     * undefined if no node exists at the given index.
     */
    fun getValue(index: Int): Int = values[index]

    /**
     * Gets the offset of a sibling of the node at the given index.
     *
     * Note that [setQueueIndex] and [setFailureIndex] overwrite values at the index this setter accesses later
     * on in the build process.
     */
    fun getNextSiblingOffset(index: Int): Int = store1[index]

    /**
     * Gets the failure index of the node at the given index.
     *
     * This failure index the part of the Aho-Corasick structure which allows one to transition to the appropriate
     * node if the current node does not have a child matching the next character. Note that [setNextSiblingOffset] and
     * [setQueueIndex] overwrite values at this index later in the build process. Behavior is undefined if no node
     * exists at the given index.
     */
    fun getFailureIndex(index: Int): Int = store1[index]

    /**
     * Gets the offset of the first child of the node at the given index.
     *
     * [RESERVED_VALUE] is returned when the give node has no children. Node that [setPrefixIndex] overwrites values
     * at this index late in the build process. Behavior is undefined if no node exists at the given index.
     */
    fun getChildOffset(index: Int): Int = store2[index]

    /**
     * Gets the index of the next node to be processed after the node at the given index.
     *
     * Note that this [setFailureIndex] overwrites values at this index late in the build process. Behavior is
     * undefined if no node exists at the given index.
     */
    fun getQueueIndex(index: Int): Int = store1[index]

    /**
     * Gets index of the node whose path is the largest suffix of the node at the given index.
     *
     * [RESERVED_VALUE] is returned when no key shares a suffix of the current node. Behavior is undefined if no node
     * exists at the given index.
     */
    fun getPrefixIndex(index: Int): Int = store2[index]

    /**
     * Sets the values for a node, resizing lists if necessary in order to accompany the given [index].
     *
     * [UnboxedIntList.safeSet] is often called when the [UnboxedIntList] may need to grow to accompany the given
     * [index]. Additional overhead occurs checking for this situation. With the knowledge that all [UnboxedIntList]s in
     * this structure are of the same size, only a single [UnboxedIntList.safeSet] call is needed as a check. If the
     * structure was not resized, then a simple [UnboxedIntList.set] can be called for the remaining structures. Only in
     * the rare case where the structure requires a resize does [UnboxedIntList.safeSet] need to be called for all other
     * values. This produces a small but noticeable increase in build time.
     *
     * @param index Index to write the given values to.
     * @param baseOffset See [setBaseOffset]
     * @param parent See [setParent]
     * @param value See [setValue]
     * @param nextSibling See [setNextSiblingOffset]
     * @param childOffset See [setChildOffset]
     */
    fun synchronizedSafeSet(index: Int, baseOffset: Int, parent: Int, value: Int, nextSibling: Int, childOffset: Int) {

        if (baseOffsets.safeSet(index, baseOffset)) {
            parents.safeSet(index, parent)
            values.safeSet(index, value)
            store1.safeSet(index, nextSibling)
            store2.safeSet(index, childOffset)
        } else {
            setParent(index, parent)
            setValue(index, value)
            setNextSiblingOffset(index, nextSibling)
            setChildOffset(index, childOffset)
        }
    }

    /**
     * Converts the underlying structures of the store to ones that have a faster lookup time, but use a large amount of
     * memory if they happen to increase in size.
     *
     * This function should only be called after all data has been added to this structure.
     *
     * A [AhoCorasickStore] originally is backed by structures great for using a small amount of memory as they grow.
     * However, these structures end up being unnecessarily slow to index once the store has been filled with all wanted
     * information. These underlying structures are therefore switched out by ones which can be indexed much faster,
     * but which have the potential to use unnecessarily large amounts of memory if they happen to grow in the future.
     * For this reason, this function should only be called after all data has been added to this structure.
     */
    fun buildRuntimeStructures() {

        baseOffsets = baseOffsets.toIntList()
        parents = parents.toIntList()
        values = values.toIntList()
        store1 = store1.toIntList()
        store2 = store2.toIntList()
    }

    /**
     * Converts this structure to an [UnboxedIntList] for performance purposes.
     *
     * @return An [UnboxedIntList] containing the same values as the original [SafeIndexable].
     */
    private fun SafeIndexable.toIntList(): UnboxedIntList {

        val intList = UnboxedIntList(size, defaultValue = defaultValue)

        // Increase the size attribute of the list
        if (size != 0) intList.safeSet(size - 1, get(size - 1))

        for (i in 0 until size - 1) intList[i] = get(i)

        return intList
    }

    companion object {

        /**
         * Value indicating that the current node has no value.
         *
         * Only nodes which are associated with the end of a key have a value. For example, if the key `cat` was
         * associated with the value `9`, only the `t` node should have the value `9`, with the `c` and `a` having the
         * value [RESERVED_VALUE], assuming the `ca` and `c` keys were not added to the structure.
         */
        const val RESERVED_VALUE: Int = Int.MIN_VALUE
    }
}
