package com.fenlight.companion.data.model

import android.net.Uri
import org.json.JSONObject

/**
 * A scraped source returned by FenLight+'s `app_list_sources` directory. The display fields
 * come from the URL-encoded `meta` payload on the item's `file`; `file` is what we Player.Open
 * to play that source (FenLight+ then resolves it, with failover, on the device).
 */
data class FenLightSource(
    val file: String,
    val label: String,
    val provider: String,
    val quality: String,
    val size: String,
    val info: String,
    val name: String,
    val debrid: String,
    val cached: Boolean,
    val seeders: Int?,
    val pack: String?,
) {
    companion object {
        fun fromDirItem(item: KodiDirItem): FenLightSource {
            val meta = runCatching {
                Uri.parse(item.file).getQueryParameter("meta")?.let { JSONObject(it) }
            }.getOrNull() ?: JSONObject()
            return FenLightSource(
                file = item.file,
                label = item.label,
                provider = meta.optString("provider"),
                quality = meta.optString("quality"),
                size = meta.optString("size"),
                info = meta.optString("info"),
                name = meta.optString("name"),
                debrid = meta.optString("debrid"),
                cached = meta.optBoolean("cached", true),
                seeders = if (meta.has("seeders")) meta.optInt("seeders") else null,
                pack = if (meta.has("pack")) meta.optString("pack") else null,
            )
        }
    }
}
