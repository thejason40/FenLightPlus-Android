package com.fenlight.companion.data.update

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateCheckerTest {

    private lateinit var server: MockWebServer
    private lateinit var checker: UpdateChecker

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        checker = UpdateChecker(versionUrl = server.url("/version.json").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueJson(body: String) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
    }

    @Test
    fun check_newerRemoteVersion_returnsAvailableWithParsedInfo() = runBlocking {
        enqueueJson(
            """
            {"versionName":"1.2.0","versionCode":5,
             "apkUrl":"https://example.com/app.apk",
             "releaseNotes":"Fixes","sha256":"abc123"}
            """.trimIndent(),
        )

        val result = checker.check(currentVersionCode = 4)

        val info = (result as UpdateResult.Available).info
        assertEquals("1.2.0", info.versionName)
        assertEquals(5, info.versionCode)
        assertEquals("https://example.com/app.apk", info.apkUrl)
        assertEquals("Fixes", info.releaseNotes)
        assertEquals("abc123", info.sha256)
    }

    @Test
    fun check_equalVersion_returnsUpToDate() = runBlocking {
        enqueueJson("""{"versionName":"1.0","versionCode":3,"apkUrl":"u"}""")
        assertEquals(UpdateResult.UpToDate, checker.check(currentVersionCode = 3))
    }

    @Test
    fun check_lowerRemoteVersion_returnsUpToDate() = runBlocking {
        enqueueJson("""{"versionName":"1.0","versionCode":2,"apkUrl":"u"}""")
        assertEquals(UpdateResult.UpToDate, checker.check(currentVersionCode = 3))
    }

    @Test
    fun check_missingOptionalFields_appliesDefaults() = runBlocking {
        enqueueJson("""{"versionName":"1.0","versionCode":9,"apkUrl":"u"}""")

        val info = (checker.check(currentVersionCode = 1) as UpdateResult.Available).info

        assertEquals("", info.releaseNotes)
        assertNull(info.sha256)
    }

    @Test
    fun check_httpError_returnsError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(UpdateResult.Error("HTTP 500"), checker.check(currentVersionCode = 1))
    }

    @Test
    fun check_malformedJson_returnsError() = runBlocking {
        enqueueJson("{not json")
        assertTrue(checker.check(currentVersionCode = 1) is UpdateResult.Error)
    }

    @Test
    fun check_emptyBody_returnsError() = runBlocking {
        enqueueJson("")
        assertTrue(checker.check(currentVersionCode = 1) is UpdateResult.Error)
    }
}
