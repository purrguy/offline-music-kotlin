package com.offlinemusic.app.music

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/** Single in-process player shared by all screens (v1). */
object PlayerHolder {
    private var player: ExoPlayer? = null

    fun get(ctx: Context): ExoPlayer {
        val p = player
        if (p != null) return p
        return ExoPlayer.Builder(ctx.applicationContext).build().also { player = it }
    }

    fun playUri(ctx: Context, uri: Uri) {
        val p = get(ctx)
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
        p.play()
    }

    fun release() {
        player?.release()
        player = null
    }
}
