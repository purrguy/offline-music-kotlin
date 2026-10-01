package com.offlinemusic.app.music

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app)
    private val tracksDir = File(app.filesDir, "tracks").apply { mkdirs() }

    val library: StateFlow<List<SavedTrack>> =
        db.tracks().observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val results = MutableStateFlow<List<SearchTrack>>(emptyList())
    val busy = MutableStateFlow("")
    val error = MutableStateFlow("")
    val nowPlaying = MutableStateFlow("Nothing playing")
    val wifiOnly = MutableStateFlow(isWifiOnly(app))
    val conn = MutableStateFlow(connectionLabel(app))

    fun refreshConn() {
        conn.value = connectionLabel(getApplication())
    }

    fun setWifiOnly(on: Boolean) {
        setWifiOnly(getApplication(), on)
        wifiOnly.value = on
    }

    fun clearError() { error.value = "" }

    fun search(q: String) {
        error.value = ""
        viewModelScope.launch {
            busy.value = "Searching Audius + Jamendo…"
            results.value = runCatching {
                withContext(Dispatchers.IO) { searchAll(q) }
            }.onFailure { error.value = it.message ?: "Search failed" }.getOrDefault(emptyList())
            busy.value = ""
        }
    }

    fun previewStream(t: SearchTrack) {
        val ctx = getApplication<Application>()
        PlayerHolder.playUri(ctx, t.streamUrl.toUri())
        nowPlaying.value = "${t.title} — ${t.artist} (stream)"
    }

    fun playSaved(t: SavedTrack) {
        val ctx = getApplication<Application>()
        val f = File(tracksDir, t.fileName)
        PlayerHolder.playUri(ctx, Uri.fromFile(f))
        nowPlaying.value = "${t.title} — ${t.artist} (offline)"
    }

    fun downloadTrack(t: SearchTrack, onDone: () -> Unit = {}) {
        val ctx = getApplication<Application>()
        val (ok, reason) = canDownloadNow(ctx)
        if (!ok) { error.value = reason; return }
        viewModelScope.launch {
            busy.value = "Downloading \"${t.title}\"…"
            runCatching {
                val bytes = withContext(Dispatchers.IO) { downloadBytes(t.downloadUrl) }
                val ext = if (t.source == "audius") "mp3" else "mp3"
                val name = "${UUID.randomUUID()}.$ext"
                File(tracksDir, name).writeBytes(bytes)
                db.tracks().upsert(
                    SavedTrack(
                        id = UUID.randomUUID().toString(),
                        title = t.title, artist = t.artist, source = t.source,
                        fileName = name, mime = "audio/mpeg", artwork = t.artwork,
                    )
                )
                onDone()
            }.onFailure {
                error.value = it.message ?: "Download failed. Note: Audius tracks without artist-enabled downloads are stream-only."
            }
            busy.value = ""
        }
    }

    fun deleteTrack(t: SavedTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { File(tracksDir, t.fileName).delete() }
            db.tracks().delete(t.id)
        }
    }

    /** Save picked file whole, or trimmed to WAV when [trim] is true. */
    fun importUri(src: Uri, title: String, trim: Boolean, startMs: Long, endMs: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            busy.value = if (trim) "Trimming…" else "Saving…"
            runCatching {
                withContext(Dispatchers.IO) {
                    if (!trim) {
                        val name = "${UUID.randomUUID()}-upload"
                        File(tracksDir, name).outputStream().use { out ->
                            getApplication<Application>().contentResolver.openInputStream(src)!!.use { it.copyTo(out) }
                        }
                        db.tracks().upsert(
                            SavedTrack(
                                id = UUID.randomUUID().toString(), title = title.ifBlank { "Upload" },
                                artist = "My upload", source = "upload", fileName = name,
                                mime = "audio/*",
                            )
                        )
                    } else {
                        val name = "${UUID.randomUUID()}.wav"
                        TrimAudio.trimToWav(getApplication(), src, startMs, endMs, File(tracksDir, name))
                        db.tracks().upsert(
                            SavedTrack(
                                id = UUID.randomUUID().toString(), title = title.ifBlank { "Upload (trimmed)" },
                                artist = "My upload", source = "upload", fileName = name,
                                mime = "audio/wav", trimStartMs = startMs, trimEndMs = endMs,
                            )
                        )
                    }
                }
                onDone()
            }.onFailure { error.value = it.message ?: "Could not process that file." }
            busy.value = ""
        }
    }

    fun probeDuration(src: Uri, cb: (Long?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            cb(TrimAudio.probeDurationMs(getApplication(), src))
        }
    }

    override fun onCleared() {
        PlayerHolder.release()
    }
}
