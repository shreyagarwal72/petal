package com.petal.browser.engine.petal.blocking

internal object BundledPetalRuleGroups {
    const val Ads = "Petal Ads"
    const val Cookies = "Petal Cookies"
}

internal data class BundledPetalRules private constructor(
    val rules: List<PetalRule>,
) {
    val matcher = PetalMatcherSnapshot.compile(rules)

    val cookieCosmeticRules: List<PetalRule>
        get() = rules.filter { rule ->
            rule.active && rule.action == PetalRuleAction.Cosmetic &&
                rule.kind == PetalRuleKind.CosmeticCss &&
                rule.group == BundledPetalRuleGroups.Cookies
        }

    val adCosmeticRules: List<PetalRule>
        get() = rules.filter { rule ->
            rule.active && rule.action == PetalRuleAction.Cosmetic &&
                rule.kind == PetalRuleKind.CosmeticCss &&
                rule.group == BundledPetalRuleGroups.Ads
        }

    fun adCosmeticSelectors(pageUrl: String?): List<String> {
        if (pageUrl == null) return emptyList()
        return matcher.cosmeticRules(pageUrl, profileId = "")
            .filter { rule -> rule.group == BundledPetalRuleGroups.Ads }
            .mapNotNull(PetalRule::cosmeticSelector)
    }

    companion object {
        val Empty = BundledPetalRules(emptyList())

        fun parse(text: String): BundledPetalRules {
            val preview = PetalRuleFormat.parse(text)
            require(preview.isApplicable) { "Invalid bundled Petal Rules" }
            require(preview.rules.all(::isSupportedBundledRule)) {
                "Unsupported bundled Petal Rule"
            }
            require(preview.rules.map(PetalRule::id).distinct().size == preview.rules.size) {
                "Duplicate bundled Petal Rule id"
            }
            return BundledPetalRules(preview.rules)
        }

        fun parseOrEmpty(text: String): BundledPetalRules =
            runCatching { parse(text) }.getOrDefault(Empty)

        private fun isSupportedBundledRule(rule: PetalRule): Boolean =
            rule.active && rule.profileId == null && when (rule.group) {
                BundledPetalRuleGroups.Ads -> true
                BundledPetalRuleGroups.Cookies ->
                    rule.action == PetalRuleAction.Cosmetic &&
                        rule.kind == PetalRuleKind.CosmeticCss
                else -> false
            }
    }
}
