package com.yesmin.ai.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class Memory(val id: Long, val kind: String, val text: String, val ts: Long)

/** Plain SQLite (no annotation processor needed => lighter, safer Termux build). */
private class MemoryHelper(ctx: Context) : SQLiteOpenHelper(ctx, "yesmin_memory.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE memory(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, text TEXT NOT NULL, ts INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}

class MemoryRepo(ctx: Context, private val scope: CoroutineScope) {
    private val helper = MemoryHelper(ctx)
    private val _items = MutableStateFlow<List<Memory>>(emptyList())
    val items: StateFlow<List<Memory>> = _items

    init {
        refresh()
    }

    private fun load(): List<Memory> {
        val out = ArrayList<Memory>()
        helper.readableDatabase.rawQuery("SELECT id,kind,text,ts FROM memory ORDER BY ts DESC", null).use { c ->
            while (c.moveToNext()) out.add(Memory(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)))
        }
        return out
    }

    fun refresh() {
        scope.launch(Dispatchers.IO) { _items.value = load() }
    }

    /** kind: "fact" | "summary" | "note" */
    fun add(kind: String, text: String) {
        if (text.isBlank()) return
        scope.launch(Dispatchers.IO) {
            val v = ContentValues().apply {
                put("kind", kind); put("text", text.trim()); put("ts", System.currentTimeMillis())
            }
            helper.writableDatabase.insert("memory", null, v)
            _items.value = load()
        }
    }

    fun update(id: Long, text: String) {
        scope.launch(Dispatchers.IO) {
            val v = ContentValues().apply { put("text", text.trim()) }
            helper.writableDatabase.update("memory", v, "id=?", arrayOf(id.toString()))
            _items.value = load()
        }
    }

    fun delete(id: Long) {
        scope.launch(Dispatchers.IO) {
            helper.writableDatabase.delete("memory", "id=?", arrayOf(id.toString()))
            _items.value = load()
        }
    }

    fun clearAll() {
        scope.launch(Dispatchers.IO) {
            helper.writableDatabase.delete("memory", null, null)
            _items.value = load()
        }
    }

    companion object {
        private val sensitive = Regex(
            "(password|passcode|pin\\b|otp|cvv|card number|credit card|aadhaar|aadhar|pan card|passport|ssn|bank account|account number|secret|api key|पासवर्ड|ओटीपी)",
            RegexOption.IGNORE_CASE
        )

        fun looksSensitive(text: String): Boolean =
            sensitive.containsMatchIn(text) || Regex("\\d{9,}").containsMatchIn(text)
    }
}
