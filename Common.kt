@file:OptIn(ExperimentalMaterial3Api::class)

package com.yesmin.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.yesmin.ai.Yesmin
import com.yesmin.ai.voice.Stt

/** Wrapper for sub-screens that have a back arrow. */
@Composable
fun SubScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { content() }
    }
}

/** 🎤 button: one-shot dictation using the phone's speech recognizer. */
@Composable
fun DictateButton(onText: (String) -> Unit) {
    val ctx = LocalContext.current
    val stt = remember { Stt(ctx) }
    var listening by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { stt.destroy() } }

    fun begin() {
        listening = true; hint = ""
        stt.start(
            Yesmin.settings.sttLang,
            onPart = { hint = it },
            onDone = { listening = false; hint = ""; onText(it) },
            onErr = { listening = false; hint = Stt.errorText(it) }
        )
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else hint = "Microphone permission denied"
    }
    TextButton(onClick = {
        if (listening) { stt.cancel(); listening = false }
        else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
        else perm.launch(Manifest.permission.RECORD_AUDIO)
    }) { Text(if (listening) "⏹ ${hint.take(14).ifBlank { "Listening" }}" else if (hint.isNotBlank()) "🎤 ($hint)" else "🎤") }
}
