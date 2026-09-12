package com.petal.browser.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.unit.BrowserUnit
import java.util.regex.Pattern

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    data class BulletListItem(val text: String) : MarkdownBlock()
    data class NumberedListItem(val number: String, val text: String) : MarkdownBlock()
    object HorizontalRule : MarkdownBlock()
}

object MarkdownParser {

    fun parse(markdown: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = markdown.replace("\r\n", "\n").replace("\r", "\n").split("\n")

        var i = 0
        val total = lines.size

        while (i < total) {
            val line = lines[i]

            // Fenced code block check ```
            if (line.trimStart().startsWith("```")) {
                val lang = line.trimStart().removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < total && !lines[i].trimStart().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < total && lines[i].trimStart().startsWith("```")) {
                    i++ // consume closing ```
                }
                blocks.add(MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n")))
                continue
            }

            val trimmed = line.trim()

            // Horizontal rule
            if (trimmed.matches(Regex("^(---|>\\s*---|\\*\\*\\*|___)$"))) {
                blocks.add(MarkdownBlock.HorizontalRule)
                i++
                continue
            }

            // Headings (#, ##, ###, etc.)
            if (trimmed.startsWith("#")) {
                var level = 0
                while (level < trimmed.length && trimmed[level] == '#') {
                    level++
                }
                if (level in 1..6 && level < trimmed.length && trimmed[level] == ' ') {
                    val headingText = trimmed.substring(level + 1).trim()
                    blocks.add(MarkdownBlock.Heading(level, headingText))
                    i++
                    continue
                }
            }

            // Blockquote >
            if (trimmed.startsWith(">")) {
                val quoteText = trimmed.removePrefix(">").trim()
                blocks.add(MarkdownBlock.Blockquote(quoteText))
                i++
                continue
            }

            // Bullet list item (- , * , + )
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
                val itemText = trimmed.substring(2).trim()
                blocks.add(MarkdownBlock.BulletListItem(itemText))
                i++
                continue
            }

            // Numbered list item (1. , 2. )
            val numMatch = Regex("^(\\d+)\\.\\s+(.+)").find(trimmed)
            if (numMatch != null) {
                val number = numMatch.groupValues[1]
                val itemText = numMatch.groupValues[2]
                blocks.add(MarkdownBlock.NumberedListItem(number, itemText))
                i++
                continue
            }

            // Empty line
            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // Otherwise, combine contiguous paragraph lines
            val paraLines = mutableListOf<String>()
            while (i < total) {
                val pLine = lines[i]
                val pTrim = pLine.trim()
                if (pTrim.isEmpty() ||
                    pTrim.startsWith("```") ||
                    pTrim.startsWith("#") ||
                    pTrim.startsWith(">") ||
                    pTrim.startsWith("- ") ||
                    pTrim.startsWith("* ") ||
                    pTrim.startsWith("+ ") ||
                    pTrim.matches(Regex("^(\\d+)\\.\\s+(.+)")) ||
                    pTrim.matches(Regex("^(---|\\*\\*\\*|___)$"))
                ) {
                    break
                }
                paraLines.add(pLine)
                i++
            }
            if (paraLines.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString("\n")))
            }
        }

        return blocks
    }
}

@Composable
fun PetalMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val fontSize = when (block.level) {
                        1 -> 22.sp
                        2 -> 19.sp
                        3 -> 17.sp
                        else -> 15.sp
                    }
                    val fontWeight = if (block.level <= 2) FontWeight.Bold else FontWeight.SemiBold
                    val headingStyle = MaterialTheme.typography.titleMedium.copy(
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    RenderInlineMarkdownText(
                        text = block.text,
                        context = context,
                        textStyle = headingStyle,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    RenderInlineMarkdownText(block.text, context)
                }

                is MarkdownBlock.CodeBlock -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Code,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = block.language.ifBlank { "code" },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Code", block.code))
                                        com.petal.browser.view.NinjaToast.show(context, "Code copied")
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Code", modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = block.code,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            RenderInlineMarkdownText(
                                text = block.text,
                                context = context,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 22.sp
                                ),
                                customTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is MarkdownBlock.BulletListItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "•",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            RenderInlineMarkdownText(block.text, context)
                        }
                    }
                }

                is MarkdownBlock.NumberedListItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "${block.number}.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            RenderInlineMarkdownText(block.text, context)
                        }
                    }
                }

                is MarkdownBlock.HorizontalRule -> {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderInlineMarkdownText(
    text: String,
    context: Context,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle? = null,
    customTextColor: Color? = null
) {
    val textColor = customTextColor ?: MaterialTheme.colorScheme.onSurface
    val linkColor = MaterialTheme.colorScheme.primary
    val annotatedString = remember(text, textColor, linkColor) {
        buildInlineMarkdown(text, textColor, linkColor)
    }

    val finalStyle = textStyle ?: MaterialTheme.typography.bodyMedium.copy(
        color = textColor,
        lineHeight = 22.sp
    )

    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = finalStyle,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    try {
                        BrowserUnit.intentURL(context, Uri.parse(annotation.item))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
        }
    )
}

/**
 * Builds an AnnotatedString with inline formatting:
 * - **bold** / __bold__
 * - *italic* / _italic_
 * - ~~strikethrough~~
 * - `inline code`
 * - [link label](url)
 * - Autolink bare URLs: https://..., http://..., www....
 */
fun buildInlineMarkdown(
    rawText: String,
    textColor: Color,
    linkColor: Color
): AnnotatedString {
    val trailingPunctuationRegex = Pattern.compile("[.,;:!?]+$")

    return buildAnnotatedString {
        // Regex pattern to match inline tokens including bare URLs
        val tokenPattern = Pattern.compile(
            "(\\[([^\\]]+)\\]\\(([^\\)]+)\\))|" +               // Group 1,2,3: Link [label](url)
            "((?:https?://|www\\.)[^\\s<>()\\]\\[\"]+)|" +       // Group 4: Bare URLs (https://, http://, www.)
            "(\\*\\*(.*?)\\*\\*)|" +                             // Group 5,6: **bold**
            "(__(.*?)__)|" +                                     // Group 7,8: __bold__
            "(\\*(.*?)\\*)|" +                                   // Group 9,10: *italic*
            "(_(.*?)_)|" +                                       // Group 11,12: _italic_
            "(~~(.*?)~~)|" +                                     // Group 13,14: ~~strikethrough~~
            "(`(.*?)`)"                                          // Group 15,16: `code`
        )

        val matcher = tokenPattern.matcher(rawText)
        var lastIndex = 0

        while (matcher.find()) {
            val start = matcher.start()
            if (start < lastIndex) {
                continue
            }
            val end = matcher.end()

            if (start > lastIndex) {
                append(rawText.substring(lastIndex, start))
            }

            when {
                // Link [label](url)
                matcher.group(1) != null -> {
                    val label = matcher.group(2) ?: ""
                    val url = matcher.group(3) ?: ""
                    val destination = if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url
                    pushStringAnnotation(tag = "URL", annotation = destination)
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold)) {
                        append(label)
                    }
                    pop()
                    lastIndex = end
                }

                // Bare URLs (https://, http://, www.)
                matcher.group(4) != null -> {
                    val rawUrl = matcher.group(4) ?: ""
                    val punctMatcher = trailingPunctuationRegex.matcher(rawUrl)
                    val cleanUrl = if (punctMatcher.find()) {
                        rawUrl.substring(0, punctMatcher.start())
                    } else {
                        rawUrl
                    }
                    val trailingPunct = rawUrl.substring(cleanUrl.length)
                    val destination = if (cleanUrl.startsWith("www.", ignoreCase = true)) "https://$cleanUrl" else cleanUrl

                    pushStringAnnotation(tag = "URL", annotation = destination)
                    withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold)) {
                        append(cleanUrl)
                    }
                    pop()
                    lastIndex = end - trailingPunct.length
                }

                // **bold**
                matcher.group(5) != null -> {
                    val content = matcher.group(6) ?: ""
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(content)
                    }
                    lastIndex = end
                }

                // __bold__
                matcher.group(7) != null -> {
                    val content = matcher.group(8) ?: ""
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(content)
                    }
                    lastIndex = end
                }

                // *italic*
                matcher.group(9) != null -> {
                    val content = matcher.group(10) ?: ""
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(content)
                    }
                    lastIndex = end
                }

                // _italic_
                matcher.group(11) != null -> {
                    val content = matcher.group(12) ?: ""
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(content)
                    }
                    lastIndex = end
                }

                // ~~strikethrough~~
                matcher.group(13) != null -> {
                    val content = matcher.group(14) ?: ""
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(content)
                    }
                    lastIndex = end
                }

                // `inline code`
                matcher.group(15) != null -> {
                    val content = matcher.group(16) ?: ""
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = linkColor,
                            background = linkColor.copy(alpha = 0.12f)
                        )
                    ) {
                        append(" $content ")
                    }
                    lastIndex = end
                }
            }
        }

        if (lastIndex < rawText.length) {
            append(rawText.substring(lastIndex))
        }
    }
}
