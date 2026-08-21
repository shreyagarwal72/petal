package com.petal.browser.compose.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object PetalAiHubManager {

    private const val CLOUD_BASE_URL = "https://raw.githubusercontent.com/shreyagarwal72/petal-aihub-config/main/"
    private const val AI_SERVICES_FILE = "ais.json"
    private const val DOMAINS_FILE = "domains.txt"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    const val DEFAULT_AIS_JSON = """
    {
      "chatbot": [
        {"name": "Duck AI", "website": "https://duck.ai", "pricing": "freemium", "privacy": "friendly", "login_required": false, "best_for": ["private chat", "image gen", "pdf chat", "anonymous AI"]},
        {"name": "Venice AI", "website": "https://venice.ai/chat", "pricing": "freemium", "privacy": "friendly", "login_required": false, "best_for": ["uncensored chat", "creative writing", "privacy", "open models"]},
        {"name": "Grok AI", "website": "https://grok.com", "pricing": "freemium", "privacy": "avoid", "login_required": true, "best_for": ["coding", "realtime search", "xAI", "reasoning"]},
        {"name": "Lumo AI", "website": "https://lumo.proton.me", "pricing": "freemium", "privacy": "friendly", "login_required": true, "best_for": ["private chat", "secure AI", "proton privacy", "assistance"]},
        {"name": "DeepSeek", "website": "https://chat.deepseek.com", "pricing": "free", "privacy": "avoid", "login_required": true, "best_for": ["coding", "deep reasoning", "r1 model", "technical"]},
        {"name": "ChatGPT", "website": "https://chatgpt.com", "pricing": "freemium", "privacy": "avoid", "login_required": true, "best_for": ["writing", "coding", "general AI", "voice chat"]},
        {"name": "Gemini", "website": "https://gemini.google.com", "pricing": "freemium", "privacy": "avoid", "login_required": true, "best_for": ["multimodal", "google ecosystem", "research", "summarization"]},
        {"name": "Google AI Studio", "website": "https://aistudio.google.com", "pricing": "freemium", "privacy": "avoid", "login_required": true, "best_for": ["prompt engineering", "gemini flash 2.0", "long context", "app building"]},
        {"name": "Claude AI", "website": "https://claude.ai/chat", "pricing": "freemium", "privacy": "avoid", "login_required": true, "best_for": ["coding", "long documents", "sonnet 3.5", "writing"]},
        {"name": "Mistral Le Chat", "website": "https://chat.mistral.ai", "pricing": "freemium", "privacy": "friendly", "login_required": true, "best_for": ["open weights", "coding", "fast chat", "european privacy"]},
        {"name": "Blackbox AI", "website": "https://www.blackbox.ai", "pricing": "freemium", "privacy": "avoid", "login_required": false, "best_for": ["code generation", "repo search", "debugging"]}
      ],
      "search & research": [
        {"name": "Perplexity AI", "website": "https://www.perplexity.ai", "pricing": "freemium", "privacy": "avoid", "login_required": true, "best_for": ["web research", "cited answers", "pro search", "deep dive"]},
        {"name": "Kagi Search", "website": "https://kagi.com", "pricing": "paid", "privacy": "friendly", "login_required": true, "best_for": ["ad-free search", "lenses", "custom ranking", "fast Results"]},
        {"name": "Brave Leo", "website": "https://search.brave.com/search", "pricing": "free", "privacy": "friendly", "login_required": false, "best_for": ["private search", "summaries", "brave search", "leo assistant"]},
        {"name": "Exa AI", "website": "https://exa.ai", "pricing": "freemium", "privacy": "friendly", "login_required": false, "best_for": ["neural search", "developer search", "semantic web"]}
      ],
      "creative & image": [
        {"name": "Midjourney", "website": "https://www.midjourney.com", "pricing": "paid", "privacy": "avoid", "login_required": true, "best_for": ["art", "photorealism", "v6 generation", "design"]},
        {"name": "Ideogram", "website": "https://ideogram.ai", "pricing": "freemium", "privacy": "avoid", "login_required": true, "best_for": ["typography", "logo design", "text in image", "2.0 model"]},
        {"name": "Recraft AI", "website": "https://www.recraft.ai", "pricing": "freemium", "privacy": "friendly", "login_required": true, "best_for": ["vector art", "brand design", "3D art", "svg export"]},
        {"name": "Flux AI (BFL)", "website": "https://blackforestlabs.ai", "pricing": "freemium", "privacy": "friendly", "login_required": false, "best_for": ["flux1.dev", "state-of-the-art art", "photorealism"]}
      ],
      "coding & dev": [
        {"name": "v0 by Vercel", "website": "https://v0.dev", "pricing": "freemium", "privacy": "avoid", "login_required": true, "best_for": ["ui generation", "react", "tailwind", "next.js"]},
        {"name": "Bolt.new", "website": "https://bolt.new", "pricing": "freemium", "privacy": "avoid", "login_required": true, "best_for": ["fullstack web apps", "webcontainers", "in-browser dev"]},
        {"name": "Lovable AI", "website": "https://lovable.dev", "pricing": "freemium", "privacy": "avoid", "login_required": true, "best_for": ["web app builder", "gpt-4o coding", "supabase integration"]},
        {"name": "Cursor Directory", "website": "https://cursor.directory", "pricing": "free", "privacy": "friendly", "login_required": false, "best_for": ["system prompts", "rules for AI", "cursor config"]}
      ]
    }
    """

    fun getSp(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    suspend fun getAiServices(context: Context): List<AiService> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, AI_SERVICES_FILE)
        val jsonString = if (file.exists()) {
            file.readText()
        } else {
            DEFAULT_AIS_JSON
        }
        val services = parseAiServicesJson(jsonString)
        if (services.isEmpty()) {
            parseAiServicesJson(DEFAULT_AIS_JSON)
        } else {
            services
        }
    }

    suspend fun syncCloudData(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(CLOUD_BASE_URL + AI_SERVICES_FILE).build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val file = File(context.filesDir, AI_SERVICES_FILE)
                    file.writeText(body)
                    getSp(context).edit().putLong("sp_aihub_last_sync", System.currentTimeMillis()).apply()
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext false
    }

    fun getSettings(context: Context): PetalAiHubSettings {
        val sp = getSp(context)
        return PetalAiHubSettings(
            loadLastOpenedAi = sp.getBoolean("sp_aihub_load_last_opened", true),
            defaultServiceName = sp.getString("sp_aihub_default_service", "Duck AI") ?: "Duck AI",
            enabledServices = sp.getStringSet("sp_aihub_enabled_services", emptySet()) ?: emptySet(),
            favoriteServices = sp.getStringSet("sp_aihub_favorite_services", emptySet()) ?: emptySet(),
            enableZoom = sp.getBoolean("sp_aihub_enable_zoom", true),
            desktopView = sp.getBoolean("sp_aihub_desktop_view", false),
            thirdPartyCookies = sp.getBoolean("sp_aihub_third_party_cookies", false),
            fontSizePercentage = sp.getInt("sp_aihub_font_size", 100),
            updateFrequencyDays = sp.getInt("sp_aihub_update_freq", 3),
            isProxy = sp.getBoolean("sp_aihub_is_proxy", false),
            proxyType = sp.getString("sp_aihub_proxy_type", "http") ?: "http",
            proxyHost = sp.getString("sp_aihub_proxy_host", "localhost") ?: "localhost",
            proxyPort = sp.getString("sp_aihub_proxy_port", "9050") ?: "9050",
            customCss = sp.getString("sp_aihub_custom_css", "") ?: "",
            customJs = sp.getString("sp_aihub_custom_js", "") ?: ""
        )
    }

    fun saveSettings(context: Context, settings: PetalAiHubSettings) {
        val sp = getSp(context)
        sp.edit()
            .putBoolean("sp_aihub_load_last_opened", settings.loadLastOpenedAi)
            .putString("sp_aihub_default_service", settings.defaultServiceName)
            .putStringSet("sp_aihub_enabled_services", settings.enabledServices)
            .putStringSet("sp_aihub_favorite_services", settings.favoriteServices)
            .putBoolean("sp_aihub_enable_zoom", settings.enableZoom)
            .putBoolean("sp_aihub_desktop_view", settings.desktopView)
            .putBoolean("sp_aihub_third_party_cookies", settings.thirdPartyCookies)
            .putInt("sp_aihub_font_size", settings.fontSizePercentage)
            .putInt("sp_aihub_update_freq", settings.updateFrequencyDays)
            .putBoolean("sp_aihub_is_proxy", settings.isProxy)
            .putString("sp_aihub_proxy_type", settings.proxyType)
            .putString("sp_aihub_proxy_host", settings.proxyHost)
            .putString("sp_aihub_proxy_port", settings.proxyPort)
            .putString("sp_aihub_custom_css", settings.customCss)
            .putString("sp_aihub_custom_js", settings.customJs)
            .apply()
    }

    fun toggleFavoriteService(context: Context, serviceName: String) {
        val sp = getSp(context)
        val currentFavs = sp.getStringSet("sp_aihub_favorite_services", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (currentFavs.contains(serviceName)) {
            currentFavs.remove(serviceName)
        } else {
            currentFavs.add(serviceName)
        }
        sp.edit().putStringSet("sp_aihub_favorite_services", currentFavs).apply()
    }
}

private fun String?.isNotNullOrBlank(): Boolean = !this.isNullOrBlank()
