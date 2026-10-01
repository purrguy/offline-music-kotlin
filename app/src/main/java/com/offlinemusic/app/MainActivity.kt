package com.offlinemusic.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.offlinemusic.app.music.MainViewModel
import com.offlinemusic.app.music.PlayerHolder
import com.offlinemusic.app.music.SearchTrack
import com.offlinemusic.app.music.fmtTime

private enum class Tab(val label: String) { Library("Library"), Search("Search"), Upload("Upload"), Spotify("Spotify") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App(vm: MainViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.Library) }
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    val nowPlaying by vm.nowPlaying.collectAsState()

    MaterialTheme {
        Scaffold(
            bottomBar = {
                Column {
                    MiniPlayer(nowPlaying)
                    NavigationBar {
                        Tab.entries.forEach {
                            NavigationBarItem(
                                selected = tab == it,
                                onClick = { tab = it; vm.refreshConn() },
                                label = { Text(it.label) },
                                icon = {},
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
                if (busy.isNotEmpty()) StatusCard(busy)
                if (error.isNotEmpty()) StatusCard("⚠ $error", isError = true, onDismiss = { vm.clearError() })
                when (tab) {
                    Tab.Library -> LibraryTab(vm)
                    Tab.Search -> SearchTab(vm)
                    Tab.Upload -> UploadTab(vm)
                    Tab.Spotify -> SpotifyTab()
                }
            }
        }
    }
}

@Composable
fun StatusCard(msg: String, isError: Boolean = false, onDismiss: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(msg, Modifier.weight(1f))
            if (onDismiss != null) TextButton(onClick = onDismiss) { Text("dismiss") }
        }
    }
}

@Composable
fun MiniPlayer(nowPlaying: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text("▶ $nowPlaying", style = MaterialTheme.typography.bodySmall)
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            factory = { c ->
                PlayerView(c).apply {
                    player = PlayerHolder.get(c)
                    useController = true
                    controllerShowTimeoutMs = 0
                    controllerHideOnTouch = false
                }
            },
        )
    }
}

@Composable
fun LibraryTab(vm: MainViewModel) {
    val lib by vm.library.collectAsState()
    val wifiOnly by vm.wifiOnly.collectAsState()
    val conn by vm.conn.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Offline library (${lib.size}) · net: $conn")
        Spacer(Modifier.weight(1f))
        Text("WiFi-only")
        Switch(checked = wifiOnly, onCheckedChange = { vm.setWifiOnly(it) })
    }
    if (lib.isEmpty()) Text("Nothing saved yet. Search or upload, download on WiFi, then go offline.")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(lib, key = { it.id }) { t ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(t.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${t.artist} · ${t.source}" +
                            (if (t.trimStartMs != null) " · ✂ ${fmtTime(t.trimStartMs!! / 1000)}–${fmtTime(t.trimEndMs!! / 1000)}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.playSaved(t) }) { Text("Play offline") }
                        TextButton(onClick = { vm.deleteTrack(t) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchTab(vm: MainViewModel) {
    val results by vm.results.collectAsState()
    var q by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(q, { q = it }, Modifier.weight(1f), label = { Text("artist, track, genre…") }, singleLine = true)
        Button(onClick = { vm.search(q) }) { Text("Go") }
    }
    Text("Audius needs no key. Jamendo uses the free client_id. Only downloadable tracks save offline.")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(results, key = { it.source + it.sourceId }) { t: SearchTrack ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(t.title, style = MaterialTheme.typography.titleSmall)
                    Text("${t.artist} · ${t.source}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.previewStream(t) }) { Text("Preview") }
                        Button(onClick = { vm.downloadTrack(t) }) { Text("Download") }
                    }
                }
            }
        }
    }
}

@Composable
fun UploadTab(vm: MainViewModel) {
    var uri by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("") }
    var durMs by remember { mutableLongStateOf(0L) }
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(0L) }
    var backToLibrary by remember { mutableStateOf(false) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? ->
        if (u != null) {
            uri = u
            name = ""
            vm.probeDuration(u) { d ->
                durMs = d ?: 0L
                startMs = 0L
                endMs = d ?: 0L
            }
        }
    }
    Button(onClick = { pick.launch("*/*") }) { Text("Pick MP3 / MP4 / any audio file") }
    if (uri != null) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Track name") }, singleLine = true)
        Text("Duration: ${fmtTime(durMs / 1000)} · video files trim via their audio track")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                startMs.toString(), { startMs = it.toLongOrNull() ?: 0L },
                Modifier.weight(1f), label = { Text("Start (ms)") }, singleLine = true,
            )
            OutlinedTextField(
                endMs.toString(), { endMs = it.toLongOrNull() ?: 0L },
                Modifier.weight(1f), label = { Text("End (ms)") }, singleLine = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.importUri(uri!!, name, false, 0, 0) { backToLibrary = true } }) { Text("Save whole") }
            Button(enabled = durMs > 0, onClick = { vm.importUri(uri!!, name, true, startMs, endMs) { backToLibrary = true } }) { Text("Crop + save") }
        }
        if (backToLibrary) Text("Saved ✓ — open the Library tab to play it offline.")
    }
}

@Composable
fun SpotifyTab() {
    Text("Spotify (desktop link only)", style = MaterialTheme.typography.titleMedium)
    Text("Spotify forbids full-track downloads via API — no app can legally save those offline for free. " +
        "v2 will link your account to browse library/playlists and control playback in the Spotify client. " +
        "Offline stays inside Spotify Premium.")
}
