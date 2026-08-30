package com.thripleq.nume.core.repo

import android.util.Log
import com.thripleq.nume.core.net.NetEaseGateway
import com.thripleq.nume.core.net.NeteaseOp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** Logged-in user summary (from /weapi/nuser/account/get). */
data class Account(
    val uid: Long,
    val nickname: String,
    val avatarUrl: String?,
    val vipType: Long,
)

/** A purchased digital album. */
data class Album(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val artist: String,
)

/** A playlist entry in the user's "my playlists" (created or subscribed). */
data class PlaylistSummary(
    val id: String,
    val name: String,
    val coverUrl: String?,
    val trackCount: Long,
    val subscribed: Boolean,
)

/**
 * Account-scoped content source (Profile tab): login state, liked tracks,
 * purchases and the user's playlists. Every call goes through the single
 * serialized [NetEaseGateway]; `code 301` means "not logged in".
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val gateway: NetEaseGateway,
) {

    /** Current account, or null when not logged in (code 301) / on error. */
    suspend fun account(): Account? = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.USER_ACCOUNT)
        if (r.err != 0 || r.code != 200.0) return@withContext null
        try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val account = root.optJSONObject("account") ?: return@withContext null
            val profile = root.optJSONObject("profile")
            Account(
                uid = account.optLong("id", 0L),
                nickname = profile?.optString("nickname")
                    ?: account.optString("userName"),
                avatarUrl = profile?.optString("avatarUrl")?.takeIf { it.isNotBlank() },
                vipType = profile?.optLong("vipType", 0L) ?: 0L,
            )
        } catch (e: Exception) {
            Log.e("ProfileRepository", "account parse failed: ${e.message}")
            null
        }
    }

    /** Liked tracks of [uid]: ids from /weapi/song/like/get → details. */
    suspend fun likedTracks(uid: Long): List<Track> = withContext(Dispatchers.IO) {
        val ids = rawIds(NeteaseOp.LIKE_LIST, uid.toString())
            ?: return@withContext emptyList()
        songDetails(ids)
    }

    /** Purchased single tracks (/api/single/mybought/song/list). */
    suspend fun purchasedSongs(): List<Track> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.SONG_PURCHASED, "100", "0")
        if (r.err != 0 || r.code != 200.0) return@withContext emptyList()
        try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val list = root.optJSONArray("data")
                ?: root.optJSONObject("data")?.optJSONArray("list")
                ?: return@withContext emptyList()
            buildList {
                for (i in 0 until list.length()) {
                    val o = list.optJSONObject(i) ?: continue
                    val song = o.optJSONObject("song") ?: o
                    parseTrack(song)?.let { add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e("ProfileRepository", "purchased songs parse failed: ${e.message}")
            emptyList()
        }
    }

    /** Purchased digital albums (/api/digitalAlbum/purchased). */
    suspend fun purchasedAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.ALBUM_PURCHASED, "100", "0")
        if (r.err != 0 || r.code != 200.0) return@withContext emptyList()
        try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val list = root.optJSONObject("data")?.optJSONArray("list")
                ?: root.optJSONArray("data")
                ?: root.optJSONArray("paidAlbums")
                ?: return@withContext emptyList()
            buildList {
                for (i in 0 until list.length()) {
                    val o = list.optJSONObject(i) ?: continue
                    val album = o.optJSONObject("album") ?: o
                    val id = album.optLong("id", 0L)
                    if (id <= 0) continue
                    add(
                        Album(
                            id = id.toString(),
                            name = album.optString("name"),
                            coverUrl = album.optString("picUrl")
                                .takeIf { it.isNotBlank() },
                            artist = album.optJSONArray("artists")
                                ?.optJSONObject(0)?.optString("name") ?: "",
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ProfileRepository", "purchased albums parse failed: ${e.message}")
            emptyList()
        }
    }

    /** The user's playlists, split into subscribed (collected) and created. */
    suspend fun playlists(uid: Long): Pair<List<PlaylistSummary>, List<PlaylistSummary>> =
        withContext(Dispatchers.IO) {
            val r = gateway.call(NeteaseOp.USER_PLAYLIST, uid.toString(), "100", "0")
            if (r.err != 0 || r.code != 200.0) return@withContext Pair(emptyList(), emptyList())
            try {
                val root = JSONObject(String(r.body, Charsets.UTF_8))
                val arr = root.optJSONArray("playlist") ?: return@withContext Pair(emptyList(), emptyList())
                val subscribed = mutableListOf<PlaylistSummary>()
                val created = mutableListOf<PlaylistSummary>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optLong("id", 0L)
                    if (id <= 0) continue
                    val p = PlaylistSummary(
                        id = id.toString(),
                        name = o.optString("name"),
                        coverUrl = o.optString("coverImgUrl").takeIf { it.isNotBlank() },
                        trackCount = o.optLong("trackCount", 0L),
                        subscribed = o.optBoolean("subscribed", false),
                    )
                    if (p.subscribed) subscribed.add(p) else created.add(p)
                }
                Pair(subscribed, created)
            } catch (e: Exception) {
                Log.e("ProfileRepository", "playlists parse failed: ${e.message}")
                Pair(emptyList(), emptyList())
            }
        }

    /** Tracks of a playlist (playlist id IS usable as chart id). */
    suspend fun playlistTracks(playlistId: String): List<Track> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.PLAYLIST_DETAIL, playlistId, "0")
        if (r.err != 0 || r.code != 200.0) return@withContext emptyList()
        try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val playlist = root.optJSONObject("playlist") ?: return@withContext emptyList()
            parseTracks(playlist.optJSONArray("tracks"))
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Tracks of a purchased album (/weapi/v1/album/{id}). */
    suspend fun albumTracks(albumId: String): List<Track> = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.ALBUM_DETAIL, albumId)
        if (r.err != 0 || r.code != 200.0) return@withContext emptyList()
        try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            parseTracks(root.optJSONArray("songs"))
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Sends the SMS login code to [phone]; returns true on code 200. */
    suspend fun sendCaptcha(phone: String): Boolean = withContext(Dispatchers.IO) {
        val r = gateway.call(NeteaseOp.SEND_CAPTCHA, phone, "86")
        r.err == 0 && r.code == 200.0
    }

    /** Logs in with the SMS code; returns true on code 200. */
    suspend fun loginWithCaptcha(phone: String, captcha: String): Boolean =
        withContext(Dispatchers.IO) {
            val r = gateway.call(NeteaseOp.LOGIN_CELLPHONE_CAPTCHA, phone, captcha, "86")
            r.err == 0 && r.code == 200.0
        }

    // ── helpers ────────────────────────────────────────────

    private suspend fun rawIds(op: Int, arg: String): List<String>? {
        val r = gateway.call(op, arg)
        if (r.err != 0 || r.code != 200.0) return null
        return try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            val arr = root.optJSONArray("ids") ?: return null
            buildList { for (i in 0 until arr.length()) add(arr.optLong(i, 0L).toString()) }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun songDetails(ids: List<String>): List<Track> {
        if (ids.isEmpty()) return emptyList()
        // /weapi/v3/song/detail accepts up to ~1000 ids in one call.
        val r = gateway.call(NeteaseOp.SONG_DETAIL, ids.joinToString(","))
        if (r.err != 0 || r.code != 200.0) return emptyList()
        return try {
            val root = JSONObject(String(r.body, Charsets.UTF_8))
            parseTracks(root.optJSONArray("songs"))
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseTracks(arr: org.json.JSONArray?): List<Track> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                parseTrack(arr.optJSONObject(i))?.let { add(it) }
            }
        }
    }

    private fun parseTrack(o: JSONObject?): Track? {
        if (o == null) return null
        val id = o.optLong("id", 0L)
        if (id <= 0) return null
        return Track(
            id = id.toString(),
            name = o.optString("name"),
            artist = o.optJSONArray("ar")?.optJSONObject(0)?.optString("name")
                ?: o.optJSONArray("artists")?.optJSONObject(0)?.optString("name") ?: "",
            artworkUrl = o.optJSONObject("al")?.optString("picUrl")
                ?.takeIf { it.isNotBlank() },
            durationMs = o.optLong("dt", 0L),
        )
    }
}
