package com.yesmin.ai.voice

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/** Live microphone capture: 16 kHz mono PCM16, 100 ms chunks. */
class AudioCapture(private val onChunk: (ByteArray) -> Unit) {
    @Volatile var muted = false
    private var rec: AudioRecord? = null
    private var job: Job? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null

    @SuppressLint("MissingPermission") // permission is checked by CallController before start()
    fun start(scope: CoroutineScope) {
        if (job != null) return // never run two mic sessions
        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 6400) * 2
        )
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            throw IllegalStateException("Microphone could not be opened (busy or blocked).")
        }
        if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(r.audioSessionId)?.also { it.setEnabled(true) }
        if (NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(r.audioSessionId)?.also { it.setEnabled(true) }
        rec = r
        r.startRecording()
        job = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(3200)
            while (isActive) {
                val n = r.read(buf, 0, buf.size)
                if (n > 0 && !muted) onChunk(buf.copyOf(n)) else if (n < 0) break
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        rec?.let {
            try { it.stop() } catch (e: Exception) { }
            it.release()
        }
        rec = null
        try { aec?.release() } catch (e: Exception) { }
        try { ns?.release() } catch (e: Exception) { }
        aec = null; ns = null
    }
}

/** Streaming PCM16 player at 24 kHz (Gemini Live output rate). */
class AudioPlayer {
    private val rate = 24000
    private val track: AudioTrack
    private val queue = Channel<ByteArray>(Channel.UNLIMITED)
    private val framesWritten = AtomicLong(0)
    private var writer: Job? = null
    @Volatile private var epoch = 0

    init {
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(maxOf(min, 24000))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
    }

    fun start(scope: CoroutineScope) {
        if (writer != null) return
        writer = scope.launch(Dispatchers.IO) {
            for (chunk in queue) {
                val myEpoch = epoch
                var off = 0
                while (off < chunk.size && isActive && myEpoch == epoch) {
                    val n = track.write(chunk, off, minOf(4800, chunk.size - off))
                    if (n <= 0) break
                    off += n
                    framesWritten.addAndGet((n / 2).toLong())
                }
            }
        }
    }

    fun enqueue(pcm: ByteArray) {
        queue.trySend(pcm)
    }

    /** Barge-in: drop everything queued and stop sound immediately. */
    fun flush() {
        epoch++
        while (queue.tryReceive().isSuccess) { /* drain */ }
        try {
            track.pause(); track.flush(); track.play()
        } catch (e: Exception) { }
        framesWritten.set(0)
    }

    /** Suspends until what was queued has really been played. */
    suspend fun awaitDrained() {
        var guard = 0
        while (guard < 600) { // max 30 s
            val head = try { track.playbackHeadPosition.toLong() } catch (e: Exception) { return }
            if (head >= framesWritten.get() && queue.isEmpty) return
            delay(50); guard++
        }
    }

    fun release() {
        writer?.cancel(); writer = null
        queue.close()
        try { track.pause(); track.flush(); track.release() } catch (e: Exception) { }
    }
}
