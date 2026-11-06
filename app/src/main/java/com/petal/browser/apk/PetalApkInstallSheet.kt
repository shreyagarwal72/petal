package com.petal.browser.apk

import com.petal.browser.ui.theme.ExpressiveButtonShape
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import com.petal.browser.ui.components.PetalSpring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.components.IconSwitch
import com.petal.browser.ui.components.PetalCircularWavyProgressIndicator
import com.petal.browser.ui.containment.PetalSheet
import com.petal.browser.ui.containment.isLiquidGlassEnabled
import com.petal.browser.ui.containment.liquidGlassChrome
import com.petal.browser.ui.containment.petalConnectedShape
import com.petal.browser.ui.containment.petalGroupSurfaceColor

/**
 * Material 3 Expressive & Liquid Glass APK Installer Sheet.
 * Directly recreates the Orion Store installer bottom sheet with offline insights,
 * 16 KB page-readiness checks, Exodus Privacy tracker lists, and Beyond Install flags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetalApkInstallSheet(
    step: ApkStep,
    insights: PetalApkInsights.Report?,
    isInspecting: Boolean,
    fastRoute: Pair<String, String>?,
    selectedOptions: Set<String>,
    customInstaller: String,
    onOptionToggle: (String, Boolean) -> Unit,
    onCustomInstallerChange: (String) -> Unit,
    onOpenInspect: () -> Unit,
    onCloseInspect: () -> Unit,
    onInstall: () -> Unit,
    onOpenInstalledApp: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")

    PetalSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 20.dp)
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        (fadeIn(animationSpec = PetalSpring.fadeThroughIn()) +
                                scaleIn(initialScale = 0.95f, animationSpec = PetalSpring.spatial()))
                            .togetherWith(
                                fadeOut(animationSpec = PetalSpring.fadeThroughOut()) +
                                        scaleOut(targetScale = 0.95f, animationSpec = PetalSpring.exit())
                            )
                    },
                    label = "ApkStepTransition"
                ) { currentStep ->
                    when (currentStep) {
                        ApkStep.Reading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    PetalCircularWavyProgressIndicator(
                                        modifier = Modifier.size(44.dp),
                                        size = 44.dp
                                    )
                                    Text(
                                        text = "Inspecting package...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        is ApkStep.Ready -> {
                            ReadyContent(
                                apk = currentStep.apk,
                                insights = insights,
                                isInspecting = isInspecting,
                                fastRoute = fastRoute,
                                selectedOptions = selectedOptions,
                                customInstaller = customInstaller,
                                onOptionToggle = onOptionToggle,
                                onCustomInstallerChange = onCustomInstallerChange,
                                onOpenInspect = onOpenInspect,
                                onInstall = onInstall,
                                onDismiss = onDismiss
                            )
                        }

                        is ApkStep.Inspect -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                                            onCloseInspect()
                                        }
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.ArrowBack,
                                            contentDescription = "Back",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Inside this APK",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Header(currentStep.apk)
                                if (insights != null) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f, fill = false)
                                            .padding(vertical = 4.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        InspectPage(insights)
                                    }
                                }
                                Button(
                                    onClick = {
                                        PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                                        onCloseInspect()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .padding(top = 12.dp),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Text("Back to install", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        is ApkStep.Installing -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Header(currentStep.apk)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 36.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    PetalCircularWavyProgressIndicator(
                                        modifier = Modifier.size(54.dp),
                                        size = 54.dp
                                    )
                                    Text(
                                        text = "Installing ${currentStep.apk.label}...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (fastRoute?.first == "shizuku") "Direct installation in progress via Shizuku..." else "Direct installation in progress...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        is ApkStep.Done -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Header(currentStep.apk)
                                NoteCard(
                                    icon = Icons.Rounded.CheckCircle,
                                    title = "Installed",
                                    body = "${currentStep.apk.label} is ready to launch.",
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = onDismiss,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = petalConnectedShape(0, 2),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Text("Done")
                                    }
                                    Button(
                                        onClick = { onOpenInstalledApp(currentStep.apk.packageName) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = petalConnectedShape(1, 2),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Icon(Icons.Rounded.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Open", maxLines = 1)
                                    }
                                }
                            }
                        }

                        is ApkStep.Failed -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (currentStep.apk != null) Header(currentStep.apk)
                                NoteCard(
                                    icon = Icons.Rounded.ErrorOutline,
                                    title = currentStep.title,
                                    body = currentStep.body,
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val hasRetry = currentStep.apk != null
                                    FilledTonalButton(
                                        onClick = onDismiss,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = if (hasRetry) petalConnectedShape(0, 2) else RoundedCornerShape(50.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Text("Dismiss")
                                    }
                                    if (hasRetry) {
                                        Button(
                                            onClick = onInstall,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp),
                                            shape = petalConnectedShape(1, 2),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Text("Try again")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyContent(
    apk: PetalApkInstallerEngine.ApkMeta,
    insights: PetalApkInsights.Report?,
    isInspecting: Boolean,
    fastRoute: Pair<String, String>?,
    selectedOptions: Set<String>,
    customInstaller: String,
    onOptionToggle: (String, Boolean) -> Unit,
    onCustomInstallerChange: (String) -> Unit,
    onOpenInspect: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var beyondInstallExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Header(apk)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            // Clash & Compatibility warnings
            apk.clash?.let { clash ->
                val (warnTitle, warnBody) = when (clash) {
                    PetalApkInstallerEngine.ClashType.SYSTEM -> "System application" to "This package exists as a system app; updates must be signed by the system or ROM vendor."
                    PetalApkInstallerEngine.ClashType.SIGNATURE_MISMATCH -> "Signature mismatch" to "This APK is signed with a different key than the installed version. You must uninstall the existing app first."
                    PetalApkInstallerEngine.ClashType.HIDDEN -> "Uninstalled profile copy" to "This package is still present in another profile or user space on this device."
                }
                NoteCard(
                    icon = Icons.Rounded.Security,
                    title = warnTitle,
                    body = warnBody,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            insights?.let { rep ->
                val deviceSdk = android.os.Build.VERSION.SDK_INT
                if (rep.minSdk > deviceSdk) {
                    NoteCard(
                        icon = Icons.Rounded.ErrorOutline,
                        title = "Needs Android ${PetalApkInsights.androidName(rep.minSdk)}+",
                        body = "This device runs Android ${PetalApkInsights.androidName(deviceSdk)}; this package requires a newer Android OS.",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                if (!rep.runsHere) {
                    NoteCard(
                        icon = Icons.Rounded.WarningAmber,
                        title = "ABI Incompatibility",
                        body = "This package carries ${rep.abis.joinToString()} libraries; your device expects ${android.os.Build.SUPPORTED_ABIS.first()}.",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Fast Install Note
            if (fastRoute != null) {
                NoteCard(
                    icon = Icons.Rounded.Bolt,
                    title = "Fast install",
                    body = fastRoute.second,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            // "Inside this APK" Card
            InsideThisApkCard(
                insights = insights,
                isInspecting = isInspecting,
                onClick = onOpenInspect
            )

            // "Beyond install" Card
            BeyondInstallCard(
                expanded = beyondInstallExpanded,
                selectedOptions = selectedOptions,
                customInstaller = customInstaller,
                onExpandToggle = {
                    beyondInstallExpanded = !beyondInstallExpanded
                    PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.TICK, 0.7f)
                },
                onOptionToggle = onOptionToggle,
                onCustomInstallerChange = onCustomInstallerChange
            )
        }

        // Action buttons: Cancel / Install
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                    onDismiss()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = petalConnectedShape(0, 2),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }

            Button(
                onClick = {
                    PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.HEAVY_CLICK, 1.0f)
                    onInstall()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = petalConnectedShape(1, 2),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                val actionLabel = when {
                    !apk.isInstalled -> "Install"
                    apk.isOlderThanInstalled -> "Reinstall"
                    else -> "Update"
                }
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Header(apk: PetalApkInstallerEngine.ApkMeta) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (apk.icon != null) {
            Image(
                bitmap = apk.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Android,
                    contentDescription = null,
                    tint = cs.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = apk.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = apk.packageName,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val versionText = listOfNotNull(
                apk.versionName.takeIf { it.isNotBlank() }?.let { "Version $it" },
                apk.installedVersion?.let { "installed $it" }
            ).joinToString(" • ")

            if (versionText.isNotBlank()) {
                Text(
                    text = versionText,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val statusText = when {
                !apk.isInstalled -> "New app"
                apk.isOlderThanInstalled -> "Older than installed"
                apk.installedVersionCode != null && apk.versionCode > apk.installedVersionCode -> "Update"
                else -> "Reinstall"
            }
            Text(
                text = "${PetalApkInsights.formatSize(apk.file.length())} • $statusText",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InsideThisApkCard(
    insights: PetalApkInsights.Report?,
    isInspecting: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")

    Surface(
        onClick = {
            if (insights != null) {
                PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                onClick()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .then(
                if (isGlass) Modifier.liquidGlassChrome(RoundedCornerShape(20.dp), enabled = true)
                else Modifier
            ),
        enabled = insights != null,
        shape = RoundedCornerShape(20.dp),
        color = if (isGlass) petalGroupSurfaceColor() else cs.surfaceContainerHigh
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Science, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Inside this APK",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (insights == null && isInspecting) {
                    PetalCircularWavyProgressIndicator(modifier = Modifier.size(20.dp), size = 20.dp)
                } else {
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = "Inspect",
                        tint = cs.onSurfaceVariant
                    )
                }
            }

            if (insights == null) {
                Text(
                    text = "Reading code, native libraries and permissions...",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else {
                FlowRow(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    insights.frameworks.forEach {
                        ExpressiveChip(Icons.Rounded.Layers, it)
                    }
                    ExpressiveChip(Icons.Rounded.Android, "Target Android ${PetalApkInsights.androidName(insights.targetSdk)}")
                    ExpressiveChip(
                        Icons.Rounded.Memory,
                        if (insights.abis.isEmpty()) "No native code" else insights.abis.joinToString(" • ") { shortAbi(it) },
                        warn = !insights.runsHere
                    )
                    insights.pageReady?.let { ready ->
                        ExpressiveChip(
                            if (ready) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                            if (ready) "16 KB ready" else "Not 16 KB ready",
                            warn = !ready
                        )
                    }
                    ExpressiveChip(
                        Icons.Rounded.Radar,
                        when (insights.trackers.size) {
                            0 -> "No trackers found"
                            1 -> "1 tracker"
                            else -> "${insights.trackers.size} trackers"
                        },
                        warn = insights.trackers.isNotEmpty()
                    )
                    val newCount = insights.newPerms.size
                    ExpressiveChip(
                        Icons.Rounded.Policy,
                        "${insights.perms.size} permissions" + if (newCount > 0) " • $newCount new" else "",
                        warn = insights.newPerms.any { it.dangerous }
                    )
                    if (insights.debuggable || insights.debugSigned) {
                        ExpressiveChip(Icons.Rounded.BugReport, "Debug build", warn = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun BeyondInstallCard(
    expanded: Boolean,
    selectedOptions: Set<String>,
    customInstaller: String,
    onExpandToggle: () -> Unit,
    onOptionToggle: (String, Boolean) -> Unit,
    onCustomInstallerChange: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .then(
                if (isGlass) Modifier.liquidGlassChrome(RoundedCornerShape(20.dp), enabled = true)
                else Modifier
            ),
        shape = RoundedCornerShape(20.dp),
        color = if (isGlass) petalGroupSurfaceColor() else if (expanded) cs.secondaryContainer else cs.surfaceContainerHigh
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandToggle() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Beyond install",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Downgrade, grant permissions, Play Store install and more",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                IconSwitch(
                    checked = expanded,
                    icon = Icons.Rounded.Check,
                    onCheckedChange = { onExpandToggle() }
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val supportedOptions = remember {
                        val sdk = android.os.Build.VERSION.SDK_INT
                        PetalApkInstallerEngine.INSTALL_OPTIONS.filter { sdk >= it.minSdk }
                    }

                    supportedOptions.forEach { opt ->
                        val isChecked = opt.id in selectedOptions
                        Surface(
                            onClick = { onOptionToggle(opt.id, !isChecked) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isChecked) cs.primaryContainer else if (isGlass) cs.surfaceContainerHighest.copy(alpha = 0.45f) else cs.surfaceContainerHighest,
                            contentColor = if (isChecked) cs.onPrimaryContainer else cs.onSurface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = opt.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = opt.desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isChecked) cs.onPrimaryContainer.copy(alpha = 0.8f) else cs.onSurfaceVariant
                                    )
                                }
                                IconSwitch(
                                    checked = isChecked,
                                    icon = Icons.Rounded.Check,
                                    onCheckedChange = { onOptionToggle(opt.id, it) }
                                )
                            }
                        }

                        if (opt.id == "installer" && isChecked) {
                            TextField(
                                value = customInstaller,
                                onValueChange = onCustomInstallerChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                label = { Text("Installer Package Name") },
                                placeholder = { Text("com.android.vending") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveChip(
    icon: ImageVector,
    text: String,
    warn: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    val bgColor = when {
        warn -> cs.tertiaryContainer
        isGlass -> cs.surfaceContainerHighest.copy(alpha = 0.5f)
        else -> cs.surfaceContainerHighest
    }
    val fgColor = if (warn) cs.onTertiaryContainer else cs.onSurface

    Surface(
        shape = ExpressiveButtonShape,
        color = bgColor,
        contentColor = fgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NoteCard(
    icon: ImageVector,
    title: String,
    body: String,
    containerColor: Color,
    contentColor: Color
) {
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .then(
                if (isGlass) Modifier.liquidGlassChrome(RoundedCornerShape(20.dp), enabled = true)
                else Modifier
            ),
        shape = RoundedCornerShape(20.dp),
        color = if (isGlass) containerColor.copy(alpha = 0.45f) else containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = contentColor.copy(alpha = 0.88f))
            }
        }
    }
}

@Composable
private fun InspectPage(rep: PetalApkInsights.Report) {
    val cs = MaterialTheme.colorScheme
    val ty = MaterialTheme.typography
    val context = LocalContext.current
    val isGlass = isLiquidGlassEnabled("sp_liquid_glass_containments")
    var allPerms by remember { mutableStateOf(false) }
    var allLibs by remember { mutableStateOf(false) }

    @Composable
    fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp),
            style = ty.labelLarge,
            color = cs.primary,
            fontWeight = FontWeight.Bold
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isGlass) Modifier.liquidGlassChrome(RoundedCornerShape(20.dp), enabled = true)
                    else Modifier
                ),
            shape = RoundedCornerShape(20.dp),
            color = if (isGlass) petalGroupSurfaceColor() else cs.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }

    @Composable
    fun Line(label: String, value: String, warn: Boolean = false, sub: String? = null) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(label, style = ty.bodyMedium, fontWeight = FontWeight.Medium)
                sub?.let {
                    Text(it, style = ty.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                value,
                style = ty.labelLarge,
                color = if (warn) cs.error else cs.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    // Built with
    Section("Built with") {
        Line("Frameworks", rep.frameworks.joinToString().ifEmpty { "Native Android" })
        Line("Target SDK", "Android ${PetalApkInsights.androidName(rep.targetSdk)} (API ${rep.targetSdk})")
        Line("Minimum SDK", "Android ${PetalApkInsights.androidName(rep.minSdk)} (API ${rep.minSdk})")
        if (rep.compileSdk > 0) Line("Compile SDK", "API ${rep.compileSdk}")
        Line("Classes & Code", "${rep.classCount} classes across ${rep.dexCount} DEX files")
    }

    // Trackers
    Section("Trackers • ${rep.trackers.size}") {
        if (rep.trackers.isEmpty()) {
            Text("No known analytics or ad trackers found in code.", style = ty.bodyMedium)
        } else {
            rep.trackers.forEach { (name, category) ->
                Line(name, category, warn = category in setOf("Ads", "Attribution"))
            }
        }
        Text(
            "Checked offline against signature database (Exodus Privacy standards).",
            style = ty.bodySmall,
            color = cs.onSurfaceVariant
        )
    }

    // Permissions
    Section("Permissions • ${rep.perms.size}") {
        if (rep.perms.isEmpty()) {
            Text("Requests no permissions.", style = ty.bodyMedium)
        }
        val displayedPerms = if (allPerms) rep.perms else rep.perms.take(8)
        displayedPerms.forEach { p ->
            val badges = listOfNotNull(
                "New".takeIf { p.isNew },
                "Sensitive".takeIf { p.dangerous }
            ).joinToString(" • ")
            Line(PetalApkInsights.permLabel(p.name), badges, warn = p.dangerous, sub = p.name)
        }
        if (rep.perms.size > 8) {
            TextButton(onClick = { allPerms = !allPerms }) {
                Text(if (allPerms) "Show fewer" else "Show all ${rep.perms.size} permissions")
            }
        }
    }

    // Components
    Section("Components") {
        rep.components.forEach { comp ->
            val exportedText = if (comp.exported > 0) " • ${comp.exported} exported" else ""
            Line(comp.kind, "${comp.total}$exportedText")
        }
    }

    // Native Libraries
    if (rep.libs.isNotEmpty()) {
        Section("Native Libraries • ${rep.libs.size}") {
            val displayedLibs = if (allLibs) rep.libs else rep.libs.take(8)
            displayedLibs.forEach { lib ->
                val isBadAlign = lib.abi in PetalApkInsights.ABI64 && lib.align != null && lib.align < PetalApkInsights.PAGE_16K
                val subText = listOfNotNull(
                    lib.known,
                    "Not 16 KB aligned".takeIf { isBadAlign }
                ).joinToString(" • ").ifEmpty { null }
                Line(
                    label = lib.name,
                    value = "${shortAbi(lib.abi)} • ${PetalApkInsights.formatSize(lib.size)}",
                    warn = isBadAlign,
                    sub = subText
                )
            }
            if (rep.libs.size > 8) {
                TextButton(onClick = { allLibs = !allLibs }) {
                    Text(if (allLibs) "Show fewer" else "Show all ${rep.libs.size} libraries")
                }
            }
        }
    }

    // Size Breakdown
    Section("Size Distribution") {
        val totalSize = rep.sizes.sumOf { it.second }.coerceAtLeast(1L)
        rep.sizes.forEach { (category, bytes) ->
            Column {
                Line(category, PetalApkInsights.formatSize(bytes))
                LinearProgressIndicator(
                    progress = { (bytes.toFloat() / totalSize).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(ExpressiveButtonShape)
                )
            }
        }
    }

    // Signing Certificate Fingerprint
    rep.signer?.let { fingerprint ->
        Section("Digital Signature") {
            Line(
                label = "SHA-256 Fingerprint",
                value = if (rep.debugSigned) "Debug Keystore" else "Release Certificate",
                warn = rep.debugSigned,
                sub = if (rep.debugSigned) "Signed with Android public debug key" else null
            )
            Text(
                text = fingerprint,
                style = ty.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = cs.onSurfaceVariant
            )
            TextButton(
                onClick = {
                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clip.setPrimaryClip(ClipData.newPlainText("SHA-256 Fingerprint", fingerprint))
                    PetalHapticEngine.getInstance(context).playIfEnabled(context, PetalHapticEngine.Pattern.CLICK, 0.7f)
                }
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Copy Fingerprint")
            }
        }
    }
}

private fun shortAbi(abi: String): String = when (abi) {
    "arm64-v8a" -> "arm64"
    "armeabi-v7a" -> "arm32"
    "armeabi" -> "arm (legacy)"
    else -> abi
}
