@file:OptIn(ExperimentalMaterial3Api::class)

package com.yesmin.ai.ui

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import com.yesmin.ai.Yesmin
import com.yesmin.ai.ai.Attach
import com.yesmin.ai.ai.ChatMsg
import com.yesmin.ai.ai.GeminiException
import com.yesmin.ai.tools.ToolsEngine
import kotlinx.coroutines.launch

private val toolNames = listOf("Translate", "Study", "Writing", "Planner", "OCR", "Image Q&A", "PDF summary", "Voice note")

@Composable
fun ToolsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    var tool by remember { mutableStateOf("Translate") }
    var input by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("English") }
    var result by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var pdfUri by remember { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { bmp = ToolsEngine.loadBitmap(ctx, uri); if (bmp == null) result = "Could not open that image." }
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { b ->
        if (b != null) bmp = b
    }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pdfUri = uri }

    fun gemini(prompt: String, atts: List<Attach> = emptyList()) {
        busy = true; result = ""
        scope.launch {
            result = try {
                val s = Yesmin.settings
                Yesmin.gemini.generate(s.model, null, listOf(ChatMsg("user", prompt)), 0.4, atts).text
            } catch (e: GeminiException) {
                "⚠ ${e.message}"
            } catch (e: Exception) {
                "⚠ ${e.message}"
            }
            busy = false
        }
    }

    fun run() {
        when (tool) {
            "Translate" -> gemini("Translate the text below into $target. Output only the translation, then (if the target is a language the user is learning) one line with Roman-letter pronunciation.\n\n$input")
            "Study" -> gemini("You are a patient tutor. Explain simply in the user's language (Hinglish if they wrote Hinglish), with 1 example and 3 quiz questions with answers at the end.\n\nTopic/question: $input")
            "Writing" -> gemini("Help with this writing task (write, rewrite or improve as asked). Keep the user's language.\n\n$input")
            "Planner" -> gemini("Make a realistic daily plan with time blocks, breaks and 3 reminder suggestions. Use the user's language.\n\nTasks / context: $input")
            "OCR" -> {
                val b = bmp ?: return
                busy = true; result = ""
                scope.launch {
                    result = try { ToolsEngine.ocr(b).ifBlank { "No text found (OCR supports Latin script)." } } catch (e: Exception) { "⚠ OCR failed: ${e.message}" }
                    busy = false
                }
            }
            "Image Q&A" -> {
                val b = bmp ?: return
                gemini(input.ifBlank { "Describe this image in detail." }, listOf(ToolsEngine.bitmapToAttach(b)))
            }
            "PDF summary" -> {
                val u = pdfUri ?: return
                busy = true; result = ""
                scope.launch {
                    try {
                        val (pages, total) = ToolsEngine.pdfPages(ctx, u)
                        val q = input.ifBlank { "Summarize this document in clear bullet points." }
                        val note = if (total > pages.size) "\n(Only the first ${pages.size} of $total pages were read.)" else ""
                        result = Yesmin.gemini.generate(Yesmin.settings.model, null, listOf(ChatMsg("user", q)), 0.3, pages).text + note
                    } catch (e: GeminiException) {
                        result = "⚠ ${e.message}"
                    } catch (e: Exception) {
                        result = "⚠ Could not read the PDF: ${e.message}"
                    }
                    busy = false
                }
            }
            "Voice note" -> gemini("Clean up this voice note transcript and give: 1) a short summary 2) action items.\n\n$input")
        }
    }

    val needsImage = tool == "OCR" || tool == "Image Q&A"
    val ready = when (tool) {
        "OCR", "Image Q&A" -> bmp != null
        "PDF summary" -> pdfUri != null
        else -> input.isNotBlank()
    }

    SubScreen("Tools & Documents", onBack) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            toolNames.forEach { n -> FilterChip(selected = tool == n, onClick = { tool = n; result = "" }, label = { Text(n) }) }
        }
        if (tool == "Translate") {
            OutlinedTextField(target, { target = it }, Modifier.fillMaxWidth(), label = { Text("Target language") }, singleLine = true)
        }
        if (needsImage) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { takePhoto.launch(null) }) { Text("📷 Camera") }
                OutlinedButton(onClick = { pickImage.launch("image/*") }) { Text("🖼 Gallery") }
            }
            Text(if (bmp != null) "Image ready ✅" else "No image selected", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (tool == "PDF summary") {
            OutlinedButton(onClick = { pickPdf.launch(arrayOf("application/pdf")) }) { Text("📄 Choose PDF") }
            Text(if (pdfUri != null) "PDF selected ✅ (first 8 pages are read)" else "No PDF selected", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (tool != "OCR") {
            OutlinedTextField(
                input, { input = it }, Modifier.fillMaxWidth(), minLines = 3,
                label = { Text(if (tool == "Image Q&A" || tool == "PDF summary") "Question (optional)" else "Text / topic") }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tool == "Voice note") DictateButton { input = (input + " " + it).trim() }
            Button(enabled = ready && !busy, onClick = { run() }) { Text(if (busy) "Working…" else "Run") }
        }
        if (result.isNotBlank()) {
            SectionTitle("Result")
            GlowCard(Modifier.fillMaxWidth()) { Text(result, color = MaterialTheme.colorScheme.onSurface) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { clip.setText(AnnotatedString(result)); Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show() }) { Text("Copy") }
                OutlinedButton(onClick = { Yesmin.tts.speak(result) }) { Text("🔊") }
                if (tool == "Voice note") OutlinedButton(onClick = { Yesmin.proposeMemory("note", result) }) { Text("Save to memory") }
            }
        }
        Text(
            "Web search: Chat screen mein 🌐 chip on karo. Wake-word, floating bubble aur screen-capture abhi implement nahi hain.",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
