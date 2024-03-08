package com.pkware.ahocorasick

import com.pkware.ahocorasick.AhoCorasickStore.Companion.RESERVED_VALUE
import java.util.Spliterators
import java.util.function.Consumer
import java.util.stream.Stream
import java.util.stream.StreamSupport
import kotlin.jvm.Throws
import kotlin.math.max

/**
 * A fast, compact, low peak memory Aho-Corasick structure which only uses approximately 5 `int`s per node.
 *
 * Due to how information is stored within this structure, it can support at most a little under [Int.MAX_VALUE] nodes.
 * The exact amount varies based on compression, but compression rates are usually well over 99%. The structure's peak
 * memory usage is 20% higher than the ending memory required, although things such as memory fragmentation may make the
 * actual value higher.
 *
 * Subclasses need to implement their own [generateResult] function, which maps an arbitrary `int` value to a generic
 * one. The function also determines the starting index of a key, given the keys ending index and its value.
 * This ends up allowing structures like [StringAhoCorasick] to save even more on memory. With a [generateResult]
 * function implemented, subclasses can make calls to [addEntry] to populate the structure. Once populated, [build] may
 * be called to generate the Aho-Corasick transitions.
 *
 * __Implementation Details__
 *
 * This structure draws inspiration from [this paper](https://www.co-ding.com/assets/pdf/dat.pdf), which uses a double
 * array structure to compact the space needed to store children of nodes, as well as provide an extremely quick
 * access time to those children. This structure builds upon the original concept, which was used to build a Trie
 * structure, to make it work with an Aho-Corasick structure. Due to the Aho-Corasick structure needing failure
 * transitions for every node, the `tail` array is not used. All nodes are instead stored in a modified double array
 * structure, which works extremely well as any node which has fewer than two children can fill any open space.
 * Compression for the modified double array structure is almost always well over 99%.
 *
 * The algorithm works as follows. Each node consists of five values, with the last two being reused for different
 * purposes multiple times throughout the structures' construction. These five values are all stored side by side in a
 * modified double array structure. Whenever a collision occurs between two nodes with children, all five values of each
 * child are moved for the appropriate parent. The first two values represent the base offset and parent (check in
 * original paper). The third value represents the value associated with a given node, or [RESERVED_VALUE] if the path
 * from the root to the node does not form a key. During the construction of the Trie structure, before failure
 * transitions are calculated, the fourth value represents the offset to a sibling child, or the offset of the current
 * node if it has no siblings. When coupled with the fifth value, which represents the index of a parent node's first
 * child (during initial Trie construction), children of a node can be determined in a linear way. This is an
 * improvement over the original implementation, as it saves one from having to iterate over all possible 65,535 (255
 * in paper) nodes that could contain a child. This makes moving nodes much faster, since a parent's children can
 * easily be calculated, but also makes doing a breadth first search possible for efficient construction of the Aho
 * Corasick failure transitions. See [determineChildOffsets] for more info on how children indexes can be derived.
 *
 * After the initial Trie structure is built, the failure transitions for the Aho-Corasick structure need to be created.
 * In order to efficiently calculate these transitions, nodes need to be iterated over in a breadth first way so that a
 * child can calculate its failure transition via its parent. This process usually involves using a queue like structure
 * which would increase peak memory use. However, this can be circumvented by reusing the fourth value, which used to be
 * used to store a reference to a parents first child, to point to a future node to be processed. This effectively forms
 * a queue structure without any additional memory being used. See [prepareChildrenForProcessing] for more information
 * on how this is accomplished. Once all of a parent's children have been added to a queue like structure, the parent is
 * then able to replace its fourth value yet again, this time with the index of the node it should jump to in case of a
 * failure. In order to prevent one from iterating up failure transitions for each node to find any keys that are equal
 * to a suffix of the path from the root to a given node, the index of the largest suffix node is stored in the fifth
 * value. This strategy ends up replacing a set found in most Aho-Corasick implementations, and has the added benefit of
 * having space for various values shared between nodes. See [calculatePrefixIndex] for more information on strategy.
 *
 * @param options Options which change the default key matching behavior.
 */
public abstract class AhoCorasickBase<T> @JvmOverloads constructor(options: Set<AhoCorasickOption> = emptySet()) {

    /**
     * `true` if keys are treated as case-sensitive, `false` otherwise.
     */
    public val isCaseSensitive: Boolean

    /**
     * `true` if only keys surrounded by white space should be found, `false` otherwise. Keys that start or end in white
     * space need _additional_ white space around them in order to be found. White space is defined as any character
     * which matches the regex `\s`, or the start and end of a string (eg: `Hello` in `Hello There` is surrounded by
     * white space).
     */
    public val findOnlyWholeWords: Boolean

    /**
     * Caches the index of [store] where all previous indexes are assumed to be in use by nodes.
     *
     * This efficiently allows one to search for open indexes for new nodes without ever iterating over the same
     * index twice, as any index that is stumbled upon is large enough for at least one node. Almost all indexes
     * in [store] less than this value are expected to be filled. Only indexes too small for offsets, or those which
     * have had their node moved to a new index will be empty. Empty indexes left behind due to nodes moving are
     * attempted to be used via the [indexCache].
     *
     * Begins at `1`, so no attempt is made to insert into the [ROOT_NODE] location.
     */
    private var singleChildCache = 1

    /**
     * Caches the last index a node with multiple children was moved to in [store].
     *
     * Future moves will start with this location, as it is assumed the previous indexes are too dense to easily fit a
     * node with many children.
     *
     * Begins at `1`, so no attempt is made to insert into the [ROOT_NODE] location.
     */
    private var multiChildCache = 1

    /**
     * Whether [build] has been called.
     */
    public var isBuilt: Boolean = false
        private set

    /**
     * Stores the values of all nodes added to this structure.
     *
     * See [AhoCorasickBase] for more information how information is laid out in this structure during various stages
     * of the build process.
     */
    private val store = AhoCorasickStore()

    /**
     * The number of nodes in this Aho-Corasick structure.
     *
     * Initially `1` due to the presence of the root node.
     */
    public var nodes: Int = 1
        private set

    /**
     * Repeatedly used to store children of a node for efficiency purposes.
     *
     * Reuse eliminates time spent on initialization and reduces GC calls.
     */
    private val childrenOffsetStore1 = UnboxedIntList()

    /**
     * Repeatedly used to store children of a node for efficiency purposes.
     *
     * Reuse eliminates time spent on initialization and reduces GC calls.
     */
    private val childrenOffsetStore2 = UnboxedIntList()

    /**
     * Used to store base offset indexes for nodes with a single node. This prevents the structure from becoming more
     * sparse as nodes are moved to differing indexes when overlaps occur.
     */
    private val indexCache = IndexCache(store)

    init {
        // The base offset is started at `1`, because otherwise the character `\u0000` could potentially be inserted as
        // the value for the root node, which is located at index zero. This problem is unique to the root node because
        // the root node is the only node which is considered to be its own parent. The root node must have a parent
        // because the existence of a parent node indicates that a node is in use. Having the parent of the root node
        // be a valid node that will always exist as opposed to a special value also makes some code cleaner.
        store.synchronizedSafeSet(ROOT_NODE, 1, ROOT_NODE, RESERVED_VALUE, RESERVED_VALUE, RESERVED_VALUE)

        isCaseSensitive = !options.contains(AhoCorasickOption.CASE_INSENSITIVE)
        findOnlyWholeWords = options.contains(AhoCorasickOption.WHOLE_WORDS_ONLY)
    }

    /**
     * Generates an [AhoCorasickResult] based on the index a value was found.
     *
     * Unlike a [AhoCorasickResult], which keeps track of the start and end indexes of a key, as well as an arbitrary
     * value, this structure is only able to keep track of the index an `int` value was found at. The reason for this
     * limitation is memory. Each node in this structure uses approximately 5 `int`s, and keeping track of the ending
     * index would require a 6th. Structures like [StringAhoCorasick] are set up in such a way where the value
     * associated with a key also acts as a way to calculate the starting index of a key. Having subclasses manually
     * calculate this value allows structures to save memory if they can. Subclasses also are free to associate an
     * arbitrary value with [value], abstracting away details for aggregating and returning generic results.
     *
     * @param index The ending index of the key associated with [value], where an index is a zero indexed value
     *        denoting the space between characters in a string. E.g. if the key `cat` was found in `bobcat`, [location]
     *        would be `6`.
     * @param value The value associated with a key during [addEntry].
     * @param input The text the key was originally found in.
     * @return [AhoCorasickResult] storing information about a match in some string.
     */
    protected abstract fun generateResult(index: Int, value: Int, input: String): AhoCorasickResult<T>

    /**
     * Attempts to build the Aho-Corasick structure.
     *
     * No further objects can be added to the structure once [build] has been called, and [build] will throw an
     * [IllegalStateException] if called again.
     *
     * @throws IllegalStateException If [build] has already been called.
     */
    @Synchronized
    @Throws(IllegalStateException::class)
    public fun build() {

        check(!isBuilt) { "PkAhoCorasick can only be built once!" }

        store.buildRuntimeStructures()
        constructFailStates()
        isBuilt = true
    }

    /**
     * Returns a stream of [AhoCorasickResult]s containing the value and positioning information for each instance
     * of a key present in this structure which appears in [input].
     *
     * Results are returned in ascending order based on their ending location, with results sharing the same ending
     * index ordered in descending order by their length.
     *
     * Ex:
     * If the keys given to this structure were `{ cat, at, a }`, and [input] was `catapult`, [AhoCorasickResult]s for
     * the following order of keys would be returned: `[a, cat, at, a]`.
     *
     * The stream is lazily populated with [AhoCorasickResult]s, although some results are necessarily buffered before
     * being added to the stream. This is because each index of a given input string could theoretically match against
     * `n` different keys, where `n` is the length of the largest key, if there were `n - 1` other keys making up the
     * suffix of the largest key. In the above example, at index 3 of `catapult`, two strings would be buffered, those
     * being `cat`, and `at`.
     *
     * @param input String to search for instances of added key-value pairs.
     * @return Stream of [AhoCorasickResult]s containing information about each instance of a key-value pair present
     *         in this structure which appears in [input].
     * @throws IllegalStateException When [build] has not yet been called.
     */
    @Throws(IllegalStateException::class)
    public fun parse(input: String): Stream<AhoCorasickResult<T>> {

        check(isBuilt) { "'build' must be called before parsing text!" }

        return StreamSupport.stream(AhoCorasickSpliterator(input), false)
    }

    /**
     * Determines if the given entry exists in this structure.
     *
     * @param entry Entry to search for in this structure.
     * @return Whether [entry] exists in this structure.
     */
    public fun contains(entry: String): Boolean = keyValue(entry) != RESERVED_VALUE

    /**
     * Returns the value associated with a key, or [RESERVED_VALUE] if no such key exists.
     *
     * @param key A key associated with some `int` value.
     * @return The `int` value associated with [key], or [RESERVED_VALUE] if no such value exists.
     */
    protected fun keyValue(key: String): Int {

        val normalizedKey = key.normalize()

        var currentNode = ROOT_NODE
        for (character in normalizedKey) {

            val nextNode = store.getBaseOffset(currentNode) + character.code
            if (store.safeGetParent(nextNode) != currentNode) return RESERVED_VALUE
            currentNode = nextNode
        }

        return store.getValue(currentNode)
    }

    /**
     * Adds a key-value pair to this [AhoCorasick].
     *
     * Duplicate key entries will replace the previous value.
     *
     * It is assumed that [value] will __NEVER__ be equal to [RESERVED_VALUE], which is used to indicate that a node has
     * no value associated with it. For example, if one associated the key `cat`  with `9`, only the `t` node would have
     * the value `9`. The `c` and `a` would have [RESERVED_VALUE] as their values since the keys `ca`  and `c` do not
     * exist.
     *
     * This function is protected as users are not expected to directly associate a key with an `int` value. It is up to
     * the subclass to map an arbitrary key to an `int` value. If this method were exposed, the subclass would have two
     * separate add functions, which is confusing for end users, and also would allow the user to add values directly
     * to this structure, breaking things depending on how the subclass determines which values should be associated
     * with certain keys.
     *
     * @param key Key associated with a value. A duplicate key replaces the previous value.
     * @param value Value associated with a key. Assumed to __NEVER__ equal [RESERVED_VALUE].
     * @throws IllegalArgumentException When [key] is empty.
     * @throws IllegalStateException When [build] has already been called.
     */
    @Synchronized
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    protected fun addEntry(key: String, value: Int) {

        check(!isBuilt) { "Can't add entries after `build` has been called!" }
        require(key.isNotEmpty()) { "Key can not be empty!" }

        val normalizedKey = key.normalize()

        var currentNode = ROOT_NODE

        // Iterate through all characters, attempting to add them as a child of the node at the current index.
        for (character in normalizedKey) {

            val characterOffset = character.code

            // In the case a node has no children, and therefore no base offset, pick one that satisfies the node to add.
            if (store.getBaseOffset(currentNode) == RESERVED_VALUE) {
                store.setBaseOffset(currentNode, findOpenIndex(characterOffset) - characterOffset)
            }

            var nextNode = store.getBaseOffset(currentNode) + characterOffset
            val nextParentValue = store.safeGetParent(nextNode)

            // Check to see if the current index already has a child of the corresponding character. If one already
            // exists, then there is nothing to insert, and we move on to the next node.
            if (nextParentValue != currentNode) {

                // If the next index is already used by another node then we will move the children of the node with
                // fewer children, which could potentially change the base offset of the current node.
                if (nextParentValue != RESERVED_VALUE) {
                    currentNode = handleBaseOffsetConflict(nextParentValue, currentNode, characterOffset)
                    nextNode = store.getBaseOffset(currentNode) + characterOffset
                }

                addNode(nextNode, currentNode)
            }

            currentNode = nextNode
        }

        store.setValue(currentNode, value)
    }

    /**
     * Whether the parent at the given index has a child with the specified offset.
     *
     * Assumes an index at the parent location exists.
     */
    private fun containsChild(parent: Int, childOffset: Int) =
        store.safeGetParent(store.getBaseOffset(parent) + childOffset) == parent

    /**
     * Constructs the failure and prefix transitions for the nodes in the Aho-Corasick structure.
     *
     * This function should only ever be called once since it replaces values necessary for it to function correctly.
     *
     * At this point in the process, all nodes needed for the AhoCorasick structure have been created. However, the
     * failure transitions for each node still need to be created, as well as an index to any valid strings which are a
     * suffix of a leaf nodes string. In order to construct these things, a breadth first search of the nodes needs to
     * be done so whenever a node is processed it is guaranteed that its parent has also been processed. A queue
     * structure can be emulated by having each node that is waiting to be processed point to the node after it that
     * should be processed. This prevents the need of having to allocate additional memory, since the queue index will
     * be overwritten with another value later on in the process.
     *
     * The process works as follows. A nodes children are iterated through and modified in such a way where the offset
     * used to determine a nodes siblings is replaced by the index of the next node to be processed. This effectively
     * creates a queue structure without requiring any additional memory. Once the child is reached in the queue, the
     * now parent node has its own children processed, and then has its queue index, as well as its child offset
     * replaced by its calculated failure index and prefix index. See [calculateFailureIndex] and [calculatePrefixIndex]
     * for details on how these are calculated and used.
     *
     * @see calculateFailureIndex
     * @see calculatePrefixIndex
     */
    private fun constructFailStates() {

        // No strings were added to the structure, so there is nothing to construct.
        if (nodes == 1) return

        // This value is constantly updated whenever children are processed.
        val lastQueuedNode = IntReference(ROOT_NODE)

        prepareChildrenForProcessing(ROOT_NODE, lastQueuedNode)
        val currentNode = IntReference(store.getQueueIndex(ROOT_NODE))
        val rootChildren = processRootChildren(lastQueuedNode, currentNode)

        // Root can't represent a string, so it's not possible for it to point anywhere.
        store.setFailureIndex(ROOT_NODE, ROOT_NODE)
        store.setPrefixIndex(ROOT_NODE, RESERVED_VALUE)

        // The below loop iteratively processes the children of all nodes.
        // Subtract 1 and the root nodes to determine how many more nodes should be processed.
        for (i in 0 until nodes - 1 - rootChildren) {

            // Needs to be called first since the stack index of the parent could potentially be set.
            prepareChildrenForProcessing(currentNode.value, lastQueuedNode)

            // Need to write the next index to a temporary variable here, as its overwritten when calling the
            // setFailureIndex function down below.
            val nextIndex = store.getQueueIndex(currentNode.value)
            store.setFailureIndex(currentNode.value, calculateFailureIndex(currentNode.value))
            store.setPrefixIndex(currentNode.value, calculatePrefixIndex(currentNode.value))

            currentNode.value = nextIndex
        }
    }

    /**
     * Whether all offsets in [relativeOffsets] correspond to a free index relative to [baseOffset].
     *
     * Ex:
     * If we had the collection `[5, 2, _, 3, _]`, where `_` represents an open space, we would be able to insert the
     * offsets `1` and `3` at index `1`, as well as any index after 4, since we could always expand the list to
     * incorporate the needed offsets. We would not be able to insert the offsets at indexes `0`, `2`, or `3`, since
     * one of the offsets added with [baseOffset] would point to an index already in use.
     *
     * @param relativeOffsets Collection of offsets that should map to an open index.
     * @param baseOffset index which the offsets are relative to.
     * @return `true` if [relativeOffsets] can be inserted at [baseOffset], `false` otherwise.
     */
    private fun canInsertOffsets(relativeOffsets: UnboxedIntList, baseOffset: Int): Boolean {

        for (i in 0 until relativeOffsets.size) {
            if (store.safeGetParent(baseOffset + relativeOffsets[i]) != RESERVED_VALUE) return false
        }

        return true
    }

    /**
     * Calculates the node to transition to from [startingNode] given the [offset].
     *
     * Repeatedly makes failure transitions until a node with the wanted child is found, or the root is reached.
     *
     * @param startingNode The index of the node representing the current state of the [AhoCorasick] structure.
     * @param offset The value of a character to transition to a child node.
     * @return The index of the node associated with the next state of the [AhoCorasick] structure.
     */
    private fun calculateNextState(startingNode: Int, offset: Int): Int {

        var currentNode = startingNode

        var childNode = store.getBaseOffset(currentNode) + offset
        var hasValidChild = store.safeGetParent(childNode) == currentNode

        while (!hasValidChild && currentNode != ROOT_NODE) {

            currentNode = store.getFailureIndex(currentNode)
            childNode = store.getBaseOffset(currentNode) + offset
            hasValidChild = store.safeGetParent(childNode) == currentNode
        }

        // The current node will always be ROOT_NODE if no valid child is found.
        return if (hasValidChild) childNode else ROOT_NODE
    }

    /**
     * Determines if whitespace surrounds an [AhoCorasickResult]. The start and end of a string count as whitespace,
     * e.g. the string `hello` in `hello there` or `there hello` is treated as valid.
     *
     * @param result [AhoCorasickResult] to check for surrounding whitespace.
     * @param text The string that [AhoCorasickResult] was derived from.
     * @return `true` if [result] has leading and trailing whitespace, `false` otherwise.
     */
    private fun passesWhitespaceChecks(result: AhoCorasickResult<T>, text: String) =
        hasLeadingWhitespace(result, text) && hasTrailingWhitespace(result, text)

    /**
     * Checks if whitespace, or the end of the string, follows a [result].
     *
     * @param result [AhoCorasickResult] to check for trailing whitespace.
     * @param text The string that [AhoCorasickResult] was derived from.
     * @return `true` if [result] has trailing whitespace, `false` otherwise.
     */
    private fun hasTrailingWhitespace(result: AhoCorasickResult<T>, text: String): Boolean =
        result.end == text.length || WHITESPACE_REGEX.matches(text[result.end].toString())

    /**
     * Checks if whitespace, or the start of the string, precedes a [result].
     *
     * @param result [AhoCorasickResult] to check for leading whitespace.
     * @param text The string that [AhoCorasickResult] was derived from.
     * @return `true` if [result] has leading whitespace, `false` otherwise.
     */
    private fun hasLeadingWhitespace(result: AhoCorasickResult<T>, text: String): Boolean =
        result.start == 0 || WHITESPACE_REGEX.matches(text[result.start - 1].toString())

    /**
     * Finds an index where, relative to it, all offsets in [offsets] point to an index not being used by another node.
     *
     * See [canInsertOffsets] for an example demonstrating when offsets can / can't be inserted.
     *
     * @param offsets Offsets to find an open index for.
     * @return An index, where when added with each offset, points to an index not in use by any node.
     * @see canInsertOffsets
     */
    private fun findOpenIndexes(offsets: UnboxedIntList): Int {

        // We can do things in a more optimized / compact way in the case of only one offset.
        if (offsets.size == 1) return findOpenIndex(offsets[0]) - offsets[0]

        // Don't move nodes to areas completely compacted by nodes.
        multiChildCache = max(singleChildCache, multiChildCache)

        while (!canInsertOffsets(offsets, ++multiChildCache));

        return multiChildCache
    }

    /**
     * Finds an index where, relative to it, the given [offset] points to an index not being used by another node.
     *
     * This variation of the function is much faster than the one that requires an [UnboxedIntList].
     *
     * See [canInsertOffsets] for an example demonstrating when offsets can / can't be inserted.
     *
     * @param offset Offset to find an open index for.
     * @return An index, which when added with [offset], points to an index not in use by any node.
     * @see canInsertOffsets
     */
    private fun findOpenIndex(offset: Int): Int {

        // Try to initially fill in indexes abandoned by nodes which were moved. This keeps compression high, and
        // saves us the need from having to manually search for an open index.
        val cacheIndex = indexCache.popIndex(offset)
        if (cacheIndex != 0) return cacheIndex

        // In the case the offset is greater than the index we last inserted a single offset, we increase the cache
        // to the minimum index for the offset. In theory this means if one of our first offsets was the max value of
        // a character (65,535) we would leave a large amount of empty space at the beginning of the store. However, in
        // practice this is unlikely to happen, for large dictionaries ends up being a very small percentage of
        // memory wasted, and saves us from having to write more complicated code which could slow down build times.
        singleChildCache = max(singleChildCache, offset - 1)

        // Search for an open index, starting from the index we last inserted a single offset.
        while (store.safeGetParent(++singleChildCache) != RESERVED_VALUE);

        return singleChildCache
    }

    /**
     * Moves the children of the node with the least children in order prevent two nodes having their children as the
     * same index.
     *
     * A major upside to the [double array structure](https://www.co-ding.com/assets/pdf/dat.pdf) is that all children
     * of nodes can be stored in the same array, and accessed by simply navigating to an index in that array. The
     * obvious problem with this approach however is that when new nodes are added, a node may already exist at the
     * index the new node should go. If this is the case, the node with the least amount of children has its base
     * offset changed, and all of its children moved to a new index. The original double array structure had two
     * values (the base and check), while this implementation moves all five values associated with a node. The original
     * values are kept the same, except for the parent which is set to [RESERVED_VALUE], indicating that index is
     * free to be used in the future.
     *
     * Unlike the original double array structure, which brute force searches for a nodes children, this structure uses
     * the extra space needed for Aho-Corasick transitions later on in the build to keep track of a nodes children. This
     * is extra important considering the original structure had a maximum offset of 256, while Java's char has a
     * maximum of 65535, meaning the brute force search would be approximately 256 times longer.
     *
     * @param originalParentIndex The index of parent already containing a child in the overlap index.
     * @param encroachingParentIndex The index of the parent wishing to add its new child to the overlap index.
     * @param wantedInsertion The offset of the child to be added to the encroaching parent.
     * @return The new location for the encroaching parent. Only different from [encroachingParentIndex] if the
     *         encroaching parent was a child of the original parent.
     */
    private fun handleBaseOffsetConflict(
        originalParentIndex: Int,
        encroachingParentIndex: Int,
        wantedInsertion: Int
    ): Int {

        val originalChildren = determineChildOffsets(originalParentIndex, childrenOffsetStore1)
        val encroachingChildren = determineChildOffsets(encroachingParentIndex, childrenOffsetStore2)
        encroachingChildren.add(wantedInsertion)

        val toMoveParent = if (encroachingChildren.size > originalChildren.size) originalParentIndex else encroachingParentIndex
        val toMoveChildren = if (toMoveParent == originalParentIndex) originalChildren else encroachingChildren

        // Find the new index to store the children of the node with the least children.
        val postMoveBaseOffset = findOpenIndexes(toMoveChildren)

        // The store containing the children not being moved can be reused to repeatedly store the children of a child
        // which is being moved. This is done to reduce memory usage and GC churning.
        val unusedStore = if (toMoveParent == originalParentIndex) encroachingChildren else originalChildren

        // The wanted insertion was originally added to encroachingChildren in order to guarantee that it could be
        // inserted into an empty index. However, it is not currently stored anywhere, so it's removed from the array
        // as to not have that index moved.
        encroachingChildren.pop()

        for (childIndex in 0 until toMoveChildren.size) {

            val offset = toMoveChildren[childIndex]
            val previousChildIndex = store.getBaseOffset(toMoveParent) + offset
            val newChildIndex = postMoveBaseOffset + offset

            // Update all the children of the current child to point to the child's new index.
            val childChildren = determineChildOffsets(previousChildIndex, unusedStore)
            for (childChildIndex in 0 until childChildren.size) {
                store.setParent(store.getBaseOffset(previousChildIndex) + childChildren[childChildIndex], newChildIndex)
            }

            // The offset for child index is the largest, so we only need to do one safe set as the new index.
            store.synchronizedSafeSet(
                newChildIndex,
                store.getBaseOffset(previousChildIndex),
                toMoveParent,
                store.getValue(previousChildIndex),
                store.getNextSiblingOffset(previousChildIndex),
                store.getChildOffset(previousChildIndex)
            )

            // Indicate the previously used space can now be used.
            store.setParent(previousChildIndex, RESERVED_VALUE)
            if (previousChildIndex < singleChildCache) indexCache.add(previousChildIndex)
        }

        // There is a rare situation where the encroaching parent is the child of the node being encroached upon. In
        // this case, the parent ends up being moved, and the updated parent index needs to be returned. This is handled
        // by adding the amount the original node's children shifted during the move to the index of the encroaching
        // parent.
        var encroachingParentOffset = 0
        if (store.getParent(encroachingParentIndex) == RESERVED_VALUE) {
            encroachingParentOffset = postMoveBaseOffset - store.getBaseOffset(originalParentIndex)
        }

        store.setBaseOffset(toMoveParent, postMoveBaseOffset)

        return encroachingParentIndex + encroachingParentOffset
    }

    /**
     * Adds a node as a child of [parentIndex], updating the parent's children's sibling references in the process.
     *
     * All parent nodes need to keep track of all their children, so in the case that the parent node is moved during
     * construction of the double array structure, it will not have to brute force search for its children. A parent
     * will also need references to its children when constructing the Aho-Corasick failure transitions, since a breadth
     * first search needs to be done. In order to keep track of its children, each node has the offset of its first
     * child, as well as an offset representing the character of another child of the nodes parent. This effectively
     * forms a singly linked list, whereas long as the parent has access to one of it children, it can access all of
     * them. It also allows for constant insertion time for new children by simply changing the offset of the first
     * child. Only two values are needed to keep track of children this way, and that space is used later on in the
     * algorithm. This means the peak memory usage should never go above the memory needed to store the end result of
     * the created structure as long as the resizing of underlying structures is ignored.
     *
     * @param index Index to add the node to. Assumed to not be in use by another node.
     * @param parentIndex Index of the added nodes parent.
     */
    private fun addNode(index: Int, parentIndex: Int) {

        nodes++

        val firstChildOffset = store.getChildOffset(parentIndex)
        val currentNodeOffset = index - store.getBaseOffset(parentIndex)

        var siblingOffset = currentNodeOffset
        if (firstChildOffset == RESERVED_VALUE) {
            // The case where the parent node does not have a child yet. In this case we have the first child of the parent
            // set to the current node, and then we make the current node point to itself, as it's an only child.
            store.setChildOffset(parentIndex, currentNodeOffset)
        } else {
            // The case where the new child node needs to be incorporated with other children. In this case we insert
            // the current node into the linked list as the child directly after the first child for a constant time.
            val firstChildIndex = store.getBaseOffset(parentIndex) + firstChildOffset
            siblingOffset = store.getNextSiblingOffset(firstChildIndex)
            store.setNextSiblingOffset(firstChildIndex, currentNodeOffset)
        }

        store.synchronizedSafeSet(
            index,
            RESERVED_VALUE,
            parentIndex,
            RESERVED_VALUE, // If this node corresponds to the end of a key, the correct value is set later on.
            siblingOffset,
            RESERVED_VALUE
        )
    }

    /**
     * Determines the offsets for the children of the given [parent].
     *
     * Each node has 5 values in this implementation of the AhoCorasick structure. The last two of these values are
     * constantly reused during different parts of the construction process in order to reduce peak memory usage. In
     * the trie construction stage, each node contains the offset of a sibling node, or the offset of the node itself
     * in the case it is an only child. These offsets end up forming a circular singly linked list connecting all
     * sibling nodes, which allows us to determine all children of a parent as long as the parent has a reference to
     * one of its children. We can simply keep iterating through the children, adding the offsets to collection as we
     * go, until we reach the offset we started with, which indicates all children have been found.
     *
     * @param parent Index of the parent node.
     * @param childrenOffsetStore [UnboxedIntList] to store children in. Uses existing list for efficiency purposes.
     * @return [childrenOffsetStore] populated with child offsets, if [parent] has children.
     */
    private fun determineChildOffsets(parent: Int, childrenOffsetStore: UnboxedIntList): UnboxedIntList {

        childrenOffsetStore.clear()
        val firstChildOffset = store.getChildOffset(parent)

        // Parent does not have an index of a child, meaning it has no children.
        if (firstChildOffset == RESERVED_VALUE) return childrenOffsetStore

        // Used to determine when we have iterated through the entire singly linked list.
        val parentBaseOffset = store.getBaseOffset(parent)
        val initialOffset = store.getNextSiblingOffset(parentBaseOffset + firstChildOffset)
        var currentOffset = initialOffset

        // Iterate through children until we reach the initial child again.
        do {
            val nextIndex = parentBaseOffset + currentOffset
            childrenOffsetStore.add(currentOffset)
            currentOffset = store.getNextSiblingOffset(nextIndex)
        } while (currentOffset != initialOffset)

        return childrenOffsetStore
    }

    /**
     * Calculates the index to transition to when a node does not have a child corresponding to a character. Assumes
     * the node's parent already has its failure index calculated.
     *
     * A nodes failure index is the index of the node which has the largest path corresponding to a suffix of the
     * current node's path. The failure index should always represent the same type of character as the current
     * node, except for the case which no node has a path corresponding to a suffix, in which case the root node is
     * used as the failure index instead.
     *
     * As long as nodes failure indexes are calculated by processing nodes in ascending order based on their depth
     * relative to the root node, a nodes failure transition can be efficiently calculated based on a nodes parent
     * failure index. One simply needs to continuously iterate up the tree via the failure indexes of the parent
     * until a node is reached which has a child corresponding the character the current node represents, or the root
     * node is reached. This strategy is used here, as [constructFailStates] iterates through nodes in such a way that
     * the parent will always be processed first.
     *
     * Ex:
     * Say we create a dictionary with the words `{ baby, byte }`. Note that the prefix `by` in `byte` is the same as
     * the suffix `by` in `baby`. Both words share the `b` node, whose failure index is [ROOT_NODE], since it has
     * no prefix. No word in the dictionary starts with `a`, so its failure index is also [ROOT_NODE]. The second
     * `b` of `baby` has a failure transition to the first `b`, since that prefix which makes up the largest suffix of
     * the substring `bab`. The `y` in baby has a failure transition to the `y` in `byte`, since `by` is the largest
     * suffix which corresponds to a prefix of a word. The `y`, `t`, and `e` failure transitions in `byte` all point to
     * [ROOT_NODE], since no strings exist which start with those characters.
     *
     * The failure transition from `y` of `baby` allows us to efficiently start matching against the next string `byte`.
     * Once we see that the `y` in `baby` has no more characters after it, we switch in constant time to the `y` in
     * `byte` via the failure transition, which would allow the string `babyte` to match both `baby` and `byte`.
     *
     * Failure transitions also allow for the matching of substrings within a word. For example, if we had the words
     * `{ cat, a }`, the failure transition from the `a` in `cat to the `a` in `a` would allow the word `cat` to match
     * both `cat` and `a`.
     *
     * @param node Index of the node to calculate the failure index for.
     * @return The index of the node to transition to in the case [node] does not have a wanted child.
     */
    private fun calculateFailureIndex(node: Int): Int {

        val parent = store.getParent(node)
        val nodeOffset = node - store.getBaseOffset(parent)
        var childFailureIndex = store.getFailureIndex(parent)

        // Continuously iterate through failure indexes, starting from this nodes parent.
        while (childFailureIndex != ROOT_NODE && !containsChild(childFailureIndex, nodeOffset)) {
            childFailureIndex = store.getFailureIndex(childFailureIndex)
        }

        // Necessary in case the root node has a child node of the wanted offset.
        if (containsChild(childFailureIndex, nodeOffset)) {
            childFailureIndex = store.getBaseOffset(childFailureIndex) + nodeOffset
        }

        return childFailureIndex
    }

    /**
     * Finds the index of the node with the largest key that makes up the suffix of the path leading to the given
     * node. Assumes the node's parent already has its string prefix calculated.
     *
     * String prefixes for shorter words are calculated before longer words, as are failure indexes (See
     * [constructFailStates]), which means we can simply iterate through the string prefixes of this index's
     * failure transition until we reach a node that corresponds to an end of a key, or we reach the root node.
     *
     * Technically one could manually iterate up the failure transitions each time a node corresponding to a key is
     * found, meaning we would not need to cache this value, and therefore only need 4 values per node instead of 5 for
     * the fully built Aho-Corasick structure. However, the index the string prefix is written to ends up being
     * needed for caching child offsets earlier on in the build process, so using this space to slightly improve match
     * times by skipping failure transitions that don't lead to a node representing the end of a key was done instead.
     *
     * Ex:
     * Assume the dictionary given to the Aho-Corasick contained the following words: { `bobcat`, `cat`, `at` }, and we
     * are constructing the string prefix for the node `t` in `bobcat`. If this node is reached, all three words in the
     * dictionary should be output. This is accomplished by having the string prefix for the node `t` in `bobcat` point
     * to the node `t` in `cat`, which in turn points to the node `t` in `at`, which in turn would point to the root
     * node, indicating there is no further key which makes up a suffix of the current path.
     *
     * @param node Index of the node to process the string prefix for.
     * @return Index of the node whose path contains the largest suffix of the current path.
     */
    private fun calculatePrefixIndex(node: Int): Int {

        val failureIndex = store.getFailureIndex(node)

        if (store.getValue(failureIndex) != RESERVED_VALUE) return failureIndex
        return store.getPrefixIndex(failureIndex)
    }

    /**
     * Prepares a nodes children for failure transition processing by having them emulate a queue structure, which
     * allows for breadth first processing.
     *
     * In order to efficiently calculate a nodes failure transitions (See [calculateFailureIndex]), nodes must be
     * processed in a breadth first way so all parents are processed before their children. In order to prevent the
     * creation of separate queue for this, which would increase peak memory usage, the index used for the
     * sibling child is reused to instead point to the node that should be processed after the child node is processed.
     * This ultimately emulates a queue, and guarantees that parents are always processed before their children.
     *
     * @param parent Index of the parent with children to prepare for processing.
     * @param lastQueuedNode Reference used to store the last node added to the emulated queue. Updated to point
     *        to the last child processed.
     */
    private fun prepareChildrenForProcessing(parent: Int, lastQueuedNode: IntReference) {

        val rootChildOffsets = determineChildOffsets(parent, childrenOffsetStore1)

        for (childIndex in 0 until rootChildOffsets.size) {

            val child = store.getBaseOffset(parent) + rootChildOffsets[childIndex]
            store.setQueueIndex(lastQueuedNode.value, child)
            lastQueuedNode.value = child

            // When a node is first added to the structure, RESERVED_VALUE is used to indicate the node does not have
            // children. This allows certain expensive processing steps to be skipped. However, navigation through all
            // nodes when finding key value pairs is more efficient if a check for the base offset being less than zero
            // does not need to be done. The new value doesn't really matter since getParent is used to determine if a
            // node has an appropriate child, but zero ensures the base offset will always be a valid index in store.
            if (store.getBaseOffset(child) == RESERVED_VALUE) store.setBaseOffset(child, 0)
        }

        // Make sure the index for the last item in the queue does not point to a valid index.
        store.setQueueIndex(lastQueuedNode.value, RESERVED_VALUE)
    }

    /**
     * Constructs the failure transitions and string prefixes for the root nodes children, and also prepares the
     * children's children for preprocessing.
     *
     * These children need to be processed separately from other children since the root node has no parent.
     *
     * @param lastQueuedNode Reference to the index of the node last added to the queue of nodes to process.
     * @param currentIndex Reference to the index of the last node processed.
     * @return The number of children the root node had.
     */
    private fun processRootChildren(lastQueuedNode: IntReference, currentIndex: IntReference): Int {

        var rootChildren = 0

        while (currentIndex.value != RESERVED_VALUE && store.getParent(currentIndex.value) == ROOT_NODE) {

            prepareChildrenForProcessing(currentIndex.value, lastQueuedNode)
            val nextIndex = store.getQueueIndex(currentIndex.value)
            store.setFailureIndex(currentIndex.value, ROOT_NODE)
            store.setPrefixIndex(currentIndex.value, RESERVED_VALUE)
            currentIndex.value = nextIndex
            rootChildren++
        }

        return rootChildren
    }

    /**
     * Used to lazily populate a stream with results from this [AhoCorasickBase].
     */
    private inner class AhoCorasickSpliterator(private val input: String) :
        Spliterators.AbstractSpliterator<AhoCorasickResult<T>>(Long.MAX_VALUE, DISTINCT.or(NONNULL).or(IMMUTABLE)) {

        /**
         * The current state of the Aho-Corasick structure.
         *
         * Allows for lazily returning values via [tryAdvance].
         */
        var currentNode = ROOT_NODE

        /**
         * The next index of [input] to advance the state of the Aho-Corasick structure.
         *
         * Used to by [tryAdvance] to lazily return results.
         */
        var currentInputIndex = 0

        var nextNode: Int = RESERVED_VALUE

        override fun tryAdvance(action: Consumer<in AhoCorasickResult<T>>): Boolean {

            while (currentInputIndex < input.length || nextNode != RESERVED_VALUE) {

                var nextValue: Int
                if (nextNode == RESERVED_VALUE) {
                    currentNode = calculateNextState(currentNode, input[currentInputIndex++].normalize().code)
                    nextNode = store.getPrefixIndex(currentNode)
                    nextValue = store.getValue(currentNode)

                    // Determine if the current node is the end of a string.
                    if (nextValue == RESERVED_VALUE) continue
                } else {

                    // Add any other values whose keys are equal to a suffix of the path from the root to current node.
                    nextValue = store.getValue(nextNode)
                    nextNode = store.getPrefixIndex(nextNode)
                }

                val result = generateResult(currentInputIndex, nextValue, input)
                if (!findOnlyWholeWords || passesWhitespaceChecks(result, input)) {
                    action.accept(result)
                    return true
                }
            }

            return false
        }
    }

    /**
     * Makes the string lowercase via character by character transformation if [isCaseSensitive] is `false`, null-op
     * otherwise.
     *
     * Character by character transformation is done to keep the length of the normalized string the same as the
     * original.
     */
    private fun String.normalize(): String {

        if (isCaseSensitive) return this

        val characters = toCharArray()
        for (i in characters.indices) characters[i] = Character.toLowerCase(characters[i])

        return String(characters)
    }

    /**
     * Makes the character lowercase if [isCaseSensitive] is `false`, null-op otherwise.
     */
    private fun Char.normalize(): Char = if (isCaseSensitive) this else Character.toLowerCase(this)

    private companion object {

        /**
         * The index of the root node in [store].
         */
        const val ROOT_NODE = 0

        /**
         * Matches a single character of whitespace.
         */
        val WHITESPACE_REGEX = Regex("\\s")
    }

    /**
     * Allows one to pass an `int` by reference.
     */
    private class IntReference(var value: Int)
}
