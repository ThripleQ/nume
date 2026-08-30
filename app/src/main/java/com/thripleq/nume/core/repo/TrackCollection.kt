package com.thripleq.nume.core.repo

import org.json.JSONObject

/**
 * 一个"壳子 + 列表"：榜单、歌单、专辑都是同一个结构——一段集合元数据
 * （封面/标题/播放量/收藏数/更新频率/描述/创建者）+ 曲目列表。喜欢/已购
 * 没有独立后端壳，由 ViewModel 用已有数据组装一份简化壳。
 */
data class TrackCollection(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val playCount: Long,
    val subscribedCount: Long,
    val trackCount: Long,
    val updateFrequency: String,
    val description: String,
    val creator: String,
    val tracks: List<Track>,
)

/**
 * 从 /weapi/v3/playlist/detail 返回的 playlist 对象解析壳元数据 + 曲目。
 * 榜单 id 就是歌单 id，两者共用此解析；曲目用共享的 [parseTracks]。
 */
fun parsePlaylistObject(obj: JSONObject): TrackCollection {
    val id = obj.optLong("id", 0L)
    val creator = obj.optJSONObject("creator")?.optString("nickname")
        ?.takeIf { it.isNotBlank() } ?: ""
    return TrackCollection(
        id = id.toString(),
        name = obj.optString("name"),
        coverUrl = obj.optString("coverImgUrl").takeIf { it.isNotBlank() },
        playCount = obj.optLong("playCount", 0L),
        subscribedCount = obj.optLong("subscribedCount", 0L),
        trackCount = obj.optLong("trackCount", 0L),
        updateFrequency = obj.optString("updateFrequency").takeIf { it.isNotBlank() } ?: "",
        description = obj.optString("description").takeIf { it.isNotBlank() } ?: "",
        creator = creator,
        tracks = parseTracks(obj.optJSONArray("tracks")),
    )
}

/** 从 /weapi/v1/album/{id} 的响应解析专辑壳。该接口顶层只有 songs，没有
 *  album 对象；专辑元数据（名称/封面/歌手/曲目数）从首曲的 al/ar 推断。 */
fun parseAlbumObject(root: JSONObject): TrackCollection {
    val songsArr = root.optJSONArray("songs")
    val tracks = parseTracks(songsArr)
    val first = songsArr?.optJSONObject(0)
    val al = first?.optJSONObject("al")
    val ar = first?.optJSONArray("ar")?.optJSONObject(0)
    return TrackCollection(
        id = al?.optLong("id", 0L)?.toString() ?: "",
        name = al?.optString("name") ?: "",
        coverUrl = al?.optString("picUrl")?.takeIf { it.isNotBlank() },
        playCount = 0L,
        subscribedCount = 0L,
        trackCount = tracks.size.toLong(),
        updateFrequency = "",
        description = "",
        creator = ar?.optString("name") ?: "",
        tracks = tracks,
    )
}
