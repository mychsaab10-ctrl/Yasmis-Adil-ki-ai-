package com.yesmin.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.yesmin.ai.Yesmin
import com.yesmin.ai.data.Memory
import java.text.DateFormat
import java.util.Date

@Composable
fun MemoryScreen(onBack: () -> Unit) {
    val items by Yesmin.memory.items.collectAsState()
    var editing by remember { mutableStateOf<Memory?>(null) }
    var editText by remember { mutableStateOf("") }
    var newText by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    SubScreen("Memory", onBack) {
        Text(
            "Yahan sirf wahi cheezein hain jo tumne approve ki. Sab kuch sirf is phone par save hota hai (API key se alag).",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
        )
        OutlinedTextField(
            newText, { newText = it }, Modifier.fillMaxWidth(),
            label = { Text("Nayi memory add karo (jaise: mujhe chai pasand hai)") }
        )
        Button(onClick = { Yesmin.proposeMemory("fact", newText); newText = "" }, enabled = newText.isNotBlank()) { Text("Add") }
        SectionTitle("Saved (${items.size})")
        if (items.isEmpty()) Text("Abhi koi memory nahi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEach { m ->
            GlowCard(Modifier.fillMaxWidth()) {
                Text("${m.kind} • ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(m.ts))}", fontSize = 11.sp, color = Cyan)
                Text(m.text, color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { editing = m; editText = m.text }) { Text("Edit") }
                    TextButton(onClick = { Yesmin.memory.delete(m.id) }) { Text("Delete") }
                }
            }
        }
        if (items.isNotEmpty()) OutlinedButton(onClick = { confirmClear = true }) { Text("Clear all memory") }
    }

    editing?.let { m ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit memory") },
            text = { OutlinedTextField(editText, { editText = it }, Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { Yesmin.memory.update(m.id, editText); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear ALL memory?") },
            text = { Text("Yeh wapas nahi aayega.") },
            confirmButton = { TextButton(onClick = { Yesmin.memory.clearAll(); confirmClear = false }) { Text("Delete everything") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}
