package com.yesmin.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import com.yesmin.ai.Yesmin
import com.yesmin.ai.voice.CallState

@Composable
fun CallScreen() {
    val call = Yesmin.call
    val ctx = LocalContext.current
    val view = LocalView.current
    val state by call.state.collectAsState()
    val status by call.status.collectAsState()
    val subs by call.subtitles.collectAsState()
    val muted by call.muted.collectAsState()
    val speaker by call.speaker.collectAsState()
    val secs by call.seconds.collectAsState()
    val mode by call.modeLabel.collectAsState()
    val inCall = state != CallState.Idle && state != CallState.Error
    val list = rememberLazyListState()

    // keep the screen awake during a call; the call itself keeps running in the background service
    DisposableEffect(inCall) {
        view.keepScreenOn = inCall
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(subs.size, subs.lastOrNull()?.text) {
        if (subs.isNotEmpty()) list.scrollToItem(subs.size - 1)
    }

    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) call.start() else {
            call.status.value = "Microphone permission denied. Allow it in Permissions screen."
        }
    }
    fun startCall() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) call.start()
        else micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Yesmin Call", fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
        Text(
            if (inCall) "%02d:%02d  •  %s".format(secs / 60, secs % 60, mode) else "AI voice call - Yesmin is an AI, not a human",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
        )
        VoiceOrb(state, Modifier.size(210.dp).padding(8.dp))
        Text(
            when (state) {
                CallState.Idle -> "Ready"
                CallState.Connecting -> "Connecting"
                CallState.Listening -> "● Listening"
                CallState.Thinking -> "● Thinking"
                CallState.Speaking -> "● Speaking"
                CallState.Error -> "Problem"
            },
            fontSize = 18.sp, color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            status, fontSize = 13.sp,
            color = if (state == CallState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), state = list, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(subs) { s ->
                Text(
                    "${s.who}: ${s.text}",
                    color = if (s.who == "You") Cyan else Pink, fontSize = 15.sp
                )
            }
        }
        if (inCall) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { call.toggleMute() }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(if (muted) "🔇 Unmute" else "🎙 Mute", fontSize = 13.sp)
                }
                OutlinedButton(onClick = { call.interrupt() }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("✋ Interrupt", fontSize = 13.sp)
                }
                OutlinedButton(onClick = { call.toggleSpeaker() }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(if (speaker) "🔊 Speaker" else "📱 Ear", fontSize = 13.sp)
                }
            }
            Button(
                onClick = { call.stop() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5484D)),
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp)
            ) { Text("End call", fontSize = 17.sp, color = Color.White) }
        } else {
            Button(onClick = { startCall() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("📞  Start call", fontSize = 17.sp)
            }
            Text(
                "Tip: headphones se barge-in (beech mein bolna) best kaam karta hai.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
