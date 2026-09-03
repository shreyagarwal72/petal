package com.petal.browser.engine.petal.blocking

import java.net.URI
import java.util.Base64
import java.util.Locale

data class PetalRuleLineError(val line: Int, val message: String)

enum class PetalImportFormat { PetalRulesV1, AdblockCompatible }

object PetalFilterPresets {
    const val UBLOCK_ORIGIN_BASE_URL =
        "https://ublockorigin.github.io/uAssets/filters/filters.txt"

    fun groupFor(sourceUrl: String): String? =
        "uBlock Origin".takeIf { sourceUrl == UBLOCK_ORIGIN_BASE_URL }
}

data class PetalRulePreview(
    val rules: List<PetalRule>,
    val errors: List<PetalRuleLineError>,
    val truncated: Boolean = false,
    val format: PetalImportFormat = PetalImportFormat.PetalRulesV1,
    val skipped: List<PetalRuleLineError> = emptyList(),
    val skippedCount: Int = skipped.size,
) {
    val isApplicable: Boolean get() = errors.isEmpty() && !truncated
}

object PetalRuleFormat {
    const val HEADER = "petal-rules:1"
    const val MAX_IMPORT_BYTES = 512 * 1_024
    const val MAX_IMPORT_LINES = 8_192

    fun parse(text: String, origin: PetalRuleOrigin = PetalRuleOrigin.Import): PetalRulePreview {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_IMPORT_BYTES) {
            return PetalRulePreview(emptyList(), listOf(PetalRuleLineError(0, "size-limit")), true)
        }
        val lines = text.lineSequence()
            .map { it.trim().trimStart('\uFEFF') }
            .toList()
        if (lines.size > MAX_IMPORT_LINES) {
            return PetalRulePreview(emptyList(), listOf(PetalRuleLineError(0, "line-limit")), true)
        }
        val firstContent = lines.indexOfFirst { it.isNotEmpty() && !it.startsWith('#') }
        if (firstContent < 0 || lines[firstContent] != HEADER) {
            return PetalRulePreview(emptyList(), listOf(PetalRuleLineError(1, "missing-header")))
        }
        val rules = mutableListOf<PetalRule>()
        val errors = mutableListOf<PetalRuleLineError>()
        lines.forEachIndexed { index, raw ->
            val line = raw
            if (index <= firstContent || line.isEmpty() || line.startsWith('#')) return@forEachIndexed
            val parsed = parseLine(line, origin)
            when (parsed) {
                is PetalRuleValidation.Valid -> rules += parsed.rule
                is PetalRuleValidation.Invalid -> errors += PetalRuleLineError(
                    index + 1,
                    parsed.reason.name,
                )
                null -> errors += PetalRuleLineError(index + 1, "syntax")
            }
        }
        if (rules.size > PetalRuleValidator.MAX_RULES) {
            errors += PetalRuleLineError(0, "rule-limit")
        }
        return PetalRulePreview(rules.take(PetalRuleValidator.MAX_RULES), errors)
    }

    fun export(rules: Iterable<PetalRule>): String = buildString {
        appendLine(HEADER)
        appendLine("# Petal Browser format; not ABP/uBlock compatible")
        PetalRuleValidator.normalizeAll(rules).sortedBy(PetalRule::id).forEach { rule ->
            appendLine(encode(rule))
        }
    }.trimEnd()

    private fun parseLine(line: String, origin: PetalRuleOrigin): PetalRuleValidation? {
        val fields = line.split('\t')
        if (fields.size !in 4..9 || fields[0] != "rule") return null
        val action = when (fields[1]) {
            "block" -> PetalRuleAction.Block
            "allow" -> PetalRuleAction.Allow
            "css" -> PetalRuleAction.Cosmetic
            else -> return null
        }
        val kind = when (fields[2]) {
            "host" -> PetalRuleKind.RequestHost
            "pair" -> PetalRuleKind.HostPair
            "origin" -> PetalRuleKind.CosmeticCss
            else -> return null
        }
        val target = fields[3]
        val requestHost = when (kind) {
            PetalRuleKind.RequestHost -> target
            PetalRuleKind.HostPair -> target.substringAfter("->", "").takeIf(String::isNotEmpty)
            PetalRuleKind.CosmeticCss -> null
        }
        val firstParty = when (kind) {
            PetalRuleKind.RequestHost -> null
            PetalRuleKind.HostPair -> target.substringBefore("->", "").takeIf(String::isNotEmpty)
            PetalRuleKind.CosmeticCss -> target
        }
        val selector = fields.getOrNull(4)?.takeIf(String::isNotEmpty)?.let { encoded ->
            runCatching { String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8) }.getOrNull()
        }
        val id = fields.getOrNull(5)?.takeIf(String::isNotEmpty)
            ?: "import-${line.hashCode().toUInt().toString(16)}"
        val profile = fields.getOrNull(6)?.takeIf { it.isNotEmpty() && it != "*" }
        val group = fields.getOrNull(7)?.takeIf(String::isNotEmpty) ?: "Imported"
        val active = fields.getOrNull(8)?.let { it == "1" } ?: true
        return PetalRuleValidator.validate(
            PetalRule(
                id = id,
                action = action,
                kind = kind,
                requestHost = requestHost,
                firstPartyHost = firstParty,
                cosmeticSelector = selector,
                profileId = profile,
                group = group,
                origin = origin,
                active = active,
            ),
        )
    }

    private fun encode(rule: PetalRule): String {
        val action = when (rule.action) {
            PetalRuleAction.Block -> "block"
            PetalRuleAction.Allow -> "allow"
            PetalRuleAction.Cosmetic -> "css"
        }
        val kind = when (rule.kind) {
            PetalRuleKind.RequestHost -> "host"
            PetalRuleKind.HostPair -> "pair"
            PetalRuleKind.CosmeticCss -> "origin"
        }
        val target = when (rule.kind) {
            PetalRuleKind.RequestHost -> rule.requestHost.orEmpty()
            PetalRuleKind.HostPair -> "${rule.firstPartyHost}->${rule.requestHost}"
            PetalRuleKind.CosmeticCss -> rule.firstPartyHost.orEmpty()
        }
        val selector = rule.cosmeticSelector?.let {
            Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8))
        }.orEmpty()
        return listOf(
            "rule", action, kind, target, selector, rule.id,
            rule.profileId ?: "*", rule.group, if (rule.active) "1" else "0",
        ).joinToString("\t")
    }
}

data class PetalSubscriptionDiff(
    val added: List<PetalRule>,
    val removed: List<PetalRule>,
    val unchanged: List<PetalRule>,
)

object PetalSubscriptionRules {
    const val MAX_BYTES = AdblockRuleFormat.MAX_IMPORT_BYTES
    const val MAX_LINES = AdblockRuleFormat.MAX_IMPORT_LINES
    const val CONNECT_TIMEOUT_MS = 8_000
    const val READ_TIMEOUT_MS = 8_000

    fun validatePreview(sourceUrl: String, body: String): PetalRulePreview {
        if (!PetalRuleValidator.isSafeHttpsUrl(sourceUrl)) {
            return PetalRulePreview(emptyList(), listOf(PetalRuleLineError(0, "https-required")))
        }
        val skippedCss = ArrayList<PetalRuleLineError>()
        var skippedCssCount = 0
        val networkOnlyBody = body.lineSequence().mapIndexed { index, rawLine ->
            val line = rawLine.trim().trimStart('\uFEFF')
            val isPetalCss = line.startsWith("rule\tcss\t")
            val isAdblockCosmetic = !line.startsWith('!') && listOf(
                "##", "#@#", "#?#", "#$#", "#%#",
            ).any(line::contains)
            if (isPetalCss || isAdblockCosmetic) {
                skippedCssCount++
                if (skippedCss.size < 20) {
                    skippedCss += PetalRuleLineError(index + 1, "subscription-css-forbidden")
                }
                ""
            } else {
                rawLine
            }
        }.joinToString("\n")
        val preview = PetalRuleImport.parse(networkOnlyBody)
        if (!preview.isApplicable) {
            return preview.copy(
                skipped = (skippedCss + preview.skipped).take(20),
                skippedCount = skippedCssCount + preview.skippedCount,
            )
        }
        val forbidden = preview.rules.filter { it.kind == PetalRuleKind.CosmeticCss }
        val allowed = preview.rules - forbidden.toSet()
        val errors = buildList {
            if (allowed.isEmpty()) add(PetalRuleLineError(0, "no-supported-rules"))
        }
        return preview.copy(
            rules = allowed,
            errors = errors,
            skipped = buildList<PetalRuleLineError> {
                addAll(skippedCss)
                addAll(preview.skipped)
                if (forbidden.isNotEmpty() && size < 20) {
                    add(PetalRuleLineError(0, "subscription-css-forbidden"))
                }
            }.take(20),
            skippedCount = skippedCssCount + preview.skippedCount + forbidden.size,
        )
    }

    fun diff(previous: Iterable<PetalRule>, next: Iterable<PetalRule>): PetalSubscriptionDiff {
        val before = previous.associateBy(::semanticKey)
        val after = next.associateBy(::semanticKey)
        return PetalSubscriptionDiff(
            added = after.filterKeys { it !in before }.values.sortedBy(PetalRule::id),
            removed = before.filterKeys { it !in after }.values.sortedBy(PetalRule::id),
            unchanged = after.filterKeys { it in before }.values.sortedBy(PetalRule::id),
        )
    }

    fun isSameSourceScope(rule: PetalRule, sourceUrl: String, profileId: String?): Boolean =
        rule.sourceUrl == sourceUrl && rule.profileId == profileId

    fun storageKey(rule: PetalRule): String = listOf(
        rule.action.name,
        rule.kind.name,
        rule.requestHost.orEmpty(),
        rule.firstPartyHost.orEmpty(),
        rule.cosmeticSelector.orEmpty(),
        rule.profileId.orEmpty(),
        if (rule.origin == PetalRuleOrigin.Subscription) rule.origin.name else "",
        if (rule.origin == PetalRuleOrigin.Subscription) rule.sourceUrl.orEmpty() else "",
    ).joinToString("\u0000")

    private fun semanticKey(rule: PetalRule): String = listOf(
        rule.action.name,
        rule.kind.name,
        rule.requestHost.orEmpty(),
        rule.firstPartyHost.orEmpty(),
        rule.cosmeticSelector.orEmpty(),
        rule.profileId.orEmpty(),
        rule.active.toString(),
    ).joinToString("\u0000")

    fun isHttpsSource(sourceUrl: String): Boolean = runCatching {
        val uri = URI(sourceUrl)
        uri.scheme.equals("https", true) && uri.userInfo == null && uri.fragment == null
    }.getOrDefault(false)
}

object PetalCosmeticScript {
    const val cleanupScript =
        "(function c(w){try{w.document.querySelectorAll('style[data-petal-filter]')" +
            ".forEach(function(s){s.remove()});Array.from(w.frames).forEach(c)}catch(e){}})(window)"

    fun create(selectors: List<String>, pausedHosts: Collection<String> = emptyList()): String {
        val encodedSelectors = selectors.asSequence()
            .distinct()
            .map { selector ->
                Base64.getEncoder().encodeToString(selector.toByteArray(Charsets.UTF_8))
            }
            .joinToString(",") { encoded -> "'$encoded'" }
        if (encodedSelectors.isEmpty()) return ""
        val pauses = pausedHosts.asSequence()
            .mapNotNull(PetalHostCanonicalizer::canonicalHost)
            .distinct()
            .take(64)
            .joinToString(",") { "'$it'" }
        return "(function(){for(var w=self;w!==top;){w=w.parent;try{void w.document}" +
            "catch(e){return}}var h=location.hostname.toLowerCase();" +
            "if([$pauses].some(function(x){return h===x||h.endsWith('.'+x)}))return;" +
            "var d=function(v){var b=atob(v);var a=Uint8Array.from(b,function(c){" +
            "return c.charCodeAt(0)});return new TextDecoder('utf-8').decode(a)};" +
            "var s=document.createElement('style');" +
            "s.dataset.petalFilter='1';(document.head||document.documentElement).appendChild(s);" +
            "[$encodedSelectors].forEach(function(v){try{s.sheet.insertRule(" +
            "d(v)+'{display:none!important}',s.sheet.cssRules.length)}catch(e){}});" +
            "if(!s.sheet.cssRules.length)s.remove()})()"
    }

    fun createScoped(
        rules: Collection<PetalRule>,
        pausedHosts: Collection<String> = emptyList(),
    ): String {
        val encodedRules = PetalRuleValidator.normalizeAll(rules).asSequence()
            .filter { rule ->
                rule.active && rule.action == PetalRuleAction.Cosmetic &&
                    rule.kind == PetalRuleKind.CosmeticCss
            }
            .mapNotNull { rule ->
                val host = rule.firstPartyHost ?: return@mapNotNull null
                val selector = rule.cosmeticSelector ?: return@mapNotNull null
                val encodedSelector = Base64.getEncoder()
                    .encodeToString(selector.toByteArray(Charsets.UTF_8))
                "{host:'$host',selector:'$encodedSelector'}"
            }
            .joinToString(prefix = "[", postfix = "]")
        if (encodedRules == "[]") return ""
        val pauses = pausedHosts.asSequence()
            .mapNotNull(PetalHostCanonicalizer::canonicalHost)
            .distinct()
            .take(64)
            .joinToString(",") { "'$it'" }
        return """
            (function(){
              for(var frame=self;frame!==top;){
                frame=frame.parent;
                try { void frame.document; }
                catch(ignored) { return; }
              }
              var h=location.hostname.toLowerCase().replace(/\.$/,'');
              if([$pauses].some(function(x){return h===x||h.endsWith('.'+x)}))return;
              var decode=function(value){
                var binary=atob(value);
                var bytes=Uint8Array.from(binary,function(character){return character.charCodeAt(0)});
                return new TextDecoder('utf-8').decode(bytes);
              };
              var selectors=$encodedRules
                .filter(function(rule){return h===rule.host||h.endsWith('.'+rule.host)})
                .map(function(rule){return decode(rule.selector)});
              if(!selectors.length)return;
              var s=document.createElement('style');
              s.dataset.petalFilter='1';
              (document.head||document.documentElement).appendChild(s);
              selectors.forEach(function(selector){
                try {
                  s.sheet.insertRule(
                    selector+'{display:none!important}',
                    s.sheet.cssRules.length
                  );
                } catch (ignored) {}
              });
              if(!s.sheet.cssRules.length)s.remove();
            })()
        """.trimIndent()
    }
}

internal object PetalDocumentStartOrigin {
    fun fromUrl(pageUrl: String?): String? {
        val uri = runCatching { URI(pageUrl ?: return null) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)?.takeIf { it == "http" || it == "https" }
            ?: return null
        val host = PetalHostCanonicalizer.canonicalHost(uri.host) ?: return null
        val port = uri.port.takeIf { it >= 0 }?.let { value -> ":$value" }.orEmpty()
        return "$scheme://$host$port"
    }
}
