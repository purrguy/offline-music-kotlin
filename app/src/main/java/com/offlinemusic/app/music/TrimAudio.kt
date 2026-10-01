package com.offlinemusic.app.music

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Universal trim: decode any playable audio/video to PCM, slice, write 16-bit WAV
 * at the source sample rate (same quality, no MP3 re-encode loss).
 */
object TrimAudio {

    fun probeDurationMs(ctx: Context, uri: Uri): Long? = runCatching {
        val ext = MediaExtractor()
        ext.setDataSource(ctx, uri, null)
        for (i in 0 until ext.trackCount) {
            val f = ext.getTrackFormat(i)
            if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/") && f.containsKey(MediaFormat.KEY_DURATION)) {
                val us = f.getLong(MediaFormat.KEY_DURATION)
                ext.release()
                return us / 1000
            }
        }
        ext.release()
        null
    }.getOrNull()

    fun trimToWav(ctx: Context, src: Uri, startMs: Long, endMs: Long, out: File) {
        val ext = MediaExtractor()
        ext.setDataSource(ctx, src, null)
        val track = (0 until ext.trackCount).firstOrNull { i ->
            (ext.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")
        } ?: throw IllegalArgumentException("No audio track in file")
        ext.selectTrack(track)
        val format = ext.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcm = ByteArrayOutputStream()
        var outFormat: MediaFormat? = null
        val bufInfo = MediaCodec.BufferInfo()
        var eosIn = false
        var eosOut = false
        while (!eosOut) {
            if (!eosIn) {
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val ib = codec.getInputBuffer(inIdx)!!
                    val n = ext.readSampleData(ib, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        eosIn = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, n, ext.sampleTime, 0)
                        ext.advance()
                    }
                }
            }
            val outIdx = codec.dequeueOutputBuffer(bufInfo, 10_000)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFormat = codec.outputFormat
                outIdx >= 0 -> {
                    val ob = codec.getOutputBuffer(outIdx)!!
                    if (bufInfo.size > 0) {
                        val chunk = ByteArray(bufInfo.size)
                        ob.position(bufInfo.offset)
                        ob.get(chunk)
                        pcm.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) eosOut = true
                }
            }
        }
        codec.stop(); codec.release(); ext.release()

        val fmt = outFormat ?: format
        val pcmBytes = to16BitPcm(pcm.toByteArray(), fmt)

        val bytesPerFrame = channels * 2
        val totalFrames = pcmBytes.size / bytesPerFrame
        val s = ((startMs.coerceAtLeast(0) * sampleRate) / 1000).coerceIn(0L, totalFrames.toLong()).toInt()
        val e = ((endMs.coerceAtLeast((s + 1).toLong()) * sampleRate) / 1000).coerceIn((s + 1).toLong(), totalFrames.toLong()).toInt()
        val sliced = pcmBytes.copyOfRange(s * bytesPerFrame, e * bytesPerFrame)
        out.writeBytes(wavBytes(sliced, channels, sampleRate))
    }

    private fun to16BitPcm(raw: ByteArray, fmt: MediaFormat): ByteArray {
        val enc = if (fmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            fmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else android.media.AudioFormat.ENCODING_PCM_16BIT
        if (enc == android.media.AudioFormat.ENCODING_PCM_16BIT) return raw
        // Assume 32-bit float -> convert to 16-bit.
        val floats = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val out = ByteBuffer.allocate(floats.remaining() * 2).order(ByteOrder.LITTLE_ENDIAN)
        while (floats.hasRemaining()) {
            val s = floats.get().coerceIn(-1f, 1f)
            out.putShort(((s * 32767).toInt()).toShort())
        }
        return out.array()
    }

    private fun wavBytes(pcm: ByteArray, channels: Int, sampleRate: Int): ByteArray {
        val buf = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        fun str(s: String) = s.forEach { buf.put(it.code.toByte()) }
        str("RIFF"); buf.putInt(36 + pcm.size); str("WAVE"); str("fmt ")
        buf.putInt(16); buf.putShort(1); buf.putShort(channels.toShort())
        buf.putInt(sampleRate); buf.putInt(sampleRate * channels * 2)
        buf.putShort((channels * 2).toShort()); buf.putShort(16); str("data")
        buf.putInt(pcm.size); buf.put(pcm)
        return buf.array()
    }
}
