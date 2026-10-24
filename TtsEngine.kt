package com.yesmin.ai.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.yesmin.ai.Yesmin
import java.util.Locale
import java.util.UUID

class TtsEngine(ctx: Context) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) tts?.language = Locale.forLanguageTag(Yesmin.settings.ttsLang)
        }
    }

    val available: Boolean get() = ready

    fun speak(text: String, finished: () -> Unit = {}) {
        val t = tts
        if (!ready || t == null) { finished(); return }
        t.language = Locale.forLanguageTag(Yesmin.settings.ttsLang)
        val id = UUID.randomUUID().toString()
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { finished() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { finished() }
        })
        t.speak(clean(text), TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop(); tts?.shutdown(); tts = null; ready = false
    }

    private fun clean(s: String) = s
        .replace(Regex("```[\\s\\S]*?```"), " code block ")
        .replace(Regex("[*_#`~>|]"), "")
        .replace(Regex("https?://\\S+"), "link")
}
