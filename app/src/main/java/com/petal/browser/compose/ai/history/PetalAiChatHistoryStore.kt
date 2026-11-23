package com.petal.browser.compose.ai.history

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.petal.browser.compose.ai.AiRole
import com.petal.browser.compose.ai.PetalChatMessage
import java.util.UUID

data class SavedChatMessage(
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SavedChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val pageUrl: String,
    val timestamp: Long = System.currentTimeMillis(),
    val messages: List<SavedChatMessage>
)

/**
 * Local persistent store for saved Petal AI chat conversations.
 *
 * Safety & Privacy:
 * - Saved strictly on explicit user request.
 * - NEVER saves incognito / private tab conversations.
 * - Includes delete-one and delete-all capabilities.
 */
object PetalAiChatHistoryStore {

    private const val PREFS_NAME = "petal_ai_chat_history_vault"
    private const val KEY_SESSIONS = "saved_chat_sessions_json"
    private val gson = Gson()

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun getSessions(context: Context): List<SavedChatSession> {
        val sp = prefs(context)
        val json = sp.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SavedChatSession>>() {}.type
            gson.fromJson<List<SavedChatSession>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Saves a conversation session. Strictly refuses to save if [isPrivate] is true.
     */
    @Synchronized
    fun saveSession(
        context: Context,
        title: String,
        pageUrl: String,
        messages: List<PetalChatMessage>,
        isPrivate: Boolean
    ): Boolean {
        if (isPrivate || messages.isEmpty()) {
            return false
        }

        val sessions = getSessions(context).toMutableList()
        val mappedMessages = messages.map {
            SavedChatMessage(role = it.role.name, text = it.text, timestamp = it.timestamp)
        }

        val newSession = SavedChatSession(
            title = title.ifBlank { "Chat session" },
            pageUrl = pageUrl,
            messages = mappedMessages
        )

        sessions.add(0, newSession) // newest first
        val json = gson.toJson(sessions)
        prefs(context).edit().putString(KEY_SESSIONS, json).apply()
        return true
    }

    @Synchronized
    fun searchSessions(context: Context, query: String): List<SavedChatSession> {
        val all = getSessions(context)
        if (query.isBlank()) return all
        val q = query.trim().lowercase()
        return all.filter { session ->
            session.title.lowercase().contains(q) ||
                    session.pageUrl.lowercase().contains(q) ||
                    session.messages.any { it.text.lowercase().contains(q) }
        }
    }

    @Synchronized
    fun deleteSession(context: Context, sessionId: String): Boolean {
        val sessions = getSessions(context).toMutableList()
        val removed = sessions.removeAll { it.id == sessionId }
        if (removed) {
            val json = gson.toJson(sessions)
            prefs(context).edit().putString(KEY_SESSIONS, json).apply()
        }
        return removed
    }

    @Synchronized
    fun clearAll(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
