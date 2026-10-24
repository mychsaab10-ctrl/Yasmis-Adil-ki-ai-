package com.yesmin.ai.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.yesmin.ai.Yesmin
import com.yesmin.ai.ai.ChatMsg
import com.yesmin.ai.ai.GeminiException
import com.yesmin.ai.automation.Actions
import com.yesmin.ai.automation.ScreenControl
import com.yesmin.ai.automation.YesminAccessibilityService
import com.yesmin.ai.automation.YesminNotificationListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun AutomationScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val log by Yesmin.log.collectAsState()
    val gate by Yesmin.accessGate.collectAsState()
    val notifs by YesminNotificationListener.recent.collectAsState()
    var cmd by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var hour by remember { mutableStateOf("") }
    var minute by remember { mutableStateOf("") }
    var timerMin by remember { mutableStateOf("") }
    var clickText by remember { mutableStateOf("") }
    val a11yOn = YesminAccessibilityService.isEnabled()
    val notifOn = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun act(vararg kv: Pair<String, Any>) {
        val o = JSONObject(); kv.forEach { o.put(it.first, it.second) }
        Yesmin.log.value = Yesmin.handleCall("phone_action", o)
    }
    fun screen(a: String, text: String = "") {
        Yesmin.log.value = Yesmin.handleCall("screen_action", JSONObject().put("action", a).put("text", text))
    }
    fun openSettings(action: String) = ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    SubScreen("Phone Automation", onBack) {
        Text(
            "Har action Android ke official intents se hota hai. 'Launched' ka matlab sirf itna hai ki Android ne request accept ki - aage kya hua, Yesmin nahi dekh sakti. Call/SMS mein final button tum dabaoge.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
        )
        SectionTitle("Command (Hindi / Hinglish / English)")
        OutlinedTextField(cmd, { cmd = it }, Modifier.fillMaxWidth(), label = { Text("jaise: YouTube pe Arijit Singh songs search karo") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = cmd.isNotBlank() && !busy,
                onClick = { busy = true; scope.launch { Yesmin.log.value = Yesmin.command(cmd); busy = false } }
            ) { Text(if (busy) "Working…" else "Run") }
            DictateButton { cmd = it }
        }

        SectionTitle("Quick actions")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { act("action" to "open_app", "query" to "YouTube") }) { Text("YouTube") }
            OutlinedButton(onClick = { act("action" to "open_url", "query" to "google.com") }) { Text("Browser") }
            OutlinedButton(onClick = { act("action" to "open_camera") }) { Text("Camera") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { act("action" to "open_settings", "query" to "") }) { Text("Settings") }
            OutlinedButton(onClick = { act("action" to "maps_search", "query" to "restaurants near me") }) { Text("Maps") }
        }

        SectionTitle("Alarm / Timer")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(hour, { hour = it.take(2) }, Modifier.weight(1f), label = { Text("Hour 0-23") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(minute, { minute = it.take(2) }, Modifier.weight(1f), label = { Text("Min") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Button(enabled = hour.toIntOrNull() in 0..23 && minute.toIntOrNull() in 0..59,
                onClick = { act("action" to "set_alarm", "hour" to hour.toInt(), "minute" to minute.toInt(), "label" to "Yesmin alarm") }) { Text("Alarm") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(timerMin, { timerMin = it.take(3) }, Modifier.weight(1f), label = { Text("Timer (minutes)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Button(enabled = (timerMin.toIntOrNull() ?: 0) > 0,
                onClick = { act("action" to "set_timer", "seconds" to timerMin.toInt() * 60) }) { Text("Timer") }
        }

        SectionTitle("Result")
        Text(log.ifBlank { "—" }, color = MaterialTheme.colorScheme.onSurface)

        SectionTitle("Screen control (Accessibility) - optional")
        Text(
            "Service: ${if (a11yOn) "ON ✅" else "OFF"}. Yesmin sirf tab screen padhti/chalati hai jab tum yahan se kehte ho, aur neeche wala switch ON ho. Tap karne se pehle poochti hai.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = { openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) { Text("Open Accessibility settings") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = gate, onCheckedChange = { Yesmin.accessGate.value = it })
            Text("  Allow Yesmin to control the screen (this session)")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = gate && a11yOn, onClick = { screen("back") }) { Text("Back") }
            OutlinedButton(enabled = gate && a11yOn, onClick = { screen("home") }) { Text("Home") }
            OutlinedButton(enabled = gate && a11yOn, onClick = { screen("scroll_down") }) { Text("Scroll ↓") }
            OutlinedButton(enabled = gate && a11yOn, onClick = { screen("scroll_up") }) { Text("↑") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(clickText, { clickText = it }, Modifier.weight(1f), label = { Text("Tap element with text") }, singleLine = true)
            Button(enabled = gate && a11yOn && clickText.isNotBlank(), onClick = { screen("click", clickText) }) { Text("Tap") }
        }
        Button(
            enabled = gate && a11yOn && !busy,
            onClick = {
                busy = true
                Yesmin.log.value = "Ab 5 second mein kisi doosre app par jao… phir screen padhi jayegi."
                scope.launch {
                    delay(5000)
                    val txt = ScreenControl.run(JSONObject().put("action", "read_screen"))
                    Yesmin.log.value = "Screen text:\n$txt"
                    busy = false
                }
            }
        ) { Text("Read screen (5 sec delay)") }
        Button(
            enabled = gate && a11yOn && !busy,
            onClick = {
                busy = true
                Yesmin.log.value = "Ab 5 second mein doosre app par jao… phir Yesmin screen explain karegi."
                scope.launch {
                    delay(5000)
                    val txt = ScreenControl.run(JSONObject().put("action", "read_screen"))
                    Yesmin.log.value = try {
                        Yesmin.gemini.generate(
                            Yesmin.settings.model, null,
                            listOf(ChatMsg("user", "Explain in simple Hinglish what is on this phone screen and what the user can do:\n\n$txt")), 0.3
                        ).text
                    } catch (e: GeminiException) { e.message ?: "Error" }
                    busy = false
                }
            }
        ) { Text("Explain screen (5 sec delay)") }

        SectionTitle("Notifications")
        Text(
            "Notification access: ${if (notifOn) "ON ✅" else "OFF"}. Sirf memory mein dikhte hain, kahin save/upload nahi hote (jab tak tum summarize na karo).",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = { openSettings("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS") }) { Text("Open Notification access settings") }
        if (notifOn && notifs.isNotEmpty()) {
            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val txt = notifs.take(15).joinToString("\n") { "- ${it.pkg}: ${it.title} - ${it.text}" }
                    Yesmin.log.value = try {
                        Yesmin.gemini.generate(
                            Yesmin.settings.model, null,
                            listOf(ChatMsg("user", "Summarize these phone notifications in simple Hinglish, most important first:\n$txt")), 0.3
                        ).text
                    } catch (e: GeminiException) { e.message ?: "Error" }
                    busy = false
                }
            }) { Text("Summarize ${notifs.size} notifications") }
            notifs.take(10).forEach {
                Text("• ${it.title}: ${it.text}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
