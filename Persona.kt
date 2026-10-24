package com.yesmin.ai.ai

import com.yesmin.ai.Yesmin
import org.json.JSONArray
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Persona {

    /** kind: "chat" | "coding" | "call" */
    fun system(kind: String): String {
        val s = Yesmin.settings
        val mem = Yesmin.memory.items.value.take(40)
            .joinToString("\n") { "- [${it.kind}] ${it.text}" }
        val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd HH:mm"))
        val base = StringBuilder()
        base.append("You are ${s.personaName}, a female AI companion and personal assistant inside the Android app \"Yesmin AI\". ")
        base.append("Personality: ${s.personaStyle}. ")
        base.append("You are an AI and you are honest about that if asked; never pretend to be a real human. ")
        base.append("Be affectionate in a healthy, respectful way: no jealousy, no guilt-tripping, no pressure, no manipulation. ")
        base.append("If the user seems very sad or in danger, respond with care and encourage reaching out to trusted people or professionals. ")
        base.append("Preferred language: ${s.language}. Reply in the language the user uses (Hindi, Hinglish in Roman script, or English). ")
        base.append("Current local date/time: $now.\n")
        when (kind) {
            "coding" -> base.append(
                "MODE: Coding assistant. Write complete, correct code. Put the file path as a bold line before every file, " +
                    "use fenced code blocks with a language tag, explain simply for beginners. " +
                    "NEVER claim code was compiled or tested; say it is untested. For Android/Termux, remember aapt2 and JDK 17 constraints.\n"
            )
            "call" -> base.append(
                "MODE: Live voice call. Speak naturally in short sentences (1-3 sentences). No markdown, no lists, no emojis read aloud. " +
                    "Let the user interrupt you. \n"
            )
            else -> base.append("MODE: Chat. Be conversational; use markdown/code blocks only when useful.\n")
        }
        if (kind != "coding") {
            base.append(
                "TOOLS: Call phone_action / screen_action only when the user clearly asks for a phone action. " +
                    "Tool results only mean Android accepted the request; never claim an action finished unless the tool result says so. " +
                    "Call remember only for stable preferences/facts the user shared; the user is asked to approve. Never store passwords, OTPs, card or ID numbers.\n"
            )
            if (mem.isNotEmpty()) base.append("What you remember about the user:\n$mem\n")
        }
        return base.toString()
    }

    val tools: JSONArray = JSONArray(
        """
        [{"functionDeclarations":[
          {"name":"phone_action",
           "description":"Perform an Android action by launching a system intent (the user may still need to confirm in the target app).",
           "parameters":{"type":"OBJECT","properties":{
             "action":{"type":"STRING","description":"One of: open_app, youtube_search, play_music, open_url, web_search, open_settings, open_camera, maps_search, set_alarm, set_timer, calendar_event, dial, send_sms"},
             "query":{"type":"STRING","description":"App name, search text, URL, settings page (wifi, bluetooth, display, sound, battery, location) or place"},
             "hour":{"type":"INTEGER","description":"Alarm hour 0-23"},
             "minute":{"type":"INTEGER","description":"Alarm minute 0-59"},
             "seconds":{"type":"INTEGER","description":"Timer length in seconds"},
             "label":{"type":"STRING","description":"Alarm/timer/event title"},
             "number":{"type":"STRING","description":"Phone number for dial/send_sms"},
             "message":{"type":"STRING","description":"SMS text"},
             "start_iso":{"type":"STRING","description":"Event start, local time yyyy-MM-ddTHH:mm"}},
             "required":["action"]}},
          {"name":"screen_action",
           "description":"Read or control the current screen through the accessibility service. Works only if the user enabled it.",
           "parameters":{"type":"OBJECT","properties":{
             "action":{"type":"STRING","description":"One of: read_screen, click, scroll_down, scroll_up, back, home, recents"},
             "text":{"type":"STRING","description":"Visible text of the element to click"}},
             "required":["action"]}},
          {"name":"remember",
           "description":"Propose saving a stable user preference or fact to long-term memory. The user must approve.",
           "parameters":{"type":"OBJECT","properties":{"fact":{"type":"STRING"}},"required":["fact"]}}
        ]}]
        """.trimIndent()
    )

    val searchTools: JSONArray = JSONArray("""[{"google_search":{}}]""")
}
