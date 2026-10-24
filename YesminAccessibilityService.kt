package com.yesmin.ai.automation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.yesmin.ai.Yesmin
import org.json.JSONObject

/**
 * Optional screen control. It does NOTHING by itself: it only reacts to calls that come from the
 * Yesmin app after the user (1) enabled the service in Android Settings and (2) switched on
 * "Allow screen control" inside Yesmin. Taps additionally need an on-screen confirmation.
 */
class YesminAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || sb.length > 6000 || depth > 40) return
            if (n.isPassword) return // never read password fields
            val t = n.text?.toString() ?: n.contentDescription?.toString()
            if (!t.isNullOrBlank()) sb.append(t).append('\n')
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return sb.toString().trim()
    }

    fun clickText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (n in nodes) {
            var cur: AccessibilityNodeInfo? = n
            while (cur != null) {
                if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                cur = cur.parent
            }
        }
        return false
    }

    fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        fun find(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (n == null) return null
            if (n.isScrollable) return n
            for (i in 0 until n.childCount) find(n.getChild(i))?.let { return it }
            return null
        }
        val target = find(root) ?: return false
        return target.performAction(
            if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
    }

    companion object {
        @Volatile var instance: YesminAccessibilityService? = null
        fun isEnabled() = instance != null
    }
}

object ScreenControl {
    /** Returns a result string. Never claims more than Android confirmed. */
    fun run(args: JSONObject): String {
        if (!Yesmin.accessGate.value) return "Blocked: screen control is switched off inside Yesmin (Phone Automation screen)."
        val svc = YesminAccessibilityService.instance
            ?: return "Blocked: the Yesmin accessibility service is not enabled in Android Settings."
        return when (val a = args.optString("action")) {
            "read_screen" -> svc.readScreen().take(3000).ifBlank { "The screen has no readable text." }
            "back" -> res(svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK), "Back")
            "home" -> res(svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME), "Home")
            "recents" -> res(svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS), "Recents")
            "scroll_down" -> res(svc.scroll(true), "Scroll down")
            "scroll_up" -> res(svc.scroll(false), "Scroll up")
            "click" -> {
                val t = args.optString("text")
                if (t.isBlank()) return "Failed: no element text given."
                Yesmin.ask(
                    com.yesmin.ai.Pending("Tap on screen?", "Yesmin wants to tap the element with text \"$t\" in the app on screen.") {
                        val ok = svc.clickText(t)
                        Toast.makeText(Yesmin.app, if (ok) "Tap performed on \"$t\"" else "Could not find a tappable \"$t\"", Toast.LENGTH_SHORT).show()
                    }
                )
                "Waiting for the user to confirm the tap. Not done yet."
            }
            else -> "Unsupported screen action: $a"
        }
    }

    private fun res(ok: Boolean, what: String) =
        if (ok) "$what: Android reported the gesture was performed." else "$what: Android reported failure."
}
