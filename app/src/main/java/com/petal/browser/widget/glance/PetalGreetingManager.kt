package com.petal.browser.widget.glance

import android.content.Context
import androidx.preference.PreferenceManager
import java.util.Calendar
import kotlin.random.Random

object PetalGreetingManager {

    private val GENERAL_TEMPLATES = listOf(
        "Welcome back, {username}—the web is ready whenever you are.",
        "What are you curious about today, {username}?",
        "Ready to discover something amazing, {username}?",
        "{username}, here is your personal window to the entire web.",
        "Search deeper and expand your world, {username}.",
        "Bring your biggest ideas to life today, {username}.",
        "Your next great breakthrough starts right here, {username}.",
        "Designed for your speed and creativity, {username}.",
        "Stay inspired and keep building, {username}.",
        "Good to see you again, {username}; let's make things happen.",
        "Clear mind and a fresh tab, {username}—what's on your agenda?",
        "Everything is set up and waiting for you, {username}.",
        "Your digital workspace is primed and ready, {username}."
    )

    private val MORNING_TEMPLATES = listOf(
        "Good morning, {username}! Start the day with a fresh perspective."
    )

    private val AFTERNOON_TEMPLATES = listOf(
        "Good afternoon, {username}—keep that momentum rolling."
    )

    private val EVENING_TEMPLATES = listOf(
        "Good evening, {username}; time to unwind and explore something fun."
    )

    /**
     * Returns a random time-appropriate greeting populated with the user's name (or "Petal Explorer" if empty).
     */
    fun getRandomGreeting(context: Context): String {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)

        // Try getting name from Google Account or User profile preference
        val rawName = sp.getString("sp_google_account_display_name", null)
            ?: sp.getString("sp_user_name", null)
            ?: sp.getString("user_name", null)

        val username = if (!rawName.isNullOrBlank()) rawName.trim() else "Petal Explorer"

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeTemplates = when {
            hour in 4..11 -> MORNING_TEMPLATES
            hour in 12..16 -> AFTERNOON_TEMPLATES
            else -> EVENING_TEMPLATES
        }

        val pool = GENERAL_TEMPLATES + timeTemplates
        val template = pool[Random.nextInt(pool.size)]
        return template.replace("{username}", username)
    }
}
