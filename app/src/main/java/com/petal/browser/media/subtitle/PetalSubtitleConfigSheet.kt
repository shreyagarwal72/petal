package com.petal.browser.media.subtitle

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.containment.liquidGlassChrome
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalSubtitleConfigSheet(
    isLiquidGlass: Boolean,
    subtitleDelayMs: Long,
    subtitleFontSizeSp: Float,
    subtitleTextColor: Color,
    onSubtitleDelayChange: (Long) -> Unit,
    onSubtitleFontSizeChange: (Float) -> Unit,
    onSubtitleTextColorChange: (Color) -> Unit,
    onSubtitlesLoaded: (List<SubtitleCue>, String) -> Unit,
    onClearSubtitles: () -> Unit,
    hasActiveSubtitles: Boolean,
    activeSubtitleName: String?,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isImporting by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isImporting = true
            scope.launch {
                val cues = PetalSubtitleEngine.parseFromUri(context, uri)
                isImporting = false
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Subtitles"
                onSubtitlesLoaded(cues, fileName)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = if (isLiquidGlass) Color(0xFF14141E).copy(alpha = 0.92f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Subtitles,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Subtitles & Sync",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (hasActiveSubtitles) {
                    TextButton(onClick = {
                        onClearSubtitles()
                        PetalHapticEngine.getInstance(context).playClick(context)
                    }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Subtitle source status card
            val statusShape = RoundedCornerShape(16.dp)
            Surface(
                shape = statusShape,
                color = Color.White.copy(alpha = 0.05f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasActiveSubtitles) (activeSubtitleName ?: "Active Subtitles") else "No Subtitle Loaded",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (hasActiveSubtitles) "Synchronized with playback" else "Select .srt or .vtt file to load",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = {
                            filePickerLauncher.launch(arrayOf("text/*", "application/x-subrip", "*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import")
                    }
                }
            }

            // Subtitle Sync Offset Slider (-10.0s to +10.0s)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.ui_subtitle_delay),
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.1f", subtitleDelayMs / 1000f)}s",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Slider(
                    value = subtitleDelayMs.toFloat(),
                    onValueChange = { onSubtitleDelayChange(it.toLong()) },
                    valueRange = -10000f..10000f,
                    steps = 39,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Subtitle Font Size Slider (14sp to 32sp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Subtitle Font Size",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${subtitleFontSizeSp.toInt()} sp",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Slider(
                    value = subtitleFontSizeSp,
                    onValueChange = { onSubtitleFontSizeChange(it) },
                    valueRange = 14f..32f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Subtitle Text Color Chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Text Color",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        Color.White to "White",
                        Color(0xFFFFEB3B) to "Yellow",
                        Color(0xFF00E5FF) to "Cyan",
                        Color(0xFF76FF03) to "Green"
                    ).forEach { (color, _) ->
                        val isSelected = subtitleTextColor == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable { onSubtitleTextColorChange(color) }
                        )
                    }
                }
            }
        }
    }
}
