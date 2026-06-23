package com.fenlight.companion.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class HashingTest {

    @Test
    fun sha256Hex_knownVector() {
        // NIST test vector for "abc"
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex(ByteArrayInputStream("abc".toByteArray())),
        )
    }

    @Test
    fun sha256Hex_emptyInput() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(ByteArrayInputStream(ByteArray(0))),
        )
    }
}
