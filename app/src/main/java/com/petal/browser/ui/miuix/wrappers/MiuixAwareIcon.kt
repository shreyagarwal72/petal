package com.petal.browser.ui.miuix.wrappers

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import com.petal.browser.ui.containment.isMiuixEnabled
import com.petal.browser.ui.miuix.icons.MiuixIconResolver
import androidx.compose.material3.Icon as MaterialIcon

/**
 * Drop-in Icon. In Miuix mode the Material vector is resolved to the matching miuix-icons icon
 * (MiuixIconResolver) and falls back to the original vector when the library has none; in every
 * other mode it is exactly Material's Icon. Tint and semantics stay Material's.
 */
@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    MaterialIcon(
        imageVector = if (isMiuixEnabled()) MiuixIconResolver.resolve(imageVector) else imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) = MaterialIcon(painter = painter, contentDescription = contentDescription, modifier = modifier, tint = tint)

@Composable
fun Icon(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) = MaterialIcon(bitmap = bitmap, contentDescription = contentDescription, modifier = modifier, tint = tint)
