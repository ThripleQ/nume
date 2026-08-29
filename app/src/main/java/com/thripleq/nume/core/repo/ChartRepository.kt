package com.thripleq.nume.core.repo

import com.thripleq.nume.Gateway
import com.thripleq.nume.core.net.NeteaseOp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class Track(
    val id: String,
    val name: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
)

data class Chart(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val tracks: List<Track>,
)

/**
 * No-login content source. /weapi/toplist/detail is served anonymously, so the
 * public charts are a safe first source until login + personalised lists land.
 */
object ChartRepository {

    suspend fun charts(): List<Chart> = withContext(Dispatchers.IO) {
        val r = Gateway.netease.call(NeteaseOp.TOPLIST_DETAIL)
        if (r.err != 0) return@withContext emptyList()
        try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val list = root.optJSONArray("list") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until list.length()) {
                    val obj = list.optJSONObject(i) ?: continue
                    val id = obj.optLong("id", 0L)
                    if (id <= 0) continue
                    add(
                        Chart(
                            id = id.toString(),
                            name = obj.optString("name"),
                            coverUrl = obj.optString("coverImgUrl").takeIf { it.isNotBlank() },
                            tracks = parseTracks(obj.optJSONArray("tracks")),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseTracks(arr: org.json.JSONArray?): List<Track> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val id = s.optLong("id", 0L)
                if (id <= 0) continue
                add(
                    Track(
                        id = id.toString(),
                        name = s.optString("name"),
                        artist = s.optJSONArray("ar")?.optJSONObject(0)?.optString("name") ?: "",
                        artworkUrl = s.optJSONObject("al")?.optString("picUrl")
                            ?.takeIf { it.isNotBlank() },
                        durationMs = s.optLong("dt", 0L),
                    ),
                )
            }
        }
    }
}