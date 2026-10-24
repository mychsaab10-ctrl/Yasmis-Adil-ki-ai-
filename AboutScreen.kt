package com.yesmin.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SubScreen("About Yesmin AI", onBack) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Avatar(110.dp)
            Text("Yesmin AI  v1.0.0", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
            Text("Yesmin ek AI companion hai, insaan nahi. Woh galtiyan kar sakti hai - zaroori cheezein khud check karo.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionTitle("Implemented")
        Text(
            "✅ Gemini chat (model list Google se refresh hoti hai)\n" +
                "✅ Web search (Google grounding) in Chat\n" +
                "✅ Calling mode: Gemini Live API + fallback (recognizer + TTS)\n" +
                "✅ Intents: apps, YouTube, music, browser, maps, camera, settings, alarm, timer, calendar, dial, SMS\n" +
                "✅ Accessibility: read screen, tap, scroll, back/home (opt-in)\n" +
                "✅ Notification reading (opt-in) + summary\n" +
                "✅ Coding mode with copy / save file\n" +
                "✅ Memory (view/edit/delete/clear), summaries\n" +
                "✅ OCR, image Q&A, PDF summary, translate, study, writing, planner, voice notes\n" +
                "✅ Quick-access notification",
            fontSize = 13.sp
        )
        SectionTitle("Not implemented (honest list)")
        Text(
            "❌ Custom wake-word (needs always-on mic + battery)\n" +
                "❌ Floating bubble overlay\n" +
                "❌ Screen capture (MediaProjection)\n" +
                "❌ Reading contacts / auto-calling\n" +
                "❌ Hindi (Devanagari) OCR\n" +
                "⚠ Live API protocol/model names change often - agar Live fail ho to Settings mein model refresh karo; fallback apne aap chalta hai.",
            fontSize = 13.sp
        )
    }
}
