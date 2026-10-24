package com.yesmin.ai.automation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.app.SearchManager
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Phone actions implemented ONLY with public Android intents.
 * A successful return value means "Android accepted the intent" - not that the task finished.
 * Calls and SMS only open the dialer / composer; the user presses the final button.
 */
object Actions {
    private val confirmActions = setOf("dial", "send_sms")

    fun needsConfirm(action: String) = action in confirmActions

    fun describe(args: JSONObject): String = when (args.optString("action")) {
        "dial" -> "Open the dialer for ${args.optString("number")}. You press Call yourself."
        "send_sms" -> "Open Messages to ${args.optString("number")} with text: \"${args.optString("message")}\". You press Send yourself."
        else -> args.toString()
    }

    private fun launch(ctx: Context, i: Intent, what: String): String {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            "Launched: $what. Android accepted the request (I cannot see what happens next)."
        } catch (e: ActivityNotFoundException) {
            "Failed: no app on this phone can handle \"$what\"."
        } catch (e: SecurityException) {
            "Failed: Android denied permission for \"$what\"."
        } catch (e: Exception) {
            "Failed: ${e.message}"
        }
    }

    fun run(ctx: Context, args: JSONObject): String {
        val q = args.optString("query").trim()
        return when (val a = args.optString("action")) {
            "open_app" -> openApp(ctx, q)
            "youtube_search" -> youtube(ctx, q)
            "play_music" -> playMusic(ctx, q)
            "open_url" -> {
                val url = if (q.startsWith("http://") || q.startsWith("https://")) q else "https://$q"
                launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)), "open $url")
            }
            "web_search" -> launch(
                ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))),
                "web search for \"$q\""
            )
            "open_settings" -> launch(ctx, Intent(settingsAction(q)), "Settings ($q)")
            "open_camera" -> launch(ctx, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "camera")
            "maps_search" -> launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q))), "maps for \"$q\"")
            "set_alarm" -> {
                if (!args.has("hour")) return "Failed: no alarm time given."
                val i = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, args.optInt("hour"))
                    .putExtra(AlarmClock.EXTRA_MINUTES, args.optInt("minute"))
                    .putExtra(AlarmClock.EXTRA_MESSAGE, args.optString("label", "Yesmin alarm"))
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false) // clock app shows it so you can confirm
                launch(ctx, i, "alarm %02d:%02d".format(args.optInt("hour"), args.optInt("minute")))
            }
            "set_timer" -> {
                val sec = args.optInt("seconds", 0)
                if (sec <= 0) return "Failed: no timer length given."
                val i = Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, sec)
                    .putExtra(AlarmClock.EXTRA_MESSAGE, args.optString("label", "Yesmin timer"))
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                launch(ctx, i, "timer for $sec seconds")
            }
            "calendar_event" -> calendar(ctx, args)
            "dial" -> launch(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(args.optString("number")))), "dialer")
            "send_sms" -> launch(
                ctx,
                Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(args.optString("number"))))
                    .putExtra("sms_body", args.optString("message")),
                "message composer"
            )
            else -> "Unsupported action: $a"
        }
    }

    @Suppress("DEPRECATION")
    private fun openApp(ctx: Context, name: String): String {
        if (name.isBlank()) return "Failed: no app name given."
        val pm = ctx.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val n = name.lowercase()
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == n }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(n) }
            ?: return "Failed: I could not find an installed app called \"$name\"."
        val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName)
            ?: return "Failed: that app has no launch screen."
        return launch(ctx, i, "app ${hit.loadLabel(pm)}")
    }

    private fun youtube(ctx: Context, q: String): String {
        val yt = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", q)
        yt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(yt)
            "Launched: YouTube search for \"$q\". Tap a result to play (Android does not allow auto-play)."
        } catch (e: Exception) {
            launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))), "YouTube search in browser for \"$q\"")
        }
    }

    private fun playMusic(ctx: Context, q: String): String {
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .putExtra(SearchManager.QUERY, q)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(i)
            "Launched: music search for \"$q\" in your music app (it decides whether to auto-play)."
        } catch (e: Exception) {
            youtube(ctx, "$q song")
        }
    }

    private fun settingsAction(q: String): String {
        val s = q.lowercase()
        return when {
            "wifi" in s || "wi-fi" in s -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" in s -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" in s || "brightness" in s -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" in s || "volume" in s -> Settings.ACTION_SOUND_SETTINGS
            "battery" in s -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "location" in s || "gps" in s -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "airplane" in s -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "accessibility" in s -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "app" in s -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
    }

    private fun calendar(ctx: Context, args: JSONObject): String {
        val iso = args.optString("start_iso")
        val begin = try {
            LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            return "Failed: I need a valid start time (yyyy-MM-ddTHH:mm)."
        }
        val i = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, args.optString("label", "Yesmin event"))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 60 * 60 * 1000)
        return launch(ctx, i, "calendar event form (you press Save)")
    }
}
