package com.petal.browser.engine.petal.blocking

import android.content.Context
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Opens an asset stream, automatically resolving and transparently decompressing `.gz` files
 * if available (e.g. `asset.txt.gz` for `asset.txt`) to save substantial APK and install size.
 */
internal fun openAssetStream(context: Context, assetName: String): InputStream {
    return try {
        val gzStream = context.assets.open("$assetName.gz")
        GZIPInputStream(gzStream)
    } catch (_: Throwable) {
        context.assets.open(assetName)
    }
}
