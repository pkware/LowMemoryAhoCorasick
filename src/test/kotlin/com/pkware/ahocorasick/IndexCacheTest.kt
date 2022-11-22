package com.pkware.ahocorasick

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class IndexCacheTest {

    private lateinit var store: AhoCorasickStore
    private lateinit var cache: IndexCache

    @BeforeEach
    fun setUp() {
        store = AhoCorasickStore()
        cache = IndexCache(store)
    }

    @Test
    fun `values can be added to the cache`() {

        cache.add(10)
        assertThat(cache.size).isEqualTo(1)
    }

    @ParameterizedTest
    @ValueSource(ints = [20, 10, 5])
    fun `value is popped if it is greater than offset`(offset: Int) {

        cache.add(20)
        assertThat(cache.popIndex(offset)).isEqualTo(20)
    }

    @Test
    fun `0 is returned when cache is empty`() {
        assertThat(cache.popIndex(5)).isEqualTo(0)
    }

    @Test
    fun `0 is returned when all cached locations are less than given offset`() {

        cache.add(20)
        cache.add(25)

        assertThat(cache.popIndex(30)).isEqualTo(0)
    }

    @Test
    fun `0 is returned when the only valid cache location is currently in use`() {

        // Set index to a non-reserved value, indicating the location the cache is storing is already in use.
        store.safeSetParent(10, 42)

        cache.add(10)
        assertThat(cache.popIndex(5)).isEqualTo(0)
    }

    @Test
    fun `oldest value in cache is returned first`() {

        cache.add(5)
        cache.add(25)

        assertThat(cache.popIndex(5)).isEqualTo(5)
    }

    @Test
    fun `skips over and drops cached locations already in use`() {

        cache.add(25)
        cache.add(30)
        store.safeSetParent(25, 1) // Indicates the parent at location 5 is in use

        assertThat(cache.popIndex(1)).isEqualTo(30)
        assertThat(cache.size).isEqualTo(0)
    }

    @Test
    fun `value is dropped from cache if missed too many times`() {

        val cache = IndexCache(store, failureTolerance = 2).apply {
            add(5)
            popIndex(10)
            popIndex(15)
        }

        assertThat(cache.size).isEqualTo(0)
    }

    @Test
    fun `limits the number of nodes added`() {

        val cache = IndexCache(store, maxSize = 2).apply {
            add(5)
            add(10)
            add(15)
        }

        assertThat(cache.size).isEqualTo(2)

        // Check to make sure 3 was that node dropped
        assertThat(cache.popIndex(1)).isEqualTo(5)
        assertThat(cache.popIndex(1)).isEqualTo(10)
    }

    private fun AhoCorasickStore.safeSetParent(index: Int, value: Int) {
        synchronizedSafeSet(index, UNUSED, value, UNUSED, UNUSED, UNUSED)
    }

    private companion object {
        private const val UNUSED = -1
    }
}
