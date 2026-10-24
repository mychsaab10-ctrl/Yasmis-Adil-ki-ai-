@file:OptIn(ExperimentalMaterial3Api::class)

package com.yesmin.ai.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yesmin.ai.Yesmin

private sealed class Seg {
    class Txt(val s: String) : Seg()
    class Code(val lang: String, val s: String) : Seg()
}

private fun split(text: String): List<Seg> {
    val out = ArrayList<Seg>()
    val re = Regex("```([A-Za-z0-9_+#.-]*)\\n?([\\s\\S]*?)```")
    var last = 0
    for (m in re.findAll(text)) {
        if (m.range.first > last) out.add(Seg.Txt(text.substring(last, m.range.first)))
        out.add(Seg.Code(m.groupValues[1], m.groupValues[2].trimEnd()))
        last = m.range.last + 1
    }
    if (last < text.length) out.add(Seg.Txt(text.substring(last)))
    return out
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    val ext = when (lang.lowercase()) {
        "kotlin", "kt" -> "kt"; "java" -> "java"; "python", "py" -> "py"; "javascript", "js" -> "js"
        "html" -> "html"; "css" -> "css"; "xml" -> "xml"; "bash", "sh" -> "sh"; else -> "txt"
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(code.toByteArray()) }
                Toast.makeText(ctx, "File saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Could not save: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF0E0A1C))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(lang.ifBlank { "code" }, color = Cyan, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { clip.setText(AnnotatedString(code)); Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show() }) { Text("Copy") }
            TextButton(onClick = { saver.launch("code.$ext") }) { Text("Save…") }
        }
        Box(Modifier.horizontalScroll(rememberScrollState()).padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
            SelectionContainer { Text(code, color = Color(0xFFE6E0FF), fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun Bubble(role: String, text: String) {
    val mine = role == "user"
    val sys = role == "system"
    val bg = when {
        mine -> Cyan.copy(alpha = 0.18f)
        sys -> Pink.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.fillMaxWidth(if (sys) 1f else 0.92f).clip(RoundedCornerShape(16.dp)).background(bg).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            split(text).forEach { seg ->
                when (seg) {
                    is Seg.Txt -> SelectionContainer { Text(seg.s.trim(), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp) }
                    is Seg.Code -> CodeBlock(seg.lang, seg.s)
                }
            }
            if (role == "model") {
                Row {
                    TextButton(onClick = { Yesmin.tts.speak(text) }) { Text("🔊 Speak", fontSize = 12.sp) }
                    TextButton(onClick = { Yesmin.tts.stop() }) { Text("⏹", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(session: ChatSession, title: String, coding: Boolean) {
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(session.messages.size, session.busy) {
        if (session.messages.isNotEmpty()) list.animateScrollToItem(session.messages.size - 1)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            if (!coding) TextButton(onClick = { session.saveSummary() }) { Text("🧠 Save summary", fontSize = 12.sp) }
            TextButton(onClick = { session.clear() }) { Text("Clear", fontSize = 12.sp) }
        }
        if (!coding) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = session.searchMode, onClick = { session.searchMode = !session.searchMode },
                    label = { Text("🌐 Web search (Google grounding)") }
                )
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Explain this code simply:\n" to "Explain",
                    "Debug this error and give the fixed file:\n" to "Debug",
                    "Create a complete Android Kotlin app: " to "Android app",
                    "Create a website (HTML, CSS, JS) for: " to "Website",
                    "How do I build an APK in Termux for this project?" to "Termux build"
                ).forEach { (p, l) -> AssistChip(onClick = { input = p }, label = { Text(l) }) }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (session.messages.isEmpty()) item {
                Text(
                    if (coding) "Likho ya paste karo: code, error, ya app idea. Main code ko compile/test nahi kar sakti - build tum karoge."
                    else "Hi! Kuch bhi poochho ya baat karo 💜 (Main ek AI hoon.)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 20.dp)
                )
            }
            items(session.messages.toList()) { m -> Bubble(m.role, m.text) }
            if (session.busy) item { CircularProgressIndicator(Modifier.padding(8.dp), strokeWidth = 2.dp) }
            session.error?.let { e -> item { Text("⚠ $e", color = MaterialTheme.colorScheme.error) } }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.weight(1f), maxLines = 5,
                placeholder = { Text(if (coding) "Code / question…" else "Message likho…") }
            )
            DictateButton { input = (input + " " + it).trim() }
            Button(
                onClick = { if (session.busy) session.stop() else { session.send(input); input = "" } },
                enabled = session.busy || input.isNotBlank()
            ) { Text(if (session.busy) "■" else "Send") }
        }
    }
}
