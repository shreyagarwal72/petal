package com.petal.browser.account

import com.petal.browser.ui.theme.ExpressiveButtonShape
import android.net.Uri
import android.preference.PreferenceManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import com.petal.browser.ui.containment.petalConnectedShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.*
import com.petal.browser.ui.kit.TextField
import com.petal.browser.ui.kit.FilledTonalButton
import com.petal.browser.ui.kit.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import com.petal.browser.ui.theme.isDynamicColorSupported
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.containment.petalGroupPositionFor
import com.petal.browser.ui.containment.petalGroupShape
import com.petal.browser.ui.components.PetalAboutDeveloperBridge
import com.petal.browser.ui.components.PetalThemedSnackbarHost
import com.petal.browser.ui.components.bouncyClickable
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.ui.theme.defaultPaletteId
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.petal.browser.R

@Composable
fun ProfileAvatarDisplay(
    profile: PetalUserProfile,
    sizeDp: Int = 72,
    modifier: Modifier = Modifier
) {
    val size = sizeDp.dp
    val context = LocalContext.current

    Box(
        modifier = modifier
            .size(size)
            .clip(ExpressiveButtonShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .then(
                PetalAccountRepository.parseAccent(profile.accentColorHex)?.let {
                    Modifier.border(2.dp, it, ExpressiveButtonShape)
                } ?: Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            profile.avatarType == AvatarType.GALLERY_URI -> {
                val avatarFile = remember(profile.customAvatarUri, profile.avatarTimestamp) {
                    val file = java.io.File(context.filesDir, "petal_user_avatar.png")
                    if (file.exists() && file.length() > 0) file else null
                }

                val imageSource: Any? = avatarFile ?: profile.customAvatarUri

                if (imageSource != null) {
                    val imageModel = remember(imageSource, profile.avatarTimestamp, avatarFile?.lastModified(), avatarFile?.length()) {
                        coil.request.ImageRequest.Builder(context)
                            .data(imageSource)
                            .memoryCachePolicy(coil.request.CachePolicy.WRITE_ONLY)
                            .diskCachePolicy(coil.request.CachePolicy.WRITE_ONLY)
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = imageModel,
                        contentDescription = stringResource(R.string.ui_custom_photo),
                        modifier = Modifier.size(size).clip(ExpressiveButtonShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    val initial = profile.displayName.trim().take(1).ifEmpty { "P" }.uppercase()
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            else -> {
                val iconVector = getPresetMaterialIcon(profile.avatarPresetId)
                if (iconVector != null) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = stringResource(R.string.ui_preset_avatar),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(size * 0.5f)
                    )
                } else {
                    val initial = profile.displayName.trim().take(1).ifEmpty { "P" }.uppercase()
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

fun getPresetMaterialIcon(presetId: String): androidx.compose.ui.graphics.vector.ImageVector? {
    return when (presetId) {
        "petal_flower" -> Icons.Rounded.LocalFlorist
        "cosmic_star" -> Icons.Rounded.Star
        "cyber_shield" -> Icons.Rounded.Shield
        "rocket_boost" -> Icons.Rounded.RocketLaunch
        "ocean_wave" -> Icons.Rounded.Water
        "ninja_cat" -> Icons.Rounded.Pets
        "sparkle" -> Icons.Rounded.AutoAwesome
        "bot_avatar" -> Icons.Rounded.SmartToy
        else -> null
    }
}

// ── Java Interop Bridge ────────────────────────────────────────────────────
object PetalAccountSyncBridge {
    @JvmStatic
    fun createAccountSyncView(
        activity: ComponentActivity,
        onBack: () -> Unit
    ): ComposeView {
        val rootView = activity.findViewById<android.view.View>(android.R.id.content) ?: activity.window.decorView
        com.petal.browser.predictive.PetalContentSnapshot.capture(rootView)
        return ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val snapshotBitmap = remember { com.petal.browser.predictive.PetalContentSnapshot.current?.asImageBitmap() }
                DisposableEffect(Unit) {
                    onDispose {
                        com.petal.browser.predictive.PetalContentSnapshot.clear()
                    }
                }
                val sp = remember { PreferenceManager.getDefaultSharedPreferences(activity) }
                var currentPaletteId by remember { mutableStateOf(sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId) }
                var isAmoled by remember { mutableStateOf(sp.getBoolean("sp_amoled", false)) }
                var isExpressiveColors by remember { mutableStateOf(sp.getBoolean("sp_expressive_colors", false)) }
                var useDynamic by remember { mutableStateOf(sp.getBoolean("useDynamicColor", isDynamicColorSupported)) }
                var fontName by remember { mutableStateOf(sp.getString("sp_app_font", "PETAL") ?: "PETAL") }
                var styleName by remember { mutableStateOf(sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT") }
                var fontWidthVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_width", 92f)) }
                var fontWeightVal by remember { mutableIntStateOf(sp.getInt("sp_font_weight", 750)) }
                var fontRoundnessVal by remember { mutableFloatStateOf(sp.getFloat("sp_font_roundness", 100f)) }

                DisposableEffect(sp) {
                    val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        when (key) {
                            "sp_palette_id" -> currentPaletteId = sp.getString("sp_palette_id", defaultPaletteId) ?: defaultPaletteId
                            "sp_amoled" -> isAmoled = sp.getBoolean("sp_amoled", false)
                            "sp_expressive_colors" -> isExpressiveColors = sp.getBoolean("sp_expressive_colors", false)
                            "useDynamicColor" -> useDynamic = sp.getBoolean("useDynamicColor", isDynamicColorSupported)
                            "sp_app_font" -> fontName = sp.getString("sp_app_font", "PETAL") ?: "PETAL"
                            "sp_color_style" -> styleName = sp.getString("sp_color_style", "TONAL_SPOT") ?: "TONAL_SPOT"
                            "sp_font_width" -> fontWidthVal = sp.getFloat("sp_font_width", 92f)
                            "sp_font_weight" -> fontWeightVal = sp.getInt("sp_font_weight", 750)
                            "sp_font_roundness" -> fontRoundnessVal = sp.getFloat("sp_font_roundness", 100f)
                        }
                    }
                    sp.registerOnSharedPreferenceChangeListener(listener)
                    onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
                }

                val appFont = remember(fontName) {
                    try { com.petal.browser.ui.theme.AppFont.valueOf(fontName) } catch (e: Exception) { com.petal.browser.ui.theme.AppFont.PETAL }
                }
                val colorStyle = remember(styleName) {
                    try { com.petal.browser.ui.theme.ColorStyle.valueOf(styleName) } catch (e: Exception) { com.petal.browser.ui.theme.ColorStyle.TONAL_SPOT }
                }

                PetalExpressiveTheme(
                    paletteId = currentPaletteId,
                    useAmoled = isAmoled,
                    dynamicColor = useDynamic,
                    expressiveColors = isExpressiveColors,
                    appFont = appFont,
                    colorStyle = colorStyle,
                    fontWidth = fontWidthVal,
                    fontWeight = fontWeightVal,
                    fontRoundness = fontRoundnessVal
                ) {
                    com.petal.browser.account.PetalAccountScreen(
                        backgroundSnapshot = snapshotBitmap,
                        onBack = onBack
                    )
                }
            }
        }
    }
}
