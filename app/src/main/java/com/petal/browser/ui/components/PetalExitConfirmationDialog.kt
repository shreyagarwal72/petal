package com.petal.browser.ui.components

import android.app.Activity
import android.app.Dialog
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Build
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.theme.PetalExpressiveTheme

/**
 * Material 3 Expressive exit confirmation dialog used by the browser activity.
 * It intentionally uses the app's Compose theme so typography, dynamic colors,
 * expressive motion and user font settings stay consistent with the rest of Petal.
 */
object PetalExitConfirmationDialog {

    @JvmStatic
    fun show(activity: Activity, onExit: Runnable) {
        if (activity.isFinishing || activity.isDestroyed) return

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)

        val composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                PetalExpressiveTheme {
                    ExitDialogContent(
                        onStay = { dialog.dismiss() },
                        onExit = {
                            dialog.dismiss()
                            onExit.run()
                        }
                    )
                }
            }
        }

        dialog.setContentView(composeView)
        dialog.setOnDismissListener { }
        dialog.setOnShowListener {
            val window = dialog.window ?: return@setOnShowListener
            window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            window.setDimAmount(0.68f)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.setBackgroundBlurRadius(34)
            }
            window.setGravity(Gravity.CENTER)
            window.setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.84f).toInt().coerceAtMost(dp(activity, 360)),
                WindowManager.LayoutParams.WRAP_CONTENT
            )

            // Expressive pop-in: slightly overshooting spring-like scale without
            // changing the Material surface itself.
            val decor = window.decorView
            decor.scaleX = 0.90f
            decor.scaleY = 0.90f
            decor.alpha = 0f
            decor.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(260L)
                .setInterpolator(android.view.animation.PathInterpolator(0.2f, 0.9f, 0.25f, 1f))
                .start()
        }
        dialog.show()

        // Dialog.show() creates the window after setOnShowListener registration.
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            window.setDimAmount(0.68f)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.84f).toInt().coerceAtMost(dp(activity, 360)),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.setBackgroundBlurRadius(34)
            }
        }
    }

    private fun dp(activity: Activity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}

@Composable
private fun ExitDialogContent(
    onStay: () -> Unit,
    onExit: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onStay) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(start = 24.dp, top = 22.dp, end = 24.dp, bottom = 18.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .semantics { contentDescription = "Close" }
                        .width(28.dp)
                        .height(28.dp)
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Leave Petal Browser?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Do you want to exit the browser?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onStay,
                        modifier = Modifier.semantics { role = Role.Button },
                    ) {
                        Text("Stay")
                    }

                    Spacer(Modifier.width(8.dp))

                    Button(
                        onClick = onExit,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        contentPadding = ButtonDefaults.ContentPadding,
                        modifier = Modifier.semantics { role = Role.Button },
                    ) {
                        Text("Exit")
                    }
                }
            }
        }
    }
}
