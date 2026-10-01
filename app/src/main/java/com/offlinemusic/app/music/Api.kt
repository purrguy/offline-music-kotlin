package com.offlinemusic.app.music

import com.google.gson.annotations.SerializedName
import com.offlinemusic.app.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ---------- Audius ----------
private interface AudiusApi {
    @GET("tracks/search")
    suspend fun search(
        @Query("query") q: String,
        @Query("limit") limit: Int = 15,
        @Query("app_name") app: String = "offline-music-kotlin",
    ): AudiusSearchResp
}

private data class AudiusSearchResp(val data: List<AudiusTrack> = emptyList())
private data class AudiusTrack(
    val id: String,
    val title: String? = null,
    val duration: Int? = null,
    val user: AudiusUser? = null,
    val artwork: Map<String, String?>? = null,
)
private data class AudiusUser(val name: String? = null)

// ---------- Jamendo ----------
private interface JamendoApi {
    @GET("tracks/")
    suspend fun search(
        @Query("client_id") clientId: String = BuildConfig.JAMENDO_CLIENT_ID,
        @Query("format") format: String = "jsonpretty",
        @Query("limit") limit: Int = 15,
        @Query("audioformat") audioformat: String = "mp32",
        @Query("include") include: String = "musicinfo",
        @Query("search") q: String,
    ): JamendoResp
}

private data class JamendoResp(val results: List<JamendoTrack> = emptyList())
private data class JamendoTrack(
    val id: String,
    val name: String? = null,
    @SerializedName("artist_name") val artistName: String? = null,
    val audio: String? = null,
    @SerializedName("album_image") val albumImage: String? = null,
    val image: String? = null,
    val duration: Int? = null,
)

private val http = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

private val audius: AudiusApi = Retrofit.Builder()
    .baseUrl("https://api.audius.co/v1/")
    .client(http)
    .addConverterFactory(GsonConverterFactory.create())
    .build().create(AudiusApi::class.java)

private val jamendo: JamendoApi = Retrofit.Builder()
    .baseUrl("https://api.jamendo.com/v3.0/")
    .client(http)
    .addConverterFactory(GsonConverterFactory.create())
    .build().create(JamendoApi::class.java)

suspend fun searchAudius(q: String): List<SearchTrack> {
    if (q.isBlank()) return emptyList()
    return audius.search(q).data.map { t ->
        SearchTrack(
            source = "audius",
            sourceId = t.id,
            title = t.title ?: "Untitled",
            artist = t.user?.name ?: "Unknown",
            artwork = t.artwork?.get("480x480") ?: t.artwork?.get("150x150"),
            streamUrl = "https://api.audius.co/v1/tracks/${t.id}/stream?app_name=offline-music-kotlin",
            // 404 unless the artist enabled downloads -> treat as stream-only then.
            downloadUrl = "https://api.audius.co/v1/tracks/${t.id}/download?app_name=offline-music-kotlin",
            durationSec = t.duration,
        )
    }
}

suspend fun searchJamendo(q: String): List<SearchTrack> {
    if (q.isBlank()) return emptyList()
    return jamendo.search(q = q).results.mapNotNull { t ->
        val audio = t.audio ?: return@mapNotNull null
        SearchTrack(
            source = "jamendo",
            sourceId = t.id,
            title = t.name ?: "Untitled",
            artist = t.artistName ?: "Unknown",
            artwork = t.albumImage ?: t.image,
            streamUrl = audio,
            downloadUrl = audio,
            durationSec = t.duration,
        )
    }
}

suspend fun searchAll(q: String): List<SearchTrack> {
    if (q.isBlank()) return emptyList()
    val a = runCatching { searchAudius(q) }.getOrDefault(emptyList())
    val j = runCatching { searchJamendo(q) }.getOrDefault(emptyList())
    return a + j
}

/** Raw download used by the ViewModel (WiFi gate lives in Net.kt). */
fun downloadBytes(url: String): ByteArray {
    val req = okhttp3.Request.Builder().url(url).header("User-Agent", "offline-music-kotlin/1.0").build()
    http.newCall(req).execute().use { res ->
        if (!res.isSuccessful) throw IllegalStateException("Download failed: HTTP ${res.code}")
        return res.body!!.bytes()
    }
}
