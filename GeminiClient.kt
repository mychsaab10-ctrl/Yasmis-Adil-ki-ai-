package com.yesmin.ai.ai

import com.yesmin.ai.data.SecureKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiException(message: String) : Exception(message)

data class ChatMsg(val role: String, val text: String) // role: "user" | "model"
data class Attach(val mime: String, val b64: String)
data class FnCall(val name: String, val args: JSONObject)
data class GenResult(val text: String, val calls: List<FnCall>, val sources: List<String>)

/**
 * Minimal REST client for the Gemini Developer API (generateContent / models.list).
 * The key is sent in the x-goog-api-key header, never in the URL and never logged.
 */
class GeminiClient(private val keys: SecureKeyStore) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val base = "https://generativelanguage.googleapis.com/v1beta"
    private val json = "application/json".toMediaType()

    private fun mapError(code: Int, body: String): GeminiException {
        val msg = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (e: JSONException) {
            ""
        }
        val text = when {
            code == 400 && (msg.contains("API key", true) || body.contains("API_KEY_INVALID")) ->
                "API key is invalid. Check it in Settings."
            code == 400 -> "Bad request: ${msg.take(200)}"
            code == 401 || code == 403 -> "Access denied (key invalid, restricted, or API not enabled): ${msg.take(160)}"
            code == 404 -> "Model not found. Choose another model in Settings (tap Refresh list)."
            code == 429 -> "Rate limit or quota exceeded. Wait a bit or check your Gemini plan."
            code >= 500 -> "Gemini server is busy (HTTP $code). Try again."
            else -> "HTTP $code: ${msg.take(160)}"
        }
        return GeminiException(text)
    }

    private fun send(req: Request): JSONObject {
        return try {
            http.newCall(req).execute().use { r ->
                val s = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw mapError(r.code, s)
                JSONObject(s)
            }
        } catch (e: GeminiException) {
            throw e
        } catch (e: IOException) {
            throw GeminiException("Network error. Check your internet connection.")
        } catch (e: JSONException) {
            throw GeminiException("Unexpected response from Gemini.")
        }
    }

    private fun requireKey(): String =
        keys.load() ?: throw GeminiException("No API key saved. Add one in Settings.")

    private fun clean(model: String) = model.removePrefix("models/").trim()

    suspend fun generate(
        model: String,
        system: String?,
        history: List<ChatMsg>,
        temperature: Double,
        attachments: List<Attach> = emptyList(),
        tools: JSONArray? = null
    ): GenResult = withContext(Dispatchers.IO) {
        val key = requireKey()
        val contents = JSONArray()
        history.forEachIndexed { i, m ->
            val parts = JSONArray().put(JSONObject().put("text", m.text))
            if (i == history.lastIndex && m.role == "user") {
                attachments.forEach { a ->
                    parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", a.mime).put("data", a.b64)))
                }
            }
            contents.put(JSONObject().put("role", m.role).put("parts", parts))
        }
        val body = JSONObject()
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", temperature))
        if (!system.isNullOrBlank()) {
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        }
        if (tools != null) body.put("tools", tools)

        val req = Request.Builder()
            .url("$base/models/${clean(model)}:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody(json))
            .build()
        try {
            parse(send(req))
        } catch (e: JSONException) {
            throw GeminiException("Unexpected response format from Gemini.")
        }
    }

    private fun parse(j: JSONObject): GenResult {
        val cands = j.optJSONArray("candidates")
        if (cands == null || cands.length() == 0) {
            val reason = j.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw GeminiException(if (reason.isNotEmpty()) "Blocked by safety filter ($reason)." else "Empty response from Gemini.")
        }
        val cand = cands.getJSONObject(0)
        val sb = StringBuilder()
        val calls = ArrayList<FnCall>()
        val parts = cand.optJSONObject("content")?.optJSONArray("parts")
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val p = parts.getJSONObject(i)
                if (p.has("text") && !p.optBoolean("thought")) sb.append(p.optString("text"))
                p.optJSONObject("functionCall")?.let {
                    calls.add(FnCall(it.optString("name"), it.optJSONObject("args") ?: JSONObject()))
                }
            }
        }
        val sources = ArrayList<String>()
        cand.optJSONObject("groundingMetadata")?.optJSONArray("groundingChunks")?.let { ch ->
            for (i in 0 until ch.length()) {
                val w = ch.getJSONObject(i).optJSONObject("web") ?: continue
                val t = w.optString("title")
                val u = w.optString("uri")
                if (u.isNotEmpty()) sources.add(if (t.isNotEmpty()) "$t - $u" else u)
            }
        }
        if (sb.isEmpty() && calls.isEmpty()) {
            val fr = cand.optString("finishReason")
            if (fr == "SAFETY") throw GeminiException("Response blocked by safety filter.")
        }
        return GenResult(sb.toString().trim(), calls, sources.distinct().take(5))
    }

    /** Returns (models supporting generateContent, models supporting bidiGenerateContent / Live). */
    suspend fun listModels(): Pair<List<String>, List<String>> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$base/models?pageSize=200")
            .header("x-goog-api-key", requireKey())
            .get().build()
        val arr = send(req).optJSONArray("models") ?: JSONArray()
        val text = ArrayList<String>()
        val live = ArrayList<String>()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val name = m.optString("name").removePrefix("models/")
            val methods = m.optJSONArray("supportedGenerationMethods") ?: JSONArray()
            val list = (0 until methods.length()).map { methods.getString(it) }
            if ("generateContent" in list && name.startsWith("gemini")) text.add(name)
            if ("bidiGenerateContent" in list) live.add(name)
        }
        Pair(text.sorted(), live.sorted())
    }

    suspend fun test(model: String): String {
        val r = generate(model, null, listOf(ChatMsg("user", "Reply with exactly: OK")), 0.0)
        return r.text.ifBlank { "OK" }
    }
}
