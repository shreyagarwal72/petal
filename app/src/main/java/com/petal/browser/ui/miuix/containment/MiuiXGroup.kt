package com.petal.browser.ui.miuix.containment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.petal.browser.ui.containment.PetalBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Miuix inset section: SmallTitle above a real Miuix [Card] with 12dp horizontal inset and 12dp
 * bottom spacing, exactly like InstallerX's settings pages. Optional header action and footer
 * caption are kept for source compatibility. [backdrop], [glassEnabled] and [shape] are accepted
 * but unused (the real Card owns surface and corners).
 */
@Composable
fun MiuiXGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    headerAction: (@Composable () -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") backdrop: PetalBackdrop? = null,
    @Suppress("UNUSED_PARAMETER") glassEnabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (headerAction != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (title != null) SmallTitle(text = title)
                headerAction()
            }
        } else if (title != null) {
            SmallTitle(text = title)
        }

        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            content = content,
        )

        if (!footer.isNullOrBlank()) {
            Text(
                text = footer,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 12.dp),
            )
        }
    }
}
