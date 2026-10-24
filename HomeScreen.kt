package com.yesmin.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.yesmin.ai.Yesmin
import com.yesmin.ai.go
import java.time.LocalTime

@Composable
fun HomeScreen(nav: NavHostController) {
    val hour = LocalTime.now().hour
    val hi = when {
        hour < 5 -> "Itni raat ko jaag rahe ho? 🌙"
        hour < 12 -> "Good morning ☀️"
        hour < 17 -> "Namaste 🌸"
        hour < 21 -> "Good evening 🌆"
        else -> "Shubh ratri 🌙"
    }
    val hasKey = Yesmin.keys.has()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Avatar(150.dp)
        Text("Yesmin AI", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(hi, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(if (hasKey) "Gemini key saved" else "No API key", hasKey)
            Chip("AI companion", null)
        }
        if (!hasKey) {
            GlowCard(Modifier.fillMaxWidth(), onClick = { nav.go("settings") }) {
                Text("Pehle Settings mein apni Gemini API key daalo (free key: aistudio.google.com).", color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Button(onClick = { nav.go("call") }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("📞  Start Yesmin Call", fontSize = 17.sp)
        }
        val cards = listOf(
            Triple("chat", "💬 AI Chat", "Baat karo, search karo"),
            Triple("coding", "💻 Coding", "Code likho, debug karo"),
            Triple("automation", "🤖 Phone Automation", "Apps, alarms, screen"),
            Triple("memory", "🧠 Memory", "Yaadein dekho/edit karo"),
            Triple("tools", "🧰 Tools & Docs", "OCR, PDF, translate"),
            Triple("permissions", "🔐 Permissions", "Privacy control"),
            Triple("settings", "⚙️ Settings", "Key, model, persona"),
            Triple("about", "ℹ️ About", "Kya kaam karta hai")
        )
        cards.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (r, t, s) ->
                    GlowCard(Modifier.weight(1f), onClick = { nav.go(r) }) {
                        Text(t, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(s, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
