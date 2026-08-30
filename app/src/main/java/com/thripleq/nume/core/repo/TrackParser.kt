package com.thripleq.nume.core.repo

import org.json.JSONArray
import org.json.JSONObject

/** A playable song. Album name is captured for the player / lyrics pages. */
data class Track(
    val id: String,
    val name: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
    val albumName: String = "",
)

/** Parses a standard netease song object (`songs`/`tracks` entries, ar/al shape). */
fun parseTrack(o: JSONObject?): Track? {
    if (o == null) return null
    val id = o.optLong("id", 0L)
    if (id <= 0) return null
    val al = o.optJSONObject("al")
    return Track(
        id = id.toString(),
        name = o.optString("name"),
        artist = o.optJSONArray("ar")?.optJSONObject(0)?.optString("name")
            ?: o.optJSONArray("artists")?.optJSONObject(0)?.optString("name") ?: "",
        artworkUrl = al?.optString("picUrl")?.takeIf { it.isNotBlank() },
        durationMs = o.optLong("dt", 0L),
        albumName = al?.optString("name") ?: "",
    )
}

/** Parses a whole song array, skipping unparseable entries. */
fun parseTracks(arr: JSONArray?): List<Track> {
    if (arr == null) return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            parseTrack(arr.optJSONObject(i))?.let { add(it) }
        }
    }
}
