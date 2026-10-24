@file:OptIn(ExperimentalMaterial3Api::class)

package com.yesmin.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yesmin.ai.Yesmin
import com.yesmin.ai.ai.GeminiException
import com.yesmin.ai.automation.QuickAccess
import kotlinx.coroutines.launch

@Composable
private fun ModelField(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value, onChange, Modifier.weight(1f), label = { Text(label) }, singleLine = true,
            trailingIcon = { if (options.isNotEmpty()) TextButton(onClick = { open = true }) { Text("▼") } }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onChange(o); open = false }) }
        }
    }
}

@Composable
fun SettingsScreen() {
    val s = Yesmin.settings
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var hasKey by remember { mutableStateOf(Yesmin.keys.has()) }
    var editing by remember { mutableStateOf(!Yesmin.keys.has()) }
    var keyInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(if (Yesmin.keys.has()) "Key saved (not tested yet)" else "No key") }
    var statusOk by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }

    var model by remember { mutableStateOf(s.model) }
    var liveModel by remember { mutableStateOf(s.liveModel) }
    var voice by remember { mutableStateOf(s.voice) }
    var temp by remember { mutableStateOf(s.temperature) }
    var pName by remember { mutableStateOf(s.personaName) }
    var pStyle by remember { mutableStateOf(s.personaStyle) }
    var lang by remember { mutableStateOf(s.language) }
    var theme by remember { mutableStateOf(s.theme) }
    var useLive by remember { mutableStateOf(s.useLive) }
    var autoFb by remember { mutableStateOf(s.autoFallback) }
    var autoSpeak by remember { mutableStateOf(s.autoSpeak) }
    var quick by remember { mutableStateOf(s.quickAccess) }
    var sttLang by remember { mutableStateOf(s.sttLang) }
    var ttsLang by remember { mutableStateOf(s.ttsLang) }
    var models by remember { mutableStateOf(s.modelsCache.split(",").filter { it.isNotBlank() }) }
    var liveModels by remember { mutableStateOf(s.liveCache.split(",").filter { it.isNotBlank() }) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settings", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)

        SectionTitle("Gemini API key")
        if (hasKey && !editing) {
            Text("Saved: ${Yesmin.keys.masked()}", color = MaterialTheme.colorScheme.onSurface)
        } else {
            OutlinedTextField(
                keyInput, { keyInput = it }, Modifier.fillMaxWidth(), label = { Text("API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (editing) {
                Button(enabled = keyInput.isNotBlank(), onClick = {
                    Yesmin.keys.save(keyInput); keyInput = ""; hasKey = true; editing = false
                    status = "Key saved (not tested yet)"; statusOk = null
                }) { Text("Save") }
            } else {
                OutlinedButton(onClick = { editing = true }) { Text("Edit") }
            }
            OutlinedButton(enabled = hasKey, onClick = {
                Yesmin.keys.clear(); hasKey = false; editing = true; status = "Key removed"; statusOk = false
            }) { Text("Remove") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = hasKey && !busy, onClick = {
                busy = true; status = "Testing…"; statusOk = null
                scope.launch {
                    try {
                        val r = Yesmin.gemini.test(s.model)
                        status = "Connected ✓ (${s.model} replied: ${r.take(20)})"; statusOk = true
                    } catch (e: GeminiException) {
                        status = e.message ?: "Error"; statusOk = false
                    }
                    busy = false
                }
            }) { Text("Test connection") }
            Chip(status, statusOk)
        }
        Text(
            "Key Android Keystore (AES-256-GCM) se encrypt hokar is phone par save hoti hai, source code ya logs mein nahi. " +
                "Dhyan: APK ke andar key daalna unsafe hai - isliye key sirf yahan daalo. Apni key kisi ko mat do; Google AI Studio mein usage limit lagao.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionTitle("Models")
        ModelField("Chat model", model, models) { model = it; s.model = it }
        ModelField("Live (voice) model", liveModel, liveModels) { liveModel = it; s.liveModel = it }
        OutlinedButton(enabled = hasKey && !busy, onClick = {
            busy = true; status = "Loading model list…"; statusOk = null
            scope.launch {
                try {
                    val (t, l) = Yesmin.gemini.listModels()
                    models = t; liveModels = l
                    s.modelsCache = t.joinToString(","); s.liveCache = l.joinToString(",")
                    status = "Found ${t.size} chat and ${l.size} live models"; statusOk = true
                } catch (e: GeminiException) {
                    status = e.message ?: "Error"; statusOk = false
                }
                busy = false
            }
        }) { Text("Refresh list from Google") }
        Text("Model names badalte rehte hain - list se wahi chuno jo tumhari key support karti hai.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Creativity (temperature): ${"%.1f".format(temp)}")
        Slider(value = temp, onValueChange = { temp = it; s.temperature = it }, valueRange = 0f..2f)

        SectionTitle("Yesmin's personality")
        OutlinedTextField(pName, { pName = it; s.personaName = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
        OutlinedTextField(pStyle, { pStyle = it; s.personaStyle = it }, Modifier.fillMaxWidth(), label = { Text("Personality description") }, minLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Hinglish", "Hindi", "English").forEach { l ->
                FilterChip(selected = lang == l, onClick = { lang = l; s.language = l }, label = { Text(l) })
            }
        }
        ModelField("Voice (Live)", voice, listOf("Aoede", "Kore", "Leda", "Zephyr", "Puck", "Charon", "Fenrir", "Orus")) { voice = it; s.voice = it }

        SectionTitle("Voice call")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(useLive, { useLive = it; s.useLive = it }); Text("  Use Gemini Live (natural voice)")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(autoFb, { autoFb = it; s.autoFallback = it }); Text("  Auto fallback if Live fails")
        }
        Text("Fallback voice languages (phone recognizer / TTS)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("hi-IN", "en-IN", "en-US").forEach { l ->
                FilterChip(selected = sttLang == l, onClick = { sttLang = l; s.sttLang = l }, label = { Text("hear $l") })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("hi-IN", "en-IN", "en-US").forEach { l ->
                FilterChip(selected = ttsLang == l, onClick = { ttsLang = l; s.ttsLang = l }, label = { Text("speak $l") })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(autoSpeak, { autoSpeak = it; s.autoSpeak = it }); Text("  Speak chat replies automatically")
        }

        SectionTitle("Appearance & shortcuts")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("dark", "light", "system").forEach { t ->
                FilterChip(selected = theme == t, onClick = { theme = t; s.theme = t }, label = { Text(t) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(quick, { quick = it; s.quickAccess = it; if (it) QuickAccess.show(ctx) else QuickAccess.hide(ctx) })
            Text("  Quick-access notification")
        }
    }
}
