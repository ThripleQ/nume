package com.thripleq.nume.core.repo

import com.thripleq.nume.Gateway
import com.thripleq.nume.core.net.NeteaseOp
import android.util.Log
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
        if (r.err != 0) {
            val preview = String(r.body, 0, minOf(200, r.body.size), Charsets.UTF_8)
            Log.e("ChartRepository", "toplist failed: err=${r.err} code=${r.code} body=${preview}")
            return@withContext emptyList()
        }
        try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            Log.d("ChartRepository", "toplist ok, root keys=${root.length()}, has list=${root.has("list")}")
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

    /**
     * Full track list for a chart. Anonymous /weapi/toplist/detail leaves the
     * per-chart `tracks` preview empty; the chart's id IS a playlist id, so we
     * pull the complete set via /weapi/v3/playlist/detail instead.
     */
    suspend fun chartTracks(chartId: String): List<Track> = withContext(Dispatchers.IO) {
        val r = Gateway.netease.call(NeteaseOp.PLAYLIST_DETAIL, chartId, "0")
        if (r.err != 0) {
            Log.e("ChartRepository", "playlist detail failed: err=${r.err} code=${r.code}")
            return@withContext emptyList()
        }
        try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val playlist = root.optJSONObject("playlist") ?: return@withContext emptyList()
            parseTracks(playlist.optJSONArray("tracks"))
        } catch (_: Exception) {
            emptyList()
        }
    }
}