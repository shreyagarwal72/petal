package com.petal.browser.ui.theme.layout

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object LayoutIcons {
    val IOS: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        ImageVector.Builder(
            name = "IOS",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                stroke = null,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(18.71f, 19.5f)
                curveTo(17.88f, 20.74f, 17.0f, 21.95f, 15.66f, 21.97f)
                curveTo(14.32f, 22.0f, 13.89f, 21.18f, 12.37f, 21.18f)
                curveTo(10.84f, 21.18f, 10.37f, 21.95f, 9.1f, 22.0f)
                curveTo(7.79f, 22.05f, 6.8f, 20.68f, 5.96f, 19.47f)
                curveTo(4.25f, 17.0f, 2.94f, 12.45f, 4.7f, 9.39f)
                curveTo(5.57f, 7.87f, 7.13f, 6.91f, 8.82f, 6.88f)
                curveTo(10.1f, 6.86f, 11.32f, 7.75f, 12.11f, 7.75f)
                curveTo(12.89f, 7.75f, 14.37f, 6.68f, 15.92f, 6.84f)
                curveTo(16.57f, 6.87f, 18.39f, 7.1f, 19.56f, 8.82f)
                curveTo(19.47f, 8.88f, 17.39f, 10.1f, 17.41f, 12.63f)
                curveTo(17.44f, 15.65f, 20.06f, 16.66f, 20.09f, 16.67f)
                curveTo(20.06f, 16.74f, 19.67f, 18.11f, 18.71f, 19.5f)
                moveTo(13.0f, 3.5f)
                curveTo(13.73f, 2.67f, 14.94f, 2.04f, 15.94f, 2.0f)
                curveTo(16.07f, 3.17f, 15.6f, 4.35f, 14.9f, 5.19f)
                curveTo(14.21f, 6.04f, 13.07f, 6.7f, 11.95f, 6.61f)
                curveTo(11.8f, 5.46f, 12.36f, 4.26f, 13.0f, 3.5f)
                close()
            }
        }.build()
    }

    val Windows: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        ImageVector.Builder(
            name = "Windows",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(2.0f, 4.0f)
                verticalLineToRelative(7.4769f)
                horizontalLineToRelative(9.481f)
                verticalLineTo(2.0f)
                horizontalLineTo(4.0f)
                curveTo(2.8954f, 2.0f, 2.0f, 2.8954f, 2.0f, 4.0f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(2.0f, 20.0f)
                curveToRelative(0.0f, 1.1046f, 0.8954f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(7.481f)
                verticalLineToRelative(-9.481f)
                horizontalLineTo(2.0f)
                verticalLineTo(20.0f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(20.0f, 2.0f)
                horizontalLineToRelative(-7.481f)
                verticalLineToRelative(9.4769f)
                horizontalLineTo(22.0f)
                verticalLineTo(4.0f)
                curveTo(22.0f, 2.8954f, 21.1046f, 2.0f, 20.0f, 2.0f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(12.519f, 22.0f)
                horizontalLineTo(20.0f)
                curveToRelative(1.1046f, 0.0f, 2.0f, -0.8954f, 2.0f, -2.0f)
                verticalLineToRelative(-7.481f)
                horizontalLineToRelative(-9.481f)
                verticalLineTo(22.0f)
                close()
            }
        }.build()
    }

    val HyperOS: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        ImageVector.Builder(
            name = "HyperOS",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(13.899f, 3.031f)
                curveToRelative(-0.089f, -0f, -0.187f, 0.022f, -0.295f, 0.051f)
                curveToRelative(-0.192f, 0.051f, -0.346f, 0.104f, -0.425f, 0.242f)
                curveToRelative(-0.158f, 0.28f, -0.165f, 0.697f, -0.179f, 1.474f)
                verticalLineToRelative(3.398f)
                curveToRelative(0.014f, 0.776f, 0.021f, 1.194f, 0.179f, 1.474f)
                curveToRelative(0.079f, 0.138f, 0.233f, 0.191f, 0.425f, 0.242f)
                curveToRelative(0.241f, 0.064f, 0.589f, 0.068f, 1.385f, 0.068f)
                horizontalLineToRelative(3.388f)
                curveToRelative(0.797f, -0f, 1.144f, -0.004f, 1.385f, -0.068f)
                curveToRelative(0.192f, -0.051f, 0.346f, -0.104f, 0.425f, -0.242f)
                curveToRelative(0.158f, -0.28f, 0.165f, -0.698f, 0.179f, -1.474f)
                verticalLineToRelative(-3.398f)
                curveToRelative(-0.014f, -0.777f, -0.021f, -1.194f, -0.179f, -1.474f)
                curveToRelative(-0.079f, -0.138f, -0.233f, -0.191f, -0.425f, -0.242f)
                curveToRelative(-0.241f, -0.064f, -0.588f, -0.068f, -1.385f, -0.068f)
                horizontalLineToRelative(-3.388f)
                curveToRelative(-0.354f, -0f, -0.589f, 0.007f, -0.765f, 0.017f)
                close()
                moveTo(3.081f, 13.849f)
                curveToRelative(-0f, -0.089f, 0.022f, -0.187f, 0.051f, -0.295f)
                curveToRelative(0.051f, -0.192f, 0.104f, -0.346f, 0.242f, -0.425f)
                curveToRelative(0.28f, -0.158f, 0.697f, -0.165f, 1.474f, -0.179f)
                horizontalLineToRelative(3.398f)
                curveToRelative(0.776f, 0.014f, 1.194f, 0.021f, 1.474f, 0.179f)
                curveToRelative(0.138f, 0.079f, 0.191f, 0.233f, 0.242f, 0.425f)
                curveToRelative(0.064f, 0.241f, 0.068f, 0.589f, 0.068f, 1.385f)
                verticalLineToRelative(3.388f)
                curveToRelative(-0f, 0.797f, -0.004f, 1.144f, -0.068f, 1.385f)
                curveToRelative(-0.051f, 0.192f, -0.104f, 0.346f, -0.242f, 0.425f)
                curveToRelative(-0.28f, 0.158f, -0.698f, 0.165f, -1.474f, 0.179f)
                horizontalLineToRelative(-3.398f)
                curveToRelative(-0.777f, -0.014f, -1.194f, -0.021f, -1.474f, -0.179f)
                curveToRelative(-0.138f, -0.079f, -0.191f, -0.233f, -0.242f, -0.425f)
                curveToRelative(-0.064f, -0.241f, -0.068f, -0.588f, -0.068f, -1.385f)
                verticalLineToRelative(-3.388f)
                curveToRelative(-0f, -0.354f, 0.007f, -0.589f, 0.017f, -0.765f)
                close()
                moveTo(13.899f, 12.031f)
                curveToRelative(-0.089f, -0f, -0.187f, 0.022f, -0.295f, 0.051f)
                curveToRelative(-0.192f, 0.051f, -0.346f, 0.104f, -0.425f, 0.242f)
                curveToRelative(-0.158f, 0.28f, -0.165f, 0.697f, -0.179f, 1.474f)
                verticalLineToRelative(5.398f)
                curveToRelative(0.014f, 0.776f, 0.021f, 1.194f, 0.179f, 1.474f)
                curveToRelative(0.079f, 0.138f, 0.233f, 0.191f, 0.425f, 0.242f)
                curveToRelative(0.241f, 0.064f, 0.589f, 0.068f, 1.385f, 0.068f)
                horizontalLineToRelative(5.388f)
                curveToRelative(0.797f, -0f, 1.144f, -0.004f, 1.385f, -0.068f)
                curveToRelative(0.192f, -0.051f, 0.346f, -0.104f, 0.425f, -0.242f)
                curveToRelative(0.158f, -0.28f, 0.165f, -0.698f, 0.179f, -1.474f)
                verticalLineToRelative(-5.398f)
                curveToRelative(-0.014f, -0.777f, -0.021f, -1.194f, -0.179f, -1.474f)
                curveToRelative(-0.079f, -0.138f, -0.233f, -0.191f, -0.425f, -0.242f)
                curveToRelative(-0.241f, -0.064f, -0.588f, -0.068f, -1.385f, -0.068f)
                horizontalLineToRelative(-5.388f)
                curveToRelative(-0.354f, -0f, -0.589f, 0.007f, -0.765f, 0.017f)
                close()
            }
        }.build()
    }

    val SamsungLetter: ImageVector by lazy(LazyThreadSafetyMode.NONE) {
        ImageVector.Builder(
            name = "SamsungLetter",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(13.706f, 15.551f)
                curveToRelative(0.163f, 0.401f, 0.113f, 0.919f, 0.036f, 1.231f)
                curveToRelative(-0.139f, 0.551f, -0.514f, 1.115f, -1.616f, 1.115f)
                curveToRelative(-1.042f, 0f, -1.672f, -0.597f, -1.672f, -1.506f)
                verticalLineToRelative(-1.606f)
                horizontalLineToRelative(-4.452f)
                lineToRelative(-0.003f, 1.284f)
                curveToRelative(0f, 3.699f, 2.913f, 4.817f, 6.034f, 4.817f)
                curveToRelative(3.002f, 0f, 5.474f, -1.025f, 5.865f, -3.791f)
                curveToRelative(0.202f, -1.433f, 0.05f, -2.372f, -0.017f, -2.727f)
                curveToRelative(-0.7f, -3.473f, -6.999f, -4.511f, -7.467f, -6.452f)
                curveToRelative(-0.08f, -0.331f, -0.056f, -0.687f, -0.017f, -0.876f)
                curveToRelative(0.116f, -0.527f, 0.478f, -1.111f, 1.516f, -1.111f)
                curveToRelative(0.969f, 0f, 1.542f, 0.601f, 1.542f, 1.506f)
                verticalLineToRelative(1.025f)
                horizontalLineToRelative(4.136f)
                verticalLineToRelative(-1.164f)
                curveToRelative(0f, -3.616f, -3.244f, -4.18f, -5.593f, -4.18f)
                curveToRelative(-2.952f, 0f, -5.364f, 0.975f, -5.805f, 3.675f)
                curveToRelative(-0.119f, 0.746f, -0.136f, 1.41f, 0.036f, 2.243f)
                curveToRelative(0.726f, 3.387f, 6.618f, 4.369f, 7.474f, 6.518f)
                close()
            }
        }.build()
    }
}
