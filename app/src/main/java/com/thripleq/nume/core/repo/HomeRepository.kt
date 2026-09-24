package com.thripleq.nume.core.repo

import com.thripleq.nume.core.net.NetEaseGateway
import com.thripleq.nume.core.net.NeteaseOp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** A recommended playlist card (personalized/playlist, served anonymously). */
data class PlaylistCard(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val playCount: Long,
    val trackCount: Long,
)

/**
 * Explore (Home) content source. 推荐歌单 / 排行榜匿名可拉；每日推荐歌曲 / 最近播放
 * 需要登录（否则接口返回 301 / 空）。所有拉取容错返回空列表，不抛。
 */
@Singleton
class HomeRepository @Inject constructor(
    private val gateway: NetEaseGateway,
    private val profileRepo: ProfileRepository,
) {

    /** 是否已登录（复用 Profile 的 account 查询，code 301 = 未登录）。 */
    suspend fun loggedIn(): Boolean = profileRepo.account() != null

    /** 个性化推荐歌单（匿名可用）。 */
    suspend fun recommendPlaylists(limit: String = "12"): List<PlaylistCard> =
        withContext(Dispatchers.IO) {
            val r = gateway.call(NeteaseOp.RECOMMEND_PLAYLISTS, limit)
            if (r.err != 0 || r.body.isEmpty()) return@withContext emptyList()
            try {
                val root = JSONObject(String(r.body, Charsets.UTF_8))
                val arr = root.optJSONArray("result")
                    ?: root.optJSONArray("recommend")
                    ?: return@withContext emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optLong("id", 0L)
                        if (id <= 0) continue
                        add(
                            PlaylistCard(
                                id = id.toString(),
                                name = o.optString("name"),
                                coverUrl = o.optString("picUrl").takeIf { it.isNotBlank() },
                                playCount = o.optLong("playCount", o.optLong("playcount", 0L)),
                                trackCount = o.optLong("trackCount", 0L),
                            ),
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    /** 每日推荐歌曲（需登录）。 */
    suspend fun dailySongs(): List<Track> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.RECOMMEND_SONGS)
        if (r.err != 0 || r.body.isEmpty()) return@withContext emptyList()
        try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val data = root.optJSONObject("data")
            parseTracks(data?.optJSONArray("dailySongs") ?: data?.optJSONArray("recommend"))
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 最近播放（需登录）。返回结构防御性兼容 list[].song / list[].data / allData[].song。 */
    suspend fun recentSongs(limit: String = "30"): List<Track> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.RECORD_RECENT, limit)
        if (r.err != 0 || r.body.isEmpty()) return@withContext emptyList()
        try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val list = root.optJSONObject("data")?.optJSONArray("list")
                ?: root.optJSONArray("allData")
                ?: return@withContext emptyList()
            buildList {
                for (i in 0 until list.length()) {
                    val o = list.optJSONObject(i) ?: continue
                    val song = o.optJSONObject("song") ?: o.optJSONObject("data") ?: o
                    parseTrack(song)?.let { add(it) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
