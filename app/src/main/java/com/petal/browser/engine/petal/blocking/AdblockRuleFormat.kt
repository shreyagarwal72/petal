package com.petal.browser.engine.petal.blocking

import java.util.Locale

/**
 * Safe, intentionally small importer for ABP/EasyList/uBlock static-filter syntax.
 * Unsupported semantics are reported and skipped; they are never approximated.
 */
object PetalRuleImport {
    fun parse(text: String): PetalRulePreview {
        val contentLines = text.lineSequence()
            .map { it.trim().trimStart('\uFEFF') }
            .filter(String::isNotEmpty)
            .toList()
        return when {
            contentLines.isEmpty() -> PetalRuleFormat.parse(text)
            contentLines.any { it == PetalRuleFormat.HEADER } -> PetalRuleFormat.parse(text)
            contentLines.any { it.startsWith("rule\t") } -> PetalRuleFormat.parse(text)
            else -> AdblockRuleFormat.parse(text)
        }
    }
}

object PetalImportScope {
    fun apply(preview: PetalRulePreview, profileId: String?): PetalRulePreview = preview.copy(
        rules = preview.rules.map { rule -> rule.copy(profileId = profileId) },
    )

    fun isAllowed(profileId: String?, availableProfileIds: Collection<String>): Boolean =
        profileId == null || profileId in availableProfileIds
}

object AdblockRuleFormat {
    const val MAX_IMPORT_BYTES = 5 * 1_024 * 1_024
    const val MAX_IMPORT_LINES = 100_000
    const val MAX_LINE_BYTES = 8 * 1_024
    private const val MAX_SKIPPED_DETAILS = 20
    private const val GROUP = "Imported"
    private val whitespace = Regex("\\s+")

    fun parse(text: String): PetalRulePreview {
        if ('\u0000' in text) return failure("invalid-input", truncated = false)
        if (text.toByteArray(Charsets.UTF_8).size > MAX_IMPORT_BYTES) {
            return failure("size-limit", truncated = true)
        }
        val lines = text.lineSequence().toList()
        if (lines.size > MAX_IMPORT_LINES) return failure("line-limit", truncated = true)
        if (lines.any { it.toByteArray(Charsets.UTF_8).size > MAX_LINE_BYTES }) {
            return failure("line-limit", truncated = true)
        }

        val disabledByBadfilter = unconditionalLines(lines).mapNotNull(::badfilterTarget).toSet()
        val rules = ArrayList<PetalRule>()
        val semanticKeys = HashSet<String>()
        val skipped = ArrayList<PetalRuleLineError>()
        var skippedCount = 0
        var cosmeticCount = 0
        var limitExceeded = false
        var conditionalDepth = 0

        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim().trimStart('\uFEFF')
            if (line.startsWith("!#if")) {
                conditionalDepth++
                recordSkipped(skipped, index + 1, "unsupported-adblock")
                skippedCount++
                return@forEachIndexed
            }
            if (line.startsWith("!#endif")) {
                conditionalDepth = (conditionalDepth - 1).coerceAtLeast(0)
                return@forEachIndexed
            }
            if (conditionalDepth > 0) {
                if (line.isNotEmpty() && !line.startsWith("!#else")) {
                    recordSkipped(skipped, index + 1, "unsupported-adblock")
                    skippedCount++
                }
                return@forEachIndexed
            }
            if (badfilterTarget(line) != null || canonicalNetworkLine(line) in disabledByBadfilter) {
                return@forEachIndexed
            }
            when (val result = parseLine(line, index + 1)) {
                LineResult.Ignored -> Unit
                is LineResult.Skipped -> {
                    skippedCount++
                    recordSkipped(skipped, index + 1, result.reason)
                }
                is LineResult.Rules -> {
                    val unique = result.rules.filter { semanticKeys.add(semanticKey(it)) }
                    if (unique.isEmpty()) return@forEachIndexed
                    val newCosmeticCount = unique.count { it.kind == PetalRuleKind.CosmeticCss }
                    if (rules.size + unique.size > PetalRuleValidator.MAX_RULES ||
                        cosmeticCount + newCosmeticCount > PetalRuleValidator.MAX_COSMETIC_RULES
                    ) {
                        limitExceeded = true
                        unique.forEach { semanticKeys.remove(semanticKey(it)) }
                        skippedCount++
                        recordSkipped(skipped, index + 1, "rule-limit")
                    } else {
                        rules += unique
                        cosmeticCount += newCosmeticCount
                    }
                }
            }
        }

        // ABP exceptions always win over every matching block. Petal normally favors the most
        // specific host and pair rules, so normalize global exceptions into equivalent Petal
        // decisions instead of silently changing list semantics.
        val globalAllows = rules.filter {
            it.action == PetalRuleAction.Allow && it.kind == PetalRuleKind.RequestHost
        }
        rules.removeAll { block ->
            block.action == PetalRuleAction.Block && globalAllows.any { allow ->
                PetalHostCanonicalizer.matches(
                    block.requestHost.orEmpty(),
                    allow.requestHost.orEmpty(),
                )
            }
        }
        val pairAllows = ArrayList<PetalRule>()
        pairBlocks@ for (pairBlock in rules.filter {
            it.action == PetalRuleAction.Block && it.kind == PetalRuleKind.HostPair
        }) {
            for (allow in globalAllows) {
                val allowHost = allow.requestHost.orEmpty()
                val blockHost = pairBlock.requestHost.orEmpty()
                if (!PetalHostCanonicalizer.matches(allowHost, blockHost)) continue
                val candidate = newRule(
                    lineNumber = 0,
                    ordinal = pairAllows.size,
                    action = PetalRuleAction.Allow,
                    kind = PetalRuleKind.HostPair,
                    requestHost = allowHost,
                    firstPartyHost = pairBlock.firstPartyHost,
                )
                val key = semanticKey(candidate)
                if (key in semanticKeys) continue
                if (rules.size + pairAllows.size >= PetalRuleValidator.MAX_RULES) {
                    limitExceeded = true
                    break@pairBlocks
                }
                semanticKeys += key
                pairAllows += candidate
            }
        }
        if (!limitExceeded) {
            rules += pairAllows
        }

        val errors = buildList {
            if (rules.isEmpty()) add(PetalRuleLineError(0, "no-supported-rules"))
            if (limitExceeded) add(PetalRuleLineError(0, "rule-limit"))
        }
        return PetalRulePreview(
            rules = rules,
            errors = errors,
            format = PetalImportFormat.AdblockCompatible,
            skipped = skipped,
            skippedCount = skippedCount,
        )
    }

    private fun parseLine(line: String, lineNumber: Int): LineResult {
        if (line.isEmpty() || isMetadataHeader(line)) {
            return LineResult.Ignored
        }
        if (line.startsWith("!#include") || line.startsWith("!#")) {
            return LineResult.Skipped("unsupported-adblock")
        }
        if (line.startsWith('!')) return LineResult.Ignored
        if (line.startsWith('#')) return LineResult.Skipped("unsupported-adblock")
        return if (containsCosmeticMarker(line)) {
            parseCosmetic(line, lineNumber)
        } else {
            parseNetwork(line, lineNumber)
        }
    }

    private fun parseNetwork(line: String, lineNumber: Int): LineResult {
        parseHostsLine(line)?.let { requestHost ->
            return validatedRules(
                listOf(
                    newRule(
                        lineNumber,
                        0,
                        PetalRuleAction.Block,
                        PetalRuleKind.RequestHost,
                        requestHost,
                    ),
                ),
            )
        }
        val action = if (line.startsWith("@@")) PetalRuleAction.Allow else PetalRuleAction.Block
        val body = if (action == PetalRuleAction.Allow) line.removePrefix("@@") else line
        val optionIndex = body.indexOf('$')
        val pattern = if (optionIndex >= 0) body.substring(0, optionIndex) else body
        val options = if (optionIndex >= 0) body.substring(optionIndex + 1) else ""
        if (!pattern.startsWith("||") || !pattern.endsWith('^')) {
            return LineResult.Skipped("unsupported-adblock")
        }
        val requestHost = PetalHostCanonicalizer.canonicalHost(pattern.substring(2, pattern.length - 1))
            ?: return LineResult.Skipped("unsupported-adblock")
        if (PetalPublicSuffixRules.isPublicSuffix(requestHost)) {
            return LineResult.Skipped("unsupported-adblock")
        }

        if (options.isEmpty()) {
            return validatedRules(
                listOf(newRule(lineNumber, 0, action, PetalRuleKind.RequestHost, requestHost)),
            )
        }
        val optionParts = options.split(',')
        val domainParts = optionParts.filter {
            it.startsWith("domain=") || it.startsWith("from=")
        }
        val modifiers = optionParts - domainParts.toSet()
        if (domainParts.size != 1 || modifiers.any { it !in setOf("third-party", "3p") }) {
            return LineResult.Skipped("unsupported-adblock")
        }
        val domains = domainParts.single()
            .removePrefix("domain=")
            .removePrefix("from=")
            .split('|')
        if (domains.isEmpty() || domains.any { it.isBlank() || it.startsWith('~') || '*' in it }) {
            return LineResult.Skipped("unsupported-adblock")
        }
        val firstPartyHosts = domains.map(PetalHostCanonicalizer::canonicalHost)
        if (firstPartyHosts.any { it == null }) return LineResult.Skipped("unsupported-adblock")
        return validatedRules(
            firstPartyHosts.filterNotNull().mapIndexed { index, firstPartyHost ->
                newRule(
                    lineNumber = lineNumber,
                    ordinal = index,
                    action = action,
                    kind = PetalRuleKind.HostPair,
                    requestHost = requestHost,
                    firstPartyHost = firstPartyHost,
                )
            },
        )
    }

    private fun parseCosmetic(line: String, lineNumber: Int): LineResult {
        if (listOf("#@#", "#?#", "#$#", "#%#").any(line::contains)) {
            return LineResult.Skipped("unsupported-adblock")
        }
        val marker = line.indexOf("##")
        if (marker <= 0) return LineResult.Skipped("unsupported-adblock")
        val domainPart = line.substring(0, marker)
        val selector = line.substring(marker + 2).trim()
        if (selector.isEmpty() || hasExtendedCosmeticSyntax(selector)) {
            return LineResult.Skipped("unsupported-adblock")
        }
        val domains = domainPart.split(',')
        if (domains.any { it.isBlank() || it.startsWith('~') || '*' in it || '/' in it }) {
            return LineResult.Skipped("unsupported-adblock")
        }
        val origins = domains.map(PetalHostCanonicalizer::canonicalHost)
        if (origins.any { it == null }) return LineResult.Skipped("unsupported-adblock")
        return validatedRules(
            origins.filterNotNull().mapIndexed { index, origin ->
                PetalRule(
                    id = stableId(lineNumber, index, "css\u0000$origin\u0000$selector"),
                    action = PetalRuleAction.Cosmetic,
                    kind = PetalRuleKind.CosmeticCss,
                    firstPartyHost = origin,
                    cosmeticSelector = selector,
                    group = GROUP,
                    origin = PetalRuleOrigin.Import,
                )
            },
        )
    }

    private fun validatedRules(candidates: List<PetalRule>): LineResult {
        val validated = candidates.map { candidate ->
            (PetalRuleValidator.validate(candidate) as? PetalRuleValidation.Valid)?.rule
                ?: return LineResult.Skipped("unsupported-adblock")
        }
        return LineResult.Rules(validated)
    }

    private fun newRule(
        lineNumber: Int,
        ordinal: Int,
        action: PetalRuleAction,
        kind: PetalRuleKind,
        requestHost: String,
        firstPartyHost: String? = null,
    ): PetalRule {
        val key = listOf(action.name, kind.name, requestHost, firstPartyHost.orEmpty())
            .joinToString("\u0000")
        return PetalRule(
            id = stableId(lineNumber, ordinal, key),
            action = action,
            kind = kind,
            requestHost = requestHost,
            firstPartyHost = firstPartyHost,
            group = GROUP,
            origin = PetalRuleOrigin.Import,
        )
    }

    private fun stableId(lineNumber: Int, ordinal: Int, value: String): String =
        "adblock-$lineNumber-$ordinal-${value.hashCode().toUInt().toString(16)}"

    private fun semanticKey(rule: PetalRule): String = listOf(
        rule.action.name,
        rule.kind.name,
        rule.requestHost.orEmpty(),
        rule.firstPartyHost.orEmpty(),
        rule.cosmeticSelector.orEmpty(),
    ).joinToString("\u0000")

    private fun isMetadataHeader(line: String): Boolean =
        line.startsWith('[') && line.endsWith(']')

    private fun parseHostsLine(line: String): String? {
        val fields = line.split(whitespace).filter(String::isNotEmpty)
        if (fields.size != 2 || fields[0] !in setOf("0.0.0.0", "127.0.0.1")) return null
        return PetalHostCanonicalizer.canonicalHost(fields[1])
    }

    private fun badfilterTarget(rawLine: String): String? {
        val line = rawLine.trim().trimStart('\uFEFF')
        val optionIndex = line.indexOf('$')
        if (optionIndex < 0) return null
        val options = line.substring(optionIndex + 1).split(',')
        if ("badfilter" !in options) return null
        val retained = options.filterNot { it == "badfilter" }
        return canonicalNetworkLine(
            line.substring(0, optionIndex) + if (retained.isEmpty()) {
                ""
            } else {
                retained.joinToString(",", prefix = "\$")
            },
        )
    }

    private fun unconditionalLines(lines: List<String>): Sequence<String> = sequence {
        var conditionalDepth = 0
        lines.forEach { rawLine ->
            val line = rawLine.trim().trimStart('\uFEFF')
            when {
                line.startsWith("!#if") -> conditionalDepth++
                line.startsWith("!#endif") -> {
                    conditionalDepth = (conditionalDepth - 1).coerceAtLeast(0)
                }
                conditionalDepth == 0 -> yield(line)
            }
        }
    }

    private fun canonicalNetworkLine(line: String): String {
        val optionIndex = line.indexOf('$')
        if (optionIndex < 0) return line
        return line.substring(0, optionIndex) + line.substring(optionIndex + 1)
            .split(',')
            .sorted()
            .joinToString(",", prefix = "\$")
    }

    private fun recordSkipped(
        skipped: MutableList<PetalRuleLineError>,
        line: Int,
        reason: String,
    ) {
        if (skipped.size < MAX_SKIPPED_DETAILS) skipped += PetalRuleLineError(line, reason)
    }

    private fun containsCosmeticMarker(line: String): Boolean =
        listOf("##", "#@#", "#?#", "#$#", "#%#").any(line::contains)

    private fun hasExtendedCosmeticSyntax(selector: String): Boolean {
        val lower = selector.lowercase(Locale.ROOT)
        return selector.startsWith('^') || listOf(
            "+js(", ":has-text(", ":matches-css(", ":matches-css-before(",
            ":matches-css-after(", ":matches-attr(", ":matches-path(",
            ":min-text-length(", ":others(", ":remove(", ":remove-attr(",
            ":remove-class(", ":style(", ":upward(", ":watch-attr(", ":xpath(",
            ":if(", ":if-not(", ":-abp-",
        ).any(lower::contains)
    }

    private fun failure(reason: String, truncated: Boolean): PetalRulePreview = PetalRulePreview(
        rules = emptyList(),
        errors = listOf(PetalRuleLineError(0, reason)),
        truncated = truncated,
        format = PetalImportFormat.AdblockCompatible,
    )

    private sealed interface LineResult {
        data object Ignored : LineResult
        data class Rules(val rules: List<PetalRule>) : LineResult
        data class Skipped(val reason: String) : LineResult
    }
}
