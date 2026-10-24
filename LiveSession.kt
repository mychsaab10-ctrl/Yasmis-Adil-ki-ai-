package com.yesmin.ai.ai

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Gemini Live API over WebSocket (BidiGenerateContent).
 * Input: 16 kHz, 16-bit mono PCM. Output: 24 kHz, 16-bit mono PCM.
 * The API key is passed as the documented `key` query parameter and is redacted from all messages.
 */
class LiveSession(
    private val apiKey: String,
    private val model: String,
    private val system: String,
    private val voice: String,
    private val tools: JSONArray,
    private val cb: Callbacks
) {
    interface Callbacks {
        fun onReady()
        fun onAudio(pcm: ByteArray)
        fun onInText(t: String)
        fun onOutText(t: String)
        fun onInterrupted()
        fun onTurnComplete()
        fun onToolCall(id: String, name: String, args: JSONObject)
        fun onClosed(reason: String, wasReady: Boolean)
    }

    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private var ws: WebSocket? = null
    @Volatile private var ready = false
    @Volatile private var closing = false
    @Volatile private var notified = false

    private fun redact(s: String?): String = (s ?: "unknown error").replace(apiKey, "***")

    fun connect() {
        val url = "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"
        ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(setupJson())
            }

            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.utf8())

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                finish("closed ($code) ${redact(reason)}")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                finish("closed ($code) ${redact(reason)}")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val code = response?.code
                finish(if (code != null) "HTTP $code - check API key / model access" else "network error: ${redact(t.message)}")
            }
        })
    }

    private fun finish(reason: String) {
        if (closing || notified) return
        notified = true
        cb.onClosed(reason, ready)
    }

    private fun setupJson(): String {
        val gen = JSONObject()
            .put("responseModalities", JSONArray().put("AUDIO"))
            .put(
                "speechConfig", JSONObject().put(
                    "voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))
                )
            )
        val setup = JSONObject()
            .put("model", "models/${model.removePrefix("models/")}")
            .put("generationConfig", gen)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("tools", tools)
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
        return JSONObject().put("setup", setup).toString()
    }

    private fun handle(raw: String) {
        val j = try { JSONObject(raw) } catch (e: Exception) { return }
        if (j.has("setupComplete")) {
            ready = true
            cb.onReady()
            return
        }
        j.optJSONObject("serverContent")?.let { sc ->
            sc.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    val d = parts.getJSONObject(i).optJSONObject("inlineData")?.optString("data")
                    if (!d.isNullOrEmpty()) cb.onAudio(Base64.decode(d, Base64.DEFAULT))
                }
            }
            sc.optJSONObject("inputTranscription")?.optString("text")?.let { if (it.isNotEmpty()) cb.onInText(it) }
            sc.optJSONObject("outputTranscription")?.optString("text")?.let { if (it.isNotEmpty()) cb.onOutText(it) }
            if (sc.optBoolean("interrupted")) cb.onInterrupted()
            if (sc.optBoolean("turnComplete")) cb.onTurnComplete()
        }
        j.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { calls ->
            for (i in 0 until calls.length()) {
                val c = calls.getJSONObject(i)
                cb.onToolCall(c.optString("id"), c.optString("name"), c.optJSONObject("args") ?: JSONObject())
            }
        }
    }

    fun sendAudio(pcm: ByteArray) {
        if (!ready) return
        val msg = JSONObject().put(
            "realtimeInput", JSONObject().put(
                "audio", JSONObject()
                    .put("data", Base64.encodeToString(pcm, Base64.NO_WRAP))
                    .put("mimeType", "audio/pcm;rate=16000")
            )
        )
        ws?.send(msg.toString())
    }

    fun sendToolResponse(id: String, name: String, result: String) {
        val fr = JSONObject().put("id", id).put("name", name).put("response", JSONObject().put("result", result))
        val msg = JSONObject().put("toolResponse", JSONObject().put("functionResponses", JSONArray().put(fr)))
        ws?.send(msg.toString())
    }

    fun close() {
        closing = true
        try { ws?.close(1000, "bye") } catch (e: Exception) { }
        ws = null
        http.dispatcher.executorService.shutdown()
    }
}
