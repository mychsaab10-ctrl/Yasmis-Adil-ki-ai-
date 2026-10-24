package com.yesmin.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** One-shot speech recognition via the phone's recognizer. Must be used on the main thread. */
class Stt(private val ctx: Context) {
    private var rec: SpeechRecognizer? = null
    var running = false
        private set

    fun start(lang: String, onPart: (String) -> Unit, onDone: (String) -> Unit, onErr: (Int) -> Unit) {
        if (running) return // no duplicate sessions
        running = true
        val r = rec ?: SpeechRecognizer.createSpeechRecognizer(ctx).also { rec = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (t.isNotBlank()) onPart(t)
            }

            override fun onResults(results: Bundle?) {
                running = false
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (t.isBlank()) onErr(SpeechRecognizer.ERROR_NO_MATCH) else onDone(t)
            }

            override fun onError(error: Int) {
                running = false
                onErr(error)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        r.startListening(i)
    }

    fun cancel() {
        running = false
        rec?.cancel()
    }

    fun destroy() {
        running = false
        rec?.destroy()
        rec = null
    }

    companion object {
        fun errorText(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech network error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't hear anything"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            else -> "Speech error $code"
        }
    }
}
