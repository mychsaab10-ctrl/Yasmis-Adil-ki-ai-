package com.yesmin.ai

import android.app.Application
import android.widget.Toast
import com.yesmin.ai.ai.ChatMsg
import com.yesmin.ai.ai.GeminiClient
import com.yesmin.ai.ai.GeminiException
import com.yesmin.ai.ai.Persona
import com.yesmin.ai.automation.Actions
import com.yesmin.ai.automation.ScreenControl
import com.yesmin.ai.data.AppSettings
import com.yesmin.ai.data.MemoryRepo
import com.yesmin.ai.data.SecureKeyStore
import com.yesmin.ai.ui.ChatSession
import com.yesmin.ai.voice.CallController
import com.yesmin.ai.voice.TtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/** A yes/no question shown to the user before anything sensitive happens. */
data class Pending(val title: String, val detail: String, val onConfirm: () -> Unit)

/** Tiny service locator (keeps the project easy to read for beginners). */
object Yesmin {
    lateinit var app: Application; private set
    lateinit var keys: SecureKeyStore; private set
    lateinit var settings: AppSettings; private set
    lateinit var gemini: GeminiClient; private set
    lateinit var memory: MemoryRepo; private set
    lateinit var chat: ChatSession; private set
    lateinit var coding: ChatSession; private set
    lateinit var call: CallController; private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val pending = MutableStateFlow<List<Pending>>(emptyList())
    /** In-app consent switch for screen control (resets on every app start). */
    val accessGate = MutableStateFlow(false)
    /** Log shown on the Phone Automation screen. */
    val log = MutableStateFlow("")

    val tts: TtsEngine by lazy { TtsEngine(app) }

    fun init(a: Application) {
        app = a
        keys = SecureKeyStore(a)
        settings = AppSettings(a)
        gemini = GeminiClient(keys)
        memory = MemoryRepo(a, scope)
        chat = ChatSession("chat")
        coding = ChatSession("coding")
        call = CallController(a, scope)
    }

    fun ask(p: Pending) {
        pending.value = pending.value + p
    }

    fun resolve(p: Pending, accepted: Boolean) {
        pending.value = pending.value - p
        if (accepted) p.onConfirm()
    }

    fun toast(msg: String) = Toast.makeText(app, msg, Toast.LENGTH_LONG).show()

    /** Saves to memory, asking first. Extra warning for sensitive-looking text. */
    fun proposeMemory(kind: String, text: String) {
        val sensitive = MemoryRepo.looksSensitive(text)
        ask(
            Pending(
                if (sensitive) "⚠ This looks sensitive. Save anyway?" else "Save to memory?",
                text
            ) { memory.add(kind, text); toast("Saved to memory") }
        )
    }

    /** Executes a function call coming from Gemini (text chat or Live). Returns a result string for the model. */
    fun handleCall(name: String, args: JSONObject): String = when (name) {
        "remember" -> {
            val fact = args.optString("fact")
            if (fact.isBlank()) "Nothing to remember." else {
                proposeMemory("fact", fact)
                "The user was asked whether to save this memory. It is NOT saved yet."
            }
        }
        "phone_action" -> {
            val act = args.optString("action")
            if (Actions.needsConfirm(act)) {
                ask(Pending("Confirm: $act", Actions.describe(args)) {
                    val r = Actions.run(app, args)
                    log.value = r
                    toast(r)
                })
                "Waiting for the user to confirm on screen. Not done yet."
            } else {
                val r = Actions.run(app, args)
                log.value = r
                r
            }
        }
        "screen_action" -> ScreenControl.run(args).also { log.value = it }
        else -> "Unknown tool: $name"
    }

    /** Used by the Phone Automation screen: natural-language command -> tool calls. */
    suspend fun command(text: String): String {
        val s = settings
        return try {
            val r = gemini.generate(
                s.model, Persona.system("chat"), listOf(ChatMsg("user", text)),
                0.2, tools = Persona.tools
            )
            if (r.calls.isEmpty()) {
                r.text.ifBlank { "I did not find an action to run." }
            } else {
                r.calls.joinToString("\n") { handleCall(it.name, it.args) }
            }
        } catch (e: GeminiException) {
            e.message ?: "Error"
        }
    }
}
