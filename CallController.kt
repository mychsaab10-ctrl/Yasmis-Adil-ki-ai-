package com.yesmin.ai.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.yesmin.ai.Yesmin
import com.yesmin.ai.ai.ChatMsg
import com.yesmin.ai.ai.GeminiException
import com.yesmin.ai.ai.LiveSession
import com.yesmin.ai.ai.Persona
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class CallState { Idle, Connecting, Listening, Thinking, Speaking, Error }
data class Subtitle(val who: String, val text: String)

/**
 * Calling mode. Primary: Gemini Live API (native audio over WebSocket).
 * Fallback: SpeechRecognizer -> Gemini text -> TextToSpeech, loop.
 * All state changes happen on the main thread.
 */
class CallController(private val ctx: Context, private val scope: CoroutineScope) {
    val state = MutableStateFlow(CallState.Idle)
    val status = MutableStateFlow("Not connected")
    val subtitles = MutableStateFlow<List<Subtitle>>(emptyList())
    val muted = MutableStateFlow(false)
    val speaker = MutableStateFlow(true)
    val seconds = MutableStateFlow(0)
    val modeLabel = MutableStateFlow("")

    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var active = false
    private var gen = 0
    private var live: LiveSession? = null
    private var liveReady = false
    private var capture: AudioCapture? = null
    private var player: AudioPlayer? = null
    private var ignoreAudio = false
    private var timerJob: Job? = null
    private var drainJob: Job? = null

    private var stt: Stt? = null
    private var tts: TtsEngine? = null
    private var fbJob: Job? = null
    private val history = mutableListOf<ChatMsg>()
    private var errorStreak = 0

    private var liveWho = ""
    private val liveBuf = StringBuilder()

    private fun hasMic() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (active) return // prevents duplicate mic sessions
        if (!hasMic()) { state.value = CallState.Error; status.value = "Microphone permission denied. Allow it and try again."; return }
        val key = Yesmin.keys.load()
        if (key == null) { state.value = CallState.Error; status.value = "No API key. Add one in Settings."; return }
        active = true
        gen++
        muted.value = false
        subtitles.value = emptyList()
        seconds.value = 0
        history.clear()
        errorStreak = 0
        liveWho = ""
        state.value = CallState.Connecting
        status.value = "Connecting…"
        startServiceAndTimer()
        if (Yesmin.settings.useLive) startLive(key) else startFallback("Text-model voice mode")
    }

    private fun startServiceAndTimer() {
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, CallService::class.java))
        } catch (e: Exception) { /* call still works while the app is in the foreground */ }
        timerJob?.cancel()
        timerJob = scope.launch { while (isActive) { delay(1000); seconds.value = seconds.value + 1 } }
    }

    // ───────────── Live API path ─────────────
    private fun startLive(key: String) {
        modeLabel.value = "Live"
        status.value = "Connecting to Gemini Live…"
        applyRoute(true)
        val g = gen
        val pl = AudioPlayer().also { it.start(scope) }
        player = pl
        liveReady = false
        val s = LiveSession(
            key, Yesmin.settings.liveModel, Persona.system("call"), Yesmin.settings.voice, Persona.tools,
            object : LiveSession.Callbacks {
                override fun onReady() { scope.launch { if (ok(g)) { liveReady = true; state.value = CallState.Listening; status.value = "Connected - speak now"; startCapture() } } }
                override fun onAudio(pcm: ByteArray) {
                    scope.launch {
                        if (!ok(g) || ignoreAudio) return@launch
                        if (state.value != CallState.Speaking) { state.value = CallState.Speaking; status.value = "Yesmin is speaking" }
                        drainJob?.cancel()
                        player?.enqueue(pcm)
                    }
                }
                override fun onInText(t: String) { scope.launch { if (ok(g)) { frag("You", t); if (state.value == CallState.Listening) status.value = "Hearing you…" } } }
                override fun onOutText(t: String) { scope.launch { if (ok(g)) frag("Yesmin", t) } }
                override fun onInterrupted() { scope.launch { if (ok(g)) { player?.flush(); ignoreAudio = false; liveWho = ""; state.value = CallState.Listening; status.value = "Listening…" } } }
                override fun onTurnComplete() {
                    scope.launch {
                        if (!ok(g)) return@launch
                        ignoreAudio = false; liveWho = ""
                        drainJob?.cancel()
                        drainJob = scope.launch {
                            player?.awaitDrained()
                            if (ok(g)) { state.value = CallState.Listening; status.value = "Listening…" } // auto-return to listening
                        }
                    }
                }
                override fun onToolCall(id: String, name: String, args: JSONObject) {
                    scope.launch {
                        if (!ok(g)) return@launch
                        state.value = CallState.Thinking; status.value = "Working on it…"
                        val r = Yesmin.handleCall(name, args)
                        live?.sendToolResponse(id, name, r)
                    }
                }
                override fun onClosed(reason: String, wasReady: Boolean) {
                    scope.launch {
                        if (!ok(g)) return@launch
                        cleanup()
                        if (!wasReady && Yesmin.settings.autoFallback) {
                            active = true
                            gen++
                            startServiceAndTimer()
                            startFallback("Live unavailable ($reason). Using fallback voice mode.")
                        } else {
                            fail(if (wasReady) "Connection lost: $reason" else "Could not connect: $reason")
                        }
                    }
                }
            }
        )
        live = s
        s.connect()
    }

    private fun ok(g: Int) = active && g == gen

    private fun startCapture() {
        if (capture != null) return
        try {
            val c = AudioCapture { pcm -> live?.sendAudio(pcm) }
            c.muted = muted.value
            c.start(scope)
            capture = c
        } catch (e: Exception) {
            fail(e.message ?: "Microphone error")
        }
    }

    private fun frag(who: String, t: String) {
        if (who != liveWho) {
            liveWho = who; liveBuf.clear()
            subtitles.value = (subtitles.value + Subtitle(who, "")).takeLast(40)
        }
        liveBuf.append(t)
        val l = subtitles.value.toMutableList()
        if (l.isNotEmpty()) l[l.lastIndex] = Subtitle(who, liveBuf.toString())
        subtitles.value = l
    }

    // ───────────── Fallback path ─────────────
    private fun startFallback(note: String) {
        modeLabel.value = "Fallback"
        status.value = note
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            fail("Speech recognition is not available on this phone (install/enable the Google app).")
            return
        }
        stt = Stt(ctx)
        tts = TtsEngine(ctx)
        scope.launch { delay(600); if (active) listen() }
    }

    private fun listen() {
        if (!active || modeLabel.value != "Fallback") return
        if (muted.value) {
            state.value = CallState.Listening; status.value = "Muted"
            val g = gen
            scope.launch { delay(700); if (ok(g)) listen() }
            return
        }
        state.value = CallState.Listening
        status.value = "Listening…"
        stt?.start(
            Yesmin.settings.sttLang,
            onPart = { status.value = "Hearing: $it" },
            onDone = { onUserSaid(it) },
            onErr = { onSttError(it) }
        )
    }

    private fun onSttError(code: Int) {
        if (!active) return
        val g = gen
        when (code) {
            SpeechRecognizer.ERROR_CLIENT -> {}
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail("Microphone permission denied.")
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                status.value = "Silence… still listening"
                scope.launch { delay(300); if (ok(g)) listen() }
            }
            else -> {
                errorStreak++
                if (errorStreak > 4) { fail(Stt.errorText(code) + " - too many failures."); return }
                status.value = Stt.errorText(code) + ", retrying…"
                stt?.destroy(); stt = Stt(ctx)
                scope.launch { delay(1500); if (ok(g)) listen() }
            }
        }
    }

    private fun onUserSaid(text: String) {
        if (!active || state.value == CallState.Thinking) return
        errorStreak = 0
        subtitles.value = (subtitles.value + Subtitle("You", text)).takeLast(40)
        state.value = CallState.Thinking
        status.value = "Thinking…"
        history.add(ChatMsg("user", text))
        val g = gen
        fbJob?.cancel()
        fbJob = scope.launch {
            try {
                val s = Yesmin.settings
                val r = Yesmin.gemini.generate(s.model, Persona.system("call"), history.takeLast(20), s.temperature.toDouble(), tools = Persona.tools)
                if (!ok(g)) return@launch
                val results = r.calls.map { Yesmin.handleCall(it.name, it.args) }
                val reply = r.text.ifBlank { results.joinToString(". ") }.ifBlank { "Hmm, I didn't get that." }
                history.add(ChatMsg("model", reply))
                subtitles.value = (subtitles.value + Subtitle("Yesmin", reply)).takeLast(40)
                state.value = CallState.Speaking
                status.value = "Yesmin is speaking"
                tts?.speak(reply) { scope.launch { if (ok(g)) listen() } } ?: listen()
            } catch (e: GeminiException) {
                if (!ok(g)) return@launch
                errorStreak++
                status.value = e.message ?: "Error"
                if (errorStreak > 3) { fail(e.message ?: "Too many errors"); return@launch }
                delay(1500)
                if (ok(g)) listen()
            }
        }
    }

    // ───────────── controls ─────────────
    fun toggleMute() {
        muted.value = !muted.value
        capture?.muted = muted.value
        if (muted.value && modeLabel.value == "Fallback") stt?.cancel()
    }

    fun toggleSpeaker() {
        speaker.value = !speaker.value
        applyRoute(modeLabel.value == "Live")
    }

    /** Barge-in button: stop Yesmin talking right now and listen. */
    fun interrupt() {
        if (!active) return
        if (modeLabel.value == "Live") {
            player?.flush(); ignoreAudio = true; drainJob?.cancel()
            state.value = CallState.Listening; status.value = "Listening…"
        } else {
            tts?.stop(); fbJob?.cancel(); listen()
        }
    }

    fun stop() {
        if (!active && state.value == CallState.Idle) return
        active = false
        gen++
        cleanup()
        state.value = CallState.Idle
        status.value = "Call ended"
    }

    private fun fail(msg: String) {
        cleanup()
        active = false
        gen++
        state.value = CallState.Error
        status.value = msg
    }

    private fun cleanup() {
        timerJob?.cancel(); timerJob = null
        drainJob?.cancel(); drainJob = null
        fbJob?.cancel(); fbJob = null
        capture?.stop(); capture = null
        live?.close(); live = null
        player?.release(); player = null
        stt?.destroy(); stt = null
        tts?.shutdown(); tts = null
        ignoreAudio = false
        liveReady = false
        restoreRoute()
        try { ctx.stopService(Intent(ctx, CallService::class.java)) } catch (e: Exception) { }
    }

    @Suppress("DEPRECATION")
    private fun applyRoute(inCommunication: Boolean) {
        if (inCommunication) audio.mode = AudioManager.MODE_IN_COMMUNICATION
        val on = speaker.value
        if (Build.VERSION.SDK_INT >= 31) {
            if (on) {
                audio.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let { audio.setCommunicationDevice(it) }
            } else audio.clearCommunicationDevice()
        } else {
            audio.isSpeakerphoneOn = on
        }
    }

    @Suppress("DEPRECATION")
    private fun restoreRoute() {
        try {
            if (Build.VERSION.SDK_INT >= 31) audio.clearCommunicationDevice() else audio.isSpeakerphoneOn = false
            audio.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) { }
    }
}
