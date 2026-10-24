package com.yesmin.ai.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

private class StrPref(val p: SharedPreferences, val k: String, val d: String) : ReadWriteProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): String = p.getString(k, d) ?: d
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        p.edit().putString(k, value.trim()).apply()
    }
}

private class BoolPref(val p: SharedPreferences, val k: String, val d: Boolean) : ReadWriteProperty<Any?, Boolean> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = p.getBoolean(k, d)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        p.edit().putBoolean(k, value).apply()
    }
}

private class FloatPref(val p: SharedPreferences, val k: String, val d: Float) : ReadWriteProperty<Any?, Float> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Float = p.getFloat(k, d)
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
        p.edit().putFloat(k, value).apply()
    }
}

/** Non-secret settings. The API key is NOT here - see SecureKeyStore. */
class AppSettings(ctx: Context) {
    private val p = ctx.getSharedPreferences("yesmin_settings", Context.MODE_PRIVATE)

    // Models are configurable. Defaults were checked against Google's docs (Oct 2026);
    // use "Refresh list" in Settings to pull the models your key can really use.
    var model by StrPref(p, "model", "gemini-3.1-flash-lite")
    var liveModel by StrPref(p, "liveModel", "gemini-3.8-live")
    var voice by StrPref(p, "voice", "Aoede")
    var temperature by FloatPref(p, "temperature", 0.9f)

    var personaName by StrPref(p, "personaName", "Yesmin")
    var personaStyle by StrPref(
        p, "personaStyle",
        "warm, caring, playful, a little teasing, emotionally supportive, curious and smart"
    )
    var language by StrPref(p, "language", "Hinglish")
    var sttLang by StrPref(p, "sttLang", "hi-IN")
    var ttsLang by StrPref(p, "ttsLang", "en-IN")

    var useLive by BoolPref(p, "useLive", true)
    var autoFallback by BoolPref(p, "autoFallback", true)
    var autoSpeak by BoolPref(p, "autoSpeak", false)
    var quickAccess by BoolPref(p, "quickAccess", false)

    var modelsCache by StrPref(p, "modelsCache", "")
    var liveCache by StrPref(p, "liveCache", "")

    val themeFlow = MutableStateFlow(p.getString("theme", "dark") ?: "dark")
    var theme: String
        get() = themeFlow.value
        set(v) {
            themeFlow.value = v
            p.edit().putString("theme", v).apply()
        }
}
