package com.offlinemusic.app.music

/** Unified result across free sources. */
data class SearchTrack(
    val source: String, // "audius" | "jamendo"
    val sourceId: String,
    val title: String,
    val artist: String,
    val artwork: String? = null,
    val streamUrl: String,
    val downloadUrl: String,
    val durationSec: Int? = null,
)

fun fmtTime(totalSec: Long?): String {
    if (totalSec == null || totalSec < 0) return "--:--"
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}
