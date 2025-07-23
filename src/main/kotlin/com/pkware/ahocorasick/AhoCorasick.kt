package com.pkware.ahocorasick

/**
 * A generic Aho-Corasick structure which uses a low amount of memory.
 *
 * One can populate this structure by repeatedly calling [add] and then calling [build] once all key value pairs have
 * been added.
 *
 * This structure builds on top of [AhoCorasickBase], requiring an additional `int` per entry and space for the objects
 * stored as values. [AhoCorasickBase] only works with ints, so the generic algorithm gets around this by, instead of
 * directly associating a key with a value, associating a key with a unique `int` (stored in [AhoCorasickBase]), and
 * also then associating that unique `int` with a value.
 *
 * __In the common case that one wishes to only use an AhoCorasick structure for string detection, use the optimized
 * [StringAhoCorasick] class instead.__
 *
 * @param options Options which change the default key matching behavior.
 */
public open class AhoCorasick<T> @JvmOverloads constructor(options: Set<AhoCorasickOption> = emptySet()) : AhoCorasickBase<T>(options) {

    /**
     * Stores all values added to this structure.
     *
     * The generic form of the Aho-Corasick works with [AhoCorasickBase] by associating each key with a unique index.
     * The `int`s returned from the base structure can then be mapped to a value stored here via [generateResult]. This
     * also means the values in this structure can be replaced via [replace] after the Aho-Corasick internals have
     * already been built.
     */
    private val storedValues = ArrayList<T>()

    /**
     * The length of each key in code points, associated by the unique value it maps to.
     *
     * The lengths of keys need to be associated with their values in order to calculate [AhoCorasickResult.start], or
     * the location of the start of a key in the searched text. This is not a problem for [StringAhoCorasick], because
     * the value of each key IS the length of the key. For the generic case however we need to manually keep track of
     * this value.
     */
    private val keyLengths = UnboxedIntList(expansionRate = 1.25)

    /**
     * Gets the value associated with the specified key, or `null` if no such key was inserted into this structure.
     *
     * @param key A string which may be associated with a value added to this [AhoCorasick].
     * @return The value associated with [key], or `null` if no key-value pair was added to this [AhoCorasick] with
     *         the given [key].
     */
    public fun valueOf(key: String): T? = storedValues.getOrNull(keyValue(key))

    /**
     * Adds a key value pair to the [AhoCorasick].
     *
     * If the given key has already been added to the structure, the keys reference will be updated to point to the new
     * value. However, an explicit check to see if this structure already contains a key is not done for speed purposes,
     * so the structure will still have a reference to the original value, it will just never be accessed. If one wants
     * to explicitly remove the previous reference from the structure and replace it with another, see [replace].
     *
     * @param key Key associated with the given [value].
     * @param value Value associated with the given [key]. If a value is already associated with [key], this value will
     *        replace it.
     */
    public fun add(key: String, value: T) {

        addEntry(key, storedValues.size)
        storedValues.add(value)
        keyLengths.add(key.length)
    }

    /**
     * Replaces a key value pair in the [AhoCorasick] structure, or inserts a new one if [insertOnFail] is `true`.
     *
     * Replaced keys do not simply point to a new object, unlike [add], which leaves the old object behind in the
     * structure, wasting memory. [replace] prevents this by explicitly checking to see if a key exists before adding it
     * if [insertOnFail] is true, which hampers build times slightly. Because of this, if one knows no duplicates exist,
     * or is fine wasting memory if they do, they should use the faster [add].
     *
     * Replacement is NOT supported after [build] has been called, since this would make a stream from [parse] mutable.
     *
     * @param key Key associated with the given [value].
     * @param value Value associated with the given [key]. If a value is already associated with [key] and
     *        [insertOnFail] is `true`, this value will replace it.
     * @param insertOnFail If `false`, values are not inserted if the given [key] has already been added to this
     *        [PkAhoCorasick]. If `true`, a duplicate key will overwrite the previous value.
     * @return `true` if the given [value] overwrote a previous value, `false` otherwise. Always `false` when
     *         [insertOnFail] is `false`.
     */
    public fun replace(key: String, value: T, insertOnFail: Boolean = false): Boolean {

        // Don't allow replacement when isBuilt is true, as streams would then be mutable.
        check(!isBuilt) { "Can't modify entries after 'build' has been called." }

        val keyValue = keyValue(key)

        if (keyValue != AhoCorasickStore.RESERVED_VALUE) {
            storedValues[keyValue] = value
            return true
        } else if (insertOnFail) {
            add(key, value)
        }

        return false
    }

    override fun generateResult(index: Int, value: Int, input: String): AhoCorasickResult<T> = AhoCorasickResult(index - keyLengths[value], index, storedValues[value])
}
