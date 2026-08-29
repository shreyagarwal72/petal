/*
 * PetalReaderScreen.kt
 * ─────────────────────────────────────────────────────────────────────────
 * Material 3 Expressive Distraction-Free Reader Mode Screen for Petal Browser.
 * Features:
 * 1. Clean extracted article title, byline, reading time estimate, and body
 * 2. Customizable Reader Themes: Light (Paper), Sepia, Dark (Slate), AMOLED (Pure Black)
 * 3. Customizable Font Size, Line Spacing, and Font Family (Sans, Serif, Monospace)
 * 4. Smooth Predictive Back animation and background blur integration
 */

package com.petal.browser.compose.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import coil.compose.AsyncImage
import com.petal.browser.predictive.PetalPredictiveBackSurface
import com.petal.browser.predictive.PetalScreenWrapper
import com.petal.browser.ui.components.ExpressiveHeader
import com.petal.browser.ui.components.HeaderActionIcon
import com.petal.browser.ui.components.PetalSlider

enum class ReaderTheme(val title: String, val bg: Color, val text: Color, val surface: Color) {
    SYSTEM("System", Color.Unspecified, Color.Unspecified, Color.Unspecified),
    LIGHT("Paper", Color(0xFFFAF8F5), Color(0xFF1F1F1F), Color(0xFFF0ECE1)),
    SEPIA("Sepia", Color(0xFFF4ECD8), Color(0xFF5B4636), Color(0xFFE8DCBF)),
    DARK("Slate", Color(0xFF1E2124), Color(0xFFE1E2E5), Color(0xFF2C3036)),
    AMOLED("AMOLED", Color(0xFF000000), Color(0xFFE8EAED), Color(0xFF121212))
}

enum class ReaderFont(val title: String, val family: FontFamily) {
    SYSTEM("System", FontFamily.Default),
    SERIF("Serif", FontFamily.Serif),
    SANS("Sans", FontFamily.SansSerif),
    MONO("Mono", FontFamily.Monospace)
}

data class ReaderArticleData(
    val title: String,
    val author: String,
    val domain: String,
    val leadImageUrl: String,
    val contentText: String,
    val wordCount: Int = contentText.split("\\s+".toRegex()).size
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalReaderScreen(
    backgroundSnapshot: ImageBitmap? = null,
    article: ReaderArticleData,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sp = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    var selectedTheme by remember {
        val themeName = sp.getString("sp_reader_theme", "SYSTEM") ?: "SYSTEM"
        mutableStateOf(try { ReaderTheme.valueOf(themeName) } catch (e: Exception) { ReaderTheme.SYSTEM })
    }

    var selectedFont by remember {
        val fontName = sp.getString("sp_reader_font", "SERIF") ?: "SERIF"
        mutableStateOf(try { ReaderFont.valueOf(fontName) } catch (e: Exception) { ReaderFont.SERIF })
    }

    var fontSizeSp by remember {
        mutableFloatStateOf(sp.getFloat("sp_reader_font_size", 18f))
    }

    var lineHeightMult by remember {
        mutableFloatStateOf(sp.getFloat("sp_reader_line_height", 1.6f))
    }

    var showAppearanceSheet by remember { mutableStateOf(false) }

    val containerBg = when (selectedTheme) {
        ReaderTheme.SYSTEM -> MaterialTheme.colorScheme.background
        else -> selectedTheme.bg
    }

    val contentTextColor = when (selectedTheme) {
        ReaderTheme.SYSTEM -> MaterialTheme.colorScheme.onBackground
        else -> selectedTheme.text
    }

    val subtitleTextColor = when (selectedTheme) {
        ReaderTheme.SYSTEM -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> selectedTheme.text.copy(alpha = 0.7f)
    }

    PetalPredictiveBackSurface(
        enabled = true,
        onBack = onBack,
    ) {
        PetalScreenWrapper(backgroundSnapshot = backgroundSnapshot) {
            Scaffold(
                containerColor = containerBg,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        ExpressiveHeader(
                            title = "Reading Mode",
                            subtitle = "${(article.wordCount / 200).coerceAtLeast(1)} min read • ${article.domain}",
                            onBack = onBack,
                            actions = {
                                HeaderActionIcon(
                                    icon = Icons.Rounded.FormatSize,
                                    contentDescription = "Format Appearance",
                                    onClick = { showAppearanceSheet = true }
                                )
                            }
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            // Article Title
                            Text(
                                text = article.title,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = selectedFont.family,
                                    fontSize = (fontSizeSp + 8).sp,
                                    lineHeight = ((fontSizeSp + 8) * 1.3f).sp
                                ),
                                color = contentTextColor
                            )

                            Spacer(Modifier.height(10.dp))

                            // Author and Domain metadata
                            if (article.author.isNotBlank() || article.domain.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PersonOutline,
                                        contentDescription = null,
                                        tint = subtitleTextColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = listOf(article.author, article.domain)
                                            .filter { it.isNotBlank() }
                                            .joinToString(" • "),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontStyle = FontStyle.Italic
                                        ),
                                        color = subtitleTextColor
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Lead Image
                            if (article.leadImageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = article.leadImageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 260.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                )
                                Spacer(Modifier.height(20.dp))
                            }

                            HorizontalDivider(
                                color = subtitleTextColor.copy(alpha = 0.2f),
                                thickness = 1.dp
                            )

                            Spacer(Modifier.height(20.dp))

                            // Article Body Paragraphs
                            val paragraphs = article.contentText.split("\n\n+".toRegex())
                            paragraphs.forEach { paragraph ->
                                val clean = paragraph.trim()
                                if (clean.isNotBlank()) {
                                    Text(
                                        text = clean,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = selectedFont.family,
                                            fontSize = fontSizeSp.sp,
                                            lineHeight = (fontSizeSp * lineHeightMult).sp
                                        ),
                                        color = contentTextColor
                                    )
                                    Spacer(Modifier.height(16.dp))
                                }
                            }

                            Spacer(Modifier.height(48.dp))
                        }
                    }

                    // Appearance Customization Modal Sheet
                    if (showAppearanceSheet) {
                        ModalBottomSheet(
                            onDismissRequest = { showAppearanceSheet = false },
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp, end = 24.dp, bottom = 36.dp, top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                Text(
                                    text = "Reader Appearance",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                // Theme Selector
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Theme", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ReaderTheme.values().forEach { theme ->
                                            val isSelected = selectedTheme == theme
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = if (theme == ReaderTheme.SYSTEM) MaterialTheme.colorScheme.surfaceContainerHighest else theme.bg,
                                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp)
                                                    .clickable {
                                                        selectedTheme = theme
                                                        sp.edit().putString("sp_reader_theme", theme.name).apply()
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = theme.title,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = if (theme == ReaderTheme.SYSTEM) MaterialTheme.colorScheme.onSurface else theme.text
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Font Family Selector
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Font Family", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ReaderFont.values().forEach { font ->
                                            FilterChip(
                                                selected = selectedFont == font,
                                                onClick = {
                                                    selectedFont = font
                                                    sp.edit().putString("sp_reader_font", font.name).apply()
                                                },
                                                label = { Text(font.title, fontFamily = font.family) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                // Font Size Slider
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Font Size", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                        Text("${fontSizeSp.toInt()} sp", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                    }
                                    PetalSlider(
                                        value = fontSizeSp,
                                        onValueChange = {
                                            fontSizeSp = it
                                            sp.edit().putFloat("sp_reader_font_size", it).apply()
                                        },
                                        valueRange = 14f..28f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // Line Height Slider
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Line Spacing", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                        Text(String.format("%.1fx", lineHeightMult), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                    }
                                    PetalSlider(
                                        value = lineHeightMult,
                                        onValueChange = {
                                            lineHeightMult = it
                                            sp.edit().putFloat("sp_reader_line_height", it).apply()
                                        },
                                        valueRange = 1.2f..2.2f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
