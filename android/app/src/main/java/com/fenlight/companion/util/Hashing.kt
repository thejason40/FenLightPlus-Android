package com.fenlight.companion.util

import java.io.InputStream
import java.security.MessageDigest

/** Streams [input] through SHA-256 and returns the lowercase hex digest. Closes the stream. */
fun sha256Hex(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    input.use { stream ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val read = stream.read(buf)
            if (read < 0) break
            digest.update(buf, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
