package com.petal.browser.compose.ai

import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import org.json.JSONArray

data class RawAiService(
    val name: String,
    val website: String,
    val pricing: String,
    val privacy: String,
    val loginRequired: Boolean,
    val bestFor: List<String>
)

data class AiService(
    val name: String,
    val url: String,
    val category: String,
    val pricing: String,
    val privacy: String,
    val loginRequired: Boolean,
    val bestFor: List<String>,
    val accentColor: Color
)

data class PetalAiHubSettings(
    var loadLastOpenedAi: Boolean = true,
    var defaultServiceName: String = "Duck AI",
    var enabledServices: Set<String> = emptySet(),
    var favoriteServices: Set<String> = emptySet(),
    var enableZoom: Boolean = true,
    var desktopView: Boolean = false,
    var thirdPartyCookies: Boolean = false,
    var fontSizePercentage: Int = 100,
    var updateFrequencyDays: Int = 3,
    var isProxy: Boolean = false,
    var proxyType: String = "http",
    var proxyHost: String = "localhost",
    var proxyPort: String = "9050",
    var customCss: String = "",
    var customJs: String = "",
    var filterCategories: Set<String> = emptySet(),
    var filterPrices: Set<String> = emptySet(),
    var filterPrivacy: Set<String> = emptySet(),
    var filterLoginRequired: Boolean? = null,
    var enableNewServicesByDefault: Boolean = true
)

fun generateAccentColorFromName(name: String): Color {
    val hash = name.hashCode()
    val hue = (hash and 0x7FFFFFFF) % 360f
    return Color.hsv(hue, 0.65f, 0.85f)
}

fun parseAiServicesJson(jsonString: String): List<AiService> {
    val list = mutableListOf<AiService>()
    try {
        val rootObj = JSONObject(jsonString)
        val keys = rootObj.keys()
        while (keys.hasNext()) {
            val category = keys.next()
            val array = rootObj.optJSONArray(category) ?: continue
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val name = item.optString("name", "")
                val website = item.optString("website", "")
                val pricing = item.optString("pricing", "freemium")
                val privacy = item.optString("privacy", "friendly")
                val loginReq = item.optBoolean("login_required", false)

                val bestForList = mutableListOf<String>()
                val bestForArray = item.optJSONArray("best_for")
                if (bestForArray != null) {
                    for (j in 0 until bestForArray.length()) {
                        bestForList.add(bestForArray.optString(j))
                    }
                }

                if (name.isNotBlank() && website.isNotBlank()) {
                    list.add(
                        AiService(
                            name = name,
                            url = website,
                            category = category,
                            pricing = pricing,
                            privacy = privacy,
                            loginRequired = loginReq,
                            bestFor = bestForList,
                            accentColor = generateAccentColorFromName(name)
                        )
                    )
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}
