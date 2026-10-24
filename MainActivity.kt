package com.yesmin.ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yesmin.ai.automation.QuickAccess
import com.yesmin.ai.ui.AboutScreen
import com.yesmin.ai.ui.AutomationScreen
import com.yesmin.ai.ui.CallScreen
import com.yesmin.ai.ui.ChatScreen
import com.yesmin.ai.ui.HomeScreen
import com.yesmin.ai.ui.MemoryScreen
import com.yesmin.ai.ui.PermissionsScreen
import com.yesmin.ai.ui.SettingsScreen
import com.yesmin.ai.ui.ToolsScreen
import com.yesmin.ai.ui.YesminTheme

class MainActivity : ComponentActivity() {
    private val routeRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeRequest.value = intent?.getStringExtra("route")
        if (Yesmin.settings.quickAccess) QuickAccess.show(this)
        setContent {
            val theme by Yesmin.settings.themeFlow.collectAsState()
            YesminTheme(theme) {
                YesminRoot(routeRequest.value) { routeRequest.value = null }
                ConfirmDialog()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeRequest.value = intent.getStringExtra("route")
    }
}

private val mainRoutes = listOf("home", "chat", "call", "coding", "settings")

fun NavHostController.go(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun YesminRoot(requested: String?, consumed: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    LaunchedEffect(requested) {
        if (requested != null) { nav.go(requested); consumed() }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (route in mainRoutes) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    val items = listOf(
                        Triple("home", "Home", Icons.Filled.Home),
                        Triple("chat", "Chat", Icons.Filled.Send),
                        Triple("call", "Call", Icons.Filled.Call),
                        Triple("coding", "Code", Icons.Filled.Build),
                        Triple("settings", "Settings", Icons.Filled.Settings)
                    )
                    items.forEach { (r, label, icon) ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = { nav.go(r) },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(nav) }
            composable("chat") { ChatScreen(Yesmin.chat, "Yesmin Chat", coding = false) }
            composable("call") { CallScreen() }
            composable("coding") { ChatScreen(Yesmin.coding, "Coding Assistant", coding = true) }
            composable("settings") { SettingsScreen() }
            composable("automation") { AutomationScreen { nav.popBackStack() } }
            composable("memory") { MemoryScreen { nav.popBackStack() } }
            composable("tools") { ToolsScreen { nav.popBackStack() } }
            composable("permissions") { PermissionsScreen { nav.popBackStack() } }
            composable("about") { AboutScreen { nav.popBackStack() } }
        }
    }
}

@Composable
fun ConfirmDialog() {
    val list by Yesmin.pending.collectAsState()
    val p = list.firstOrNull() ?: return
    AlertDialog(
        onDismissRequest = { Yesmin.resolve(p, false) },
        title = { Text(p.title) },
        text = { Text(p.detail) },
        confirmButton = { TextButton(onClick = { Yesmin.resolve(p, true) }) { Text("Yes") } },
        dismissButton = { TextButton(onClick = { Yesmin.resolve(p, false) }) { Text("No") } }
    )
}
