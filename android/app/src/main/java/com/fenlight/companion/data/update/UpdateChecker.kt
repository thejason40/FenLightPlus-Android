package com.fenlight.companion.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// Host this file at https://thejason40.github.io/apk/version.json
// Example content:
// {
//   "versionName": "1.0.1",
//   "versionCode": 2,
//   "apkUrl": "https://thejason40.github.io/apk/FenLightCompanion.apk",
//   "releaseNotes": "Bug fixes and improvements",
//   "sha256": "<hex sha-256 of the APK>"
// }
data class UpdateInfo(
    val versionName: String = "",
    val versionCode: Int = 0,
    val apkUrl: String = "",
    val releaseNotes: String = "",
    // Optional: when present, the downloaded APK is verified against it before install
    val sha256: String? = null,
)

sealed class UpdateResult {
    object UpToDate : UpdateResult()
    data class Available(val info: UpdateInfo) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

class UpdateChecker(
    private val versionUrl: String = DEFAULT_VERSION_URL,
    private val client: OkHttpClient = defaultClient(),
) {
    companion object {
        const val DEFAULT_VERSION_URL = "https://thejason40.github.io/apk/version.json"

        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    suspend fun check(currentVersionCode: Int): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(
                Request.Builder()
                    .url(versionUrl)
                    .build()
            ).execute()
            if (!response.isSuccessful) return@withContext UpdateResult.Error("HTTP ${response.code}")
            val body = response.body?.string()
                ?: return@withContext UpdateResult.Error("Empty response")
            // Parse with org.json (built into Android) rather than reflective Moshi:
            // immune to R8/obfuscation, so no keep-rules or Kotlin metadata required.
            val info = runCatching { parseManifest(body) }.getOrNull()
                ?: return@withContext UpdateResult.Error("Invalid response format")
            if (info.versionCode == 0 || info.versionName.isBlank() || info.apkUrl.isBlank())
                return@withContext UpdateResult.Error("Incomplete update manifest")
            if (info.versionCode > currentVersionCode) UpdateResult.Available(info)
            else UpdateResult.UpToDate
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Network error")
        }
    }

    private fun parseManifest(body: String): UpdateInfo {
        // Strip a leading UTF-8 BOM (Windows editors often add one); org.json,
        // unlike Moshi/okio, does not tolerate it and would throw on the '{'.
        val noBom = if (body.isNotEmpty() && body[0].code == 0xFEFF) body.substring(1) else body
        val o = JSONObject(noBom.trim())
        return UpdateInfo(
            versionName = o.optString("versionName", ""),
            versionCode = o.optInt("versionCode", 0),
            apkUrl = o.optString("apkUrl", ""),
            releaseNotes = o.optString("releaseNotes", ""),
            sha256 = if (o.isNull("sha256")) null else o.optString("sha256", null),
        )
    }
}
