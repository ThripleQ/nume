package com.thripleq.nume.core.repo

import com.thripleq.nume.core.net.NetEaseGateway
import com.thripleq.nume.core.net.NeteaseOp
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

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
@Singleton
class ChartRepository @Inject constructor(
    private val gateway: NetEaseGateway,
) {

    suspend fun charts(): List<Chart> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.TOPLIST_DETAIL)
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
     * 一个榜单的完整壳（元数据 + 曲目）。匿名 /weapi/toplist/detail 里
     * per-chart 的 `tracks` 预览是空的；榜单 id 本身就是歌单 id，改从
     * /weapi/v3/playlist/detail 拉完整集合。
     */
    suspend fun chartCollection(chartId: String): TrackCollection? = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.PLAYLIST_DETAIL, chartId, "0")
        if (r.err != 0) {
            Log.e("ChartRepository", "playlist detail failed: err=${r.err} code=${r.code}")
            return@withContext null
        }
        try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val playlist = root.optJSONObject("playlist") ?: return@withContext null
            parsePlaylistObject(playlist, ::parseTracks)
        } catch (_: Exception) {
            null
        }
    }
}