package com.petal.browser.widget.glance

import android.content.Context
import androidx.preference.PreferenceManager
import kotlin.random.Random

object PetalGreetingManager {

    private val GREETING_TEMPLATES = listOf(
        "The web is yours to uncover, {username}.",
        "Where will your curiosity take you today, {username}?",
        "Ready to discover something new, {username}?",
        "{username}, your window to the world is open.",
        "Search deeper, explore wider, {username}.",
        "Turn ideas into reality today, {username}.",
        "{username}, your next big project starts with a search.",
        "Built for creation, styled for speed—let's go, {username}.",
        "Create, connect, and stay inspired, {username}.",
        "Welcome back, {username}. Let's get to work.",
        "Clear tabs, clear mind. Ready when you are, {username}.",
        "Fast, focused, and ready, {username}.",
        "Your digital space, tailored for you, {username}.",
        "Good morning, {username}. A fresh start for big ideas.",
        "Good afternoon, {username}. Keep the momentum going.",
        "Good evening, {username}. Winding down or diving into something fun?"
    )

    /**
     * Returns a random greeting populated with the user's name (or "Petal Explorer" if empty).
     */
    fun getRandomGreeting(context: Context): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)

        // Try getting name from Google Account or User profile preference
        val rawName = sp.getString("sp_google_account_display_name", null)
            ?: sp.getString("sp_user_name", null)
            ?: sp.getString("user_name", null)

        val username = if (!rawName.isNullOrBlank()) rawName.trim() else "Petal Explorer"
        val template = GREETING_TEMPLATES[Random.nextInt(GREETING_TEMPLATES.size)]
        return template.replace("{username}", username)
    }
}
