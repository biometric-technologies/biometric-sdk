package net.iriscan.sdk.utils

import net.iriscan.sdk.core.io.asByteArray
import net.iriscan.sdk.core.io.asDataBytes
import net.iriscan.sdk.testutil.pseudoRandomBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * NSData made from a Kotlin array must own a copy of the bytes. If it only pointed at the array,
 * the garbage collector could free the array and reuse its memory while the application still
 * holds the NSData, and a template returned by the SDK would change. A test cannot make the
 * collector free an array and give its memory to another object at a chosen moment, so these
 * tests change the array instead: an NSData that shares the array's memory changes with it.
 */
class NSDataExtTest {

    /** Bytes of an iris template with the default settings: 8-byte header, code and mask planes. */
    private val templateSize = 8 + 2 * 512

    @Test
    fun nsDataKeepsItsBytesWhenTheArrayChanges() {
        val expected = pseudoRandomBytes(templateSize, seed = 1)
        val bytes = expected.copyOf()
        val data = bytes.toNSData()
        bytes.fill(0)
        assertContentEquals(expected, data.toByteArray())
    }

    @Test
    fun dataBytesKeepTheirBytesWhenTheArrayChanges() {
        // asDataBytes makes the DataBytes (NSData) that the iris encoder returns to the application
        val expected = pseudoRandomBytes(templateSize, seed = 2)
        val bytes = expected.copyOf()
        val data = bytes.asDataBytes()
        bytes.fill(0)
        assertContentEquals(expected, data.asByteArray())
    }
}
