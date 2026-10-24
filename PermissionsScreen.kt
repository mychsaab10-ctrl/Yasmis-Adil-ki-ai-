package com.yesmin.ai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yesmin.ai.automation.YesminAccessibilityService

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) } // bump to re-read status
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    fun open(action: String) = ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    val mic = granted(Manifest.permission.RECORD_AUDIO)
    val notif = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)
    val listener = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    val a11y = YesminAccessibilityService.isEnabled()
    tick.let { }

    SubScreen("Permissions & Privacy", onBack) {
        Text("Yesmin sirf wahi permission maangti hai jo feature ke liye zaroori hai. Kuch bhi apne aap on nahi hota.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        @Composable
        fun row(title: String, desc: String, ok: Boolean, label: String, onClick: () -> Unit) {
            GlowCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Chip(if (ok) "ON" else "OFF", ok)
                }
                Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onClick) { Text(label) }
            }
        }

        row("🎙 Microphone", "Voice call, dictation, voice notes. Mic sirf call/dictation ke dauran chalta hai.", mic, "Allow") {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        row("🔔 Notifications", "Call-in-progress aur quick-access notification dikhane ke liye.", notif, "Allow") {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        row("📩 Notification access", "Doosre apps ke notifications padhne ke liye (optional). Sirf memory mein rehte hain.", listener, "Open settings") {
            open("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        }
        row("♿ Accessibility service", "Screen padhne / tap / scroll ke liye (optional, risky - sirf tab on karo jab zaroorat ho).", a11y, "Open settings") {
            open(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
        row("⚙ App info", "Android ki app permission screen (camera/gallery picker ko permission nahi chahiye).", true, "Open") {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        SectionTitle("Privacy")
        Text(
            "• Chats Google ke Gemini API ko bheje jaate hain (tumhari key se). Google ki privacy policy lagti hai.\n" +
                "• Memory sirf is phone par SQLite mein hai; API key alag, Keystore-encrypted.\n" +
                "• Call/SMS Android ke dialer/Messages mein khulta hai - tum final button dabate ho.\n" +
                "• Backups band hain (allowBackup=false).\n" +
                "• Notifications aur screen text disk par save nahi hote.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface
        )
    }
}
