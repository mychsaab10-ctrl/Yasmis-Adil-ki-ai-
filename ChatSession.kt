package com.yesmin.ai.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yesmin.ai.Yesmin
import com.yesmin.ai.ai.ChatMsg
import com.yesmin.ai.ai.GeminiException
import com.yesmin.ai.ai.Persona
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** role: "user" | "model" | "system" (system = tool results / notices) */
class UiMsg(val role: String, val text: String)

class ChatSession(val kind: String) {
    val messages = mutableStateListOf<UiMsg>()
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var searchMode by mutableStateOf(false)
    private var job: Job? = null

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        error = null
        messages.add(UiMsg("user", t))
        busy = true
        job = Yesmin.scope.launch {
            try {
                val s = Yesmin.settings
                val history = messages.filter { it.role != "system" }.takeLast(30).map { ChatMsg(it.role, it.text) }
                val coding = kind == "coding"
                val tools = when {
                    coding -> null
                    searchMode -> Persona.searchTools
                    else -> Persona.tools
                }
                val temp = if (coding) 0.3 else s.temperature.toDouble()
                val r = Yesmin.gemini.generate(s.model, Persona.system(kind), history, temp, tools = tools)
                val sb = StringBuilder(r.text)
                if (r.sources.isNotEmpty()) sb.append("\n\nSources:\n").append(r.sources.joinToString("\n") { "• $it" })
                if (sb.isNotBlank()) messages.add(UiMsg("model", sb.toString()))
                r.calls.forEach { c ->
                    messages.add(UiMsg("system", "⚙ " + Yesmin.handleCall(c.name, c.args)))
                }
                if (s.autoSpeak && r.text.isNotBlank()) Yesmin.tts.speak(r.text)
            } catch (e: GeminiException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    fun stop() {
        job?.cancel(); busy = false
    }

    fun clear() {
        stop(); messages.clear(); error = null
    }

    /** Asks Gemini for a short summary and (after user approval) saves it as a memory. */
    fun saveSummary() {
        if (messages.none { it.role == "user" } || busy) return
        busy = true
        Yesmin.scope.launch {
            try {
                val s = Yesmin.settings
                val convo = messages.filter { it.role != "system" }.takeLast(30)
                    .joinToString("\n") { (if (it.role == "user") "User: " else "Yesmin: ") + it.text }
                val r = Yesmin.gemini.generate(
                    s.model, null,
                    listOf(ChatMsg("user", "Summarize the useful, lasting points of this conversation in 2-3 short sentences. Leave out passwords, OTPs and ID numbers.\n\n$convo")),
                    0.2
                )
                if (r.text.isNotBlank()) Yesmin.proposeMemory("summary", r.text)
            } catch (e: GeminiException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }
}
