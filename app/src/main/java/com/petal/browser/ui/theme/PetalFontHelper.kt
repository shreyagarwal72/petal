package com.petal.browser.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.view.PetalToast
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.ceil
import kotlin.math.roundToInt

data class ValidatedFontInfo(
    val isVariable: Boolean,
    val extension: String
)

object PetalFontHelper {

    private const val FONTS_DIR = "fonts"
    private const val TAG_FVAR = 0x66766172 // 'fvar'
    private const val TAG_HEAD = 0x68656164 // 'head'
    private const val TAG_OTTO = 0x4F54544F // 'OTTO'
    private const val TAG_TRUE = 0x74727565 // 'true'
    private const val TAG_TYP1 = 0x74797031 // 'typ1'
    private const val TAG_TTCF = 0x74746366 // 'ttcf'
    private const val TAG_SFNT_TTF = 0x00010000

    private val typefaceCache = LruCache<String, Typeface>(32)

    fun clearCache() {
        typefaceCache.evictAll()
    }

    /**
     * Validates that [file] is a real TTF/OTF/TTC font file, inspects whether it has an OpenType `fvar`
     * variable font table, and confirms Android can load it without falling back/crashing.
     */
    fun inspectFontFile(file: File): ValidatedFontInfo? {
        if (!file.exists() || !file.isFile || !file.canRead() || file.length() < 32L) return null
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                val headerTag = raf.readInt()
                val ext: String
                val offset = when (headerTag) {
                    TAG_OTTO -> {
                        ext = "otf"
                        0L
                    }
                    TAG_SFNT_TTF, TAG_TRUE, TAG_TYP1 -> {
                        ext = "ttf"
                        0L
                    }
                    TAG_TTCF -> {
                        if (len < 16L) return null
                        raf.readShort() // major
                        raf.readShort() // minor
                        val numFonts = raf.readInt()
                        if (numFonts <= 0 || numFonts > 256) return null
                        val firstOffset = raf.readInt().toLong() and 0xFFFFFFFFL
                        if (firstOffset <= 0L || firstOffset + 12L > len) return null
                        ext = "ttf"
                        firstOffset
                    }
                    else -> return null
                }

                raf.seek(offset)
                val sfntTag = raf.readInt()
                if (sfntTag != TAG_SFNT_TTF && sfntTag != TAG_OTTO && sfntTag != TAG_TRUE && sfntTag != TAG_TYP1) {
                    return null
                }
                val numTables = raf.readUnsignedShort()
                if (numTables <= 0 || numTables > 512) return null
                if (offset + 12L + numTables * 16L > len) return null
                raf.seek(offset + 12L)

                var hasHead = false
                var hasFvar = false
                for (i in 0 until numTables) {
                    val tag = raf.readInt()
                    raf.readInt() // checksum
                    val tableOffset = raf.readInt().toLong() and 0xFFFFFFFFL
                    val tableLen = raf.readInt().toLong() and 0xFFFFFFFFL
                    if (tableOffset + tableLen > len) return null
                    if (tag == TAG_HEAD) hasHead = true
                    if (tag == TAG_FVAR && tableLen >= 16L) hasFvar = true
                }
                if (!hasHead) return null

                // Confirm Android's native Minikin/FreeType stack loads this font cleanly.
                val tf = Typeface.createFromFile(file)
                if (tf === Typeface.DEFAULT) return null

                ValidatedFontInfo(isVariable = hasFvar, extension = ext)
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun isVariableFontFile(file: File): Boolean = inspectFontFile(file)?.isVariable == true

    /**
     * Resolves a readable custom font file from [custom], falling back to `lastValidPath` and
     * legacy `custom_font.ttf` so a missing/failed file never drops straight back to Petal Signature
     * when a previous working custom font exists.
     */
    fun resolveReadableCustomFontFile(custom: CustomFontState): File? {
        val primary = custom.path?.takeIf { it.isNotBlank() }?.let(::File)
        if (primary != null && primary.exists() && primary.canRead() && inspectFontFile(primary) != null) {
            return primary
        }
        val fallback = custom.lastValidPath?.takeIf { it.isNotBlank() && it != custom.path }?.let(::File)
        if (fallback != null && fallback.exists() && fallback.canRead() && inspectFontFile(fallback) != null) {
            return fallback
        }
        return null
    }

    /**
     * Imports a user-picked font URI after validating that it is a real TTF/OTF file.
     * Saves under a unique filename so an invalid import never clobbers the active font.
     */
    fun saveCustomFontUri(context: Context, uri: Uri): String? {
        val tempFile = File(context.cacheDir, "import_font_${System.currentTimeMillis()}.tmp")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: run {
                tempFile.delete()
                showInvalidToast(context, "Could not read selected font file")
                return null
            }

            val info = inspectFontFile(tempFile)
            if (info == null) {
                tempFile.delete()
                showInvalidToast(context, "Invalid font file. Select a valid .ttf or .otf font.")
                return null
            }

            val fontsDir = File(context.filesDir, FONTS_DIR).apply { if (!exists()) mkdirs() }
            val destFile = File(fontsDir, "custom_${System.currentTimeMillis()}.${info.extension}")
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()

            val fileName = getFileName(context, uri) ?: destFile.name
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val currentPath = sp.getString(PetalFontPrefs.KEY_CUSTOM_FONT_PATH, null)
            val prevValid = currentPath?.takeIf {
                val f = File(it)
                f.exists() && inspectFontFile(f) != null
            } ?: sp.getString(PetalFontPrefs.KEY_CUSTOM_FONT_LAST_VALID_PATH, null)

            sp.edit().apply {
                if (!prevValid.isNullOrBlank()) {
                    putString(PetalFontPrefs.KEY_CUSTOM_FONT_LAST_VALID_PATH, prevValid)
                }
                putString(PetalFontPrefs.KEY_CUSTOM_FONT_PATH, destFile.absolutePath)
                putString(PetalFontPrefs.KEY_CUSTOM_FONT_NAME, fileName)
                putBoolean(PetalFontPrefs.KEY_CUSTOM_FONT_IS_VARIABLE, info.isVariable)
                putString(PetalFontPrefs.KEY_APP_FONT, AppFont.CUSTOM.name)
            }.apply()

            cleanupOldFonts(fontsDir, keepPaths = setOfNotNull(destFile.absolutePath, prevValid))
            clearCache()
            PetalThemePrefs.bump()
            destFile.absolutePath
        } catch (_: Throwable) {
            tempFile.delete()
            showInvalidToast(context, "Failed to import font file. Make sure it is a valid TTF or OTF font.")
            null
        }
    }

    private fun cleanupOldFonts(dir: File, keepPaths: Set<String>) {
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.absolutePath !in keepPaths) {
                runCatching { f.delete() }
            }
        }
    }

    private fun showInvalidToast(context: Context, message: String) {
        val main = Handler(Looper.getMainLooper())
        if (Looper.myLooper() == Looper.getMainLooper()) {
            PetalToast.show(context, message, PetalToast.LENGTH_LONG)
        } else {
            main.post { PetalToast.show(context, message, PetalToast.LENGTH_LONG) }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun getAppTypeface(context: Context, weight: Int? = null, italic: Boolean = false): Typeface {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val state = PetalFontPrefs.load(sp, context)
        return getTypefaceForState(context, state, weight, italic)
    }

    fun getTypefaceForState(
        context: Context,
        state: FontState,
        weight: Int? = null,
        italic: Boolean = false
    ): Typeface {
        val targetWeight = weight?.let { FontAxes.Axis.WEIGHT.clamp(it.toFloat()).toInt() }
        return when (state.appFont) {
            AppFont.SYSTEM -> createWeightVariant(Typeface.DEFAULT, targetWeight, italic)
            AppFont.NUNITO -> nunitoTypeface(context, targetWeight ?: state.nunitoWeight, italic)
            AppFont.CUSTOM -> {
                val file = resolveReadableCustomFontFile(state.customFont)
                if (file != null) {
                    customTypeface(file, state.customFont, targetWeight, italic)
                        ?: petalSignatureTypeface(context, state.gsFlexSettings, targetWeight, italic)
                } else {
                    petalSignatureTypeface(context, state.gsFlexSettings, targetWeight, italic)
                }
            }
            AppFont.PETAL -> petalSignatureTypeface(context, state.gsFlexSettings, targetWeight, italic)
        }
    }

    private fun createWeightVariant(base: Typeface, weight: Int?, italic: Boolean): Typeface {
        val resolvedWeight = weight ?: 400
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(base, resolvedWeight, italic)
        } else {
            val style = when {
                resolvedWeight >= 600 && italic -> Typeface.BOLD_ITALIC
                resolvedWeight >= 600 -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            Typeface.create(base, style)
        }
    }

    private fun customTypeface(
        file: File,
        custom: CustomFontState,
        weight: Int?,
        italic: Boolean
    ): Typeface? {
        val isVar = isVariableFontFile(file)
        val tierAxes = when {
            weight != null && weight >= 800 -> custom.settings.display
            weight != null && weight >= 600 -> custom.settings.headline
            else -> custom.settings.body
        }
        val targetWeight = weight ?: tierAxes.weight.toInt()
        val key = "custom|${file.absolutePath}|${file.lastModified()}|$isVar|${if (isVar) tierAxes.toVariationString(targetWeight) else targetWeight}|$italic"
        typefaceCache.get(key)?.let { return it }

        val built = try {
            if (isVar) {
                val effectiveAxes = if (italic) tierAxes.copy(slant = -10f) else tierAxes
                Typeface.Builder(file).setFontVariationSettings(effectiveAxes.toVariationString(overrideWeight = targetWeight)).build()
            } else {
                val base = Typeface.createFromFile(file)
                createWeightVariant(base, targetWeight, italic)
            }
        } catch (_: Throwable) {
            null
        }
        if (built != null) typefaceCache.put(key, built)
        return built
    }

    private fun nunitoTypeface(context: Context, weight: Int, italic: Boolean): Typeface {
        val w = FontAxes.Axis.WEIGHT.clamp(weight.toFloat()).toInt()
        val key = "nunito|$w|$italic"
        typefaceCache.get(key)?.let { return it }

        val fallback = try {
            ResourcesCompat.getFont(context, R.font.nunito_variable) ?: Typeface.DEFAULT
        } catch (_: Throwable) {
            Typeface.DEFAULT
        }
        val built = try {
            val file = ensureRawFontCached(context, "nunito_variable_cached.ttf", R.font.nunito_variable)
            val tf = Typeface.Builder(file).setFontVariationSettings("'wght' $w").build() ?: fallback
            if (italic) createWeightVariant(tf, w, true) else tf
        } catch (_: Throwable) {
            createWeightVariant(fallback, w, italic)
        }
        typefaceCache.put(key, built)
        return built
    }

    private fun petalSignatureTypeface(
        context: Context,
        gs: GSFlexSettings,
        weight: Int?,
        italic: Boolean
    ): Typeface {
        val baseAxes = if (gs.preset == GSFlexPreset.CUSTOM) {
            when {
                weight != null && weight >= 800 -> gs.display
                weight != null && weight >= 600 -> gs.headline
                else -> gs.body
            }
        } else {
            val p = VariableFontFactory.getPresetFontAxes(gs.preset)
            when {
                weight != null && weight >= 800 -> p.first
                weight != null && weight >= 600 -> p.second
                else -> p.third
            }
        }
        val resolvedWeight = weight ?: baseAxes.weight.toInt()
        val axes = if (italic) baseAxes.copy(slant = -10f) else baseAxes
        val settings = axes.toVariationString(overrideWeight = resolvedWeight)
        val key = "petal|$settings"
        typefaceCache.get(key)?.let { return it }

        val fallback = try {
            ResourcesCompat.getFont(context, R.font.google_sans_flex_variable) ?: Typeface.DEFAULT
        } catch (_: Throwable) {
            Typeface.DEFAULT
        }
        val built = try {
            val file = ensureRawFontCached(context, "petal_signature.ttf", R.font.google_sans_flex_variable)
            Typeface.Builder(file).setFontVariationSettings(settings).build() ?: fallback
        } catch (_: Throwable) {
            createWeightVariant(fallback, resolvedWeight, italic)
        }
        typefaceCache.put(key, built)
        return built
    }

    private fun ensureRawFontCached(context: Context, name: String, resId: Int): File {
        val file = File(context.cacheDir, name)
        if (!file.exists() || file.length() == 0L) {
            context.resources.openRawResource(resId).use { i ->
                file.outputStream().use { o -> i.copyTo(o) }
            }
        }
        return file
    }

    /**
     * Applies the user's chosen app font to a classic View hierarchy, preserving each TextView's
     * bold/medium/italic weight styling and applying Contrast-checked Font Accent if enabled.
     */
    @JvmStatic
    fun applyTo(root: View?) {
        if (root == null) return
        val sp = PreferenceManager.getDefaultSharedPreferences(root.context)
        val state = PetalFontPrefs.load(sp, root.context)
        fun walk(v: View) {
            if (v is TextView) {
                val existing = v.typeface
                val existingStyle = existing?.style ?: Typeface.NORMAL
                val italic = (existingStyle and Typeface.ITALIC) != 0
                val existingWeight = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && existing != null -> existing.weight
                    (existingStyle and Typeface.BOLD) != 0 -> 700
                    else -> null
                }
                v.typeface = getTypefaceForState(v.context, state, existingWeight, italic)
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
        }
        walk(root)
    }

    /**
     * Renders text using the active runtime [Typeface] into a crisp density-aware [Bitmap] for
     * RemoteViews / Glance widget surfaces where Binder forbids sending custom in-memory Typefaces.
     */
    @JvmStatic
    @JvmOverloads
    fun renderTextBitmap(
        context: Context,
        text: String,
        textSizeSp: Float,
        colorInt: Int,
        weight: Int = 500,
        align: Paint.Align = Paint.Align.LEFT,
        maxWidthDp: Int = 220
    ): Bitmap? {
        if (text.isBlank()) return null
        return try {
            val dm = context.resources.displayMetrics
            val textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSizeSp, dm)
            val padX = (3f * dm.density).coerceAtLeast(4f)
            val padY = (2f * dm.density).coerceAtLeast(3f)
            val maxW = (maxWidthDp * dm.density).roundToInt().coerceAtLeast(32)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = colorInt
                textSize = textSizePx
                typeface = getAppTypeface(context, weight = weight)
            }
            val availTextPx = (maxW - 2f * padX).coerceAtLeast(16f)
            var drawText = text
            val bounds = android.graphics.Rect()
            fun measureVisualWidth(s: String): Float {
                paint.getTextBounds(s, 0, s.length, bounds)
                val adv = paint.measureText(s)
                val inked = (bounds.right - kotlin.math.min(0, bounds.left)).toFloat()
                return kotlin.math.max(adv, inked)
            }
            var measuredW = measureVisualWidth(drawText)
            if (measuredW > availTextPx) {
                val ell = "\u2026"
                val ellW = measureVisualWidth(ell)
                val targetAvail = (availTextPx - ellW).coerceAtLeast(1f)
                var count = paint.breakText(drawText, true, targetAvail, null)
                while (count > 0 && measureVisualWidth(drawText.substring(0, count).trimEnd() + ell) > availTextPx) {
                    count--
                }
                drawText = if (count > 0) drawText.substring(0, count).trimEnd() + ell else ell
                measuredW = measureVisualWidth(drawText)
            } else {
                paint.getTextBounds(drawText, 0, drawText.length, bounds)
            }
            val fm = paint.fontMetrics
            val leftBear = kotlin.math.min(0, bounds.left)
            val topY = kotlin.math.min(fm.ascent, bounds.top.toFloat())
            val botY = kotlin.math.max(fm.descent, bounds.bottom.toFloat())
            val wPx = ceil(measuredW + 2f * padX).toInt().coerceIn(4, maxW)
            val hPx = ceil(botY - topY + 2f * padY).toInt().coerceAtLeast(4)
            val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888).apply {
                density = dm.densityDpi
            }
            val canvas = Canvas(bmp)
            paint.textAlign = align
            val x = when (align) {
                Paint.Align.CENTER -> wPx / 2f
                Paint.Align.RIGHT -> wPx - padX
                else -> padX - leftBear
            }
            canvas.drawText(drawText, x, -topY + padY, paint)
            bmp
        } catch (_: Throwable) {
            null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1) name = cursor.getString(index)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) path.substring(cut + 1) else path
            }
        }
        return name
    }
}
