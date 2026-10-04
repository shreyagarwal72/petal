package com.petal.browser.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.petal.browser.R
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.ui.theme.PetalExpressiveTheme
import com.petal.browser.view.PetalToast
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.regex.Pattern

/**
 * PetalCastDevice represents a discovered DLNA / UPnP / Smart TV / Cast receiver.
 */
data class PetalCastDevice(
    val friendlyName: String,
    val controlUrl: String,
    val locationUrl: String,
    val manufacturer: String = "",
    val modelName: String = "",
)

private class CastDialogLifecycleOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry
}

/**
 * PetalCastManager
 * Direct in-app casting to Smart TVs, DLNA / UPnP media renderers, Roku, Samsung, LG,
 * and Chromecast-compatible receivers on local Wi-Fi without requiring external apps,
 * with an optional fallback to external media players if desired.
 */
object PetalCastManager {

    private var activeDialog: BottomSheetDialog? = null
    private var activeLifecycleOwner: CastDialogLifecycleOwner? = null

    @JvmStatic
    fun castMedia(context: Context, videoUrl: String?, title: String?) {
        if (videoUrl.isNullOrBlank()) {
            PetalToast.show(context, "No stream URL found to cast")
            return
        }

        val activity = context as? ComponentActivity
        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            showCastDialog(activity, videoUrl, title)
        } else {
            openExternalAppChooser(context, videoUrl, title)
        }
    }

    @JvmStatic
    fun openExternalAppChooser(context: Context, videoUrl: String?, title: String?) {
        if (videoUrl.isNullOrBlank()) return
        try {
            val uri = Uri.parse(videoUrl)
            val mimeType = when {
                videoUrl.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
                videoUrl.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
                videoUrl.contains(".webm", ignoreCase = true) -> "video/webm"
                videoUrl.contains(".mkv", ignoreCase = true) -> "video/x-matroska"
                else -> "video/*"
            }

            val castIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_TITLE, title ?: "Web Video")
                putExtra("title", title ?: "Web Video")
            }

            val chooser = Intent.createChooser(castIntent, context.getString(R.string.ui_cast_to_device)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            PetalToast.show(context, context.getString(R.string.ui_no_cast_devices_found))
        }
    }

    private fun showCastDialog(activity: ComponentActivity, videoUrl: String, title: String?) {
        activity.runOnUiThread {
            activeDialog?.dismiss()
            activeDialog = null
            activeLifecycleOwner?.let { it.registry.currentState = Lifecycle.State.DESTROYED }
            activeLifecycleOwner = null

            val dialog = BottomSheetDialog(activity)
            activeDialog = dialog

            val dialogLifecycleOwner = CastDialogLifecycleOwner()
            activeLifecycleOwner = dialogLifecycleOwner
            dialogLifecycleOwner.registry.currentState = Lifecycle.State.RESUMED

            val composeView = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(dialogLifecycleOwner)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    PetalExpressiveTheme {
                        PetalCastDeviceSheet(
                            videoUrl = videoUrl,
                            videoTitle = title ?: "Web Video",
                            onDismiss = {
                                try {
                                    if (dialog.isShowing) dialog.dismiss()
                                } catch (ignored: Exception) {}
                            },
                            onOpenExternal = {
                                try {
                                    if (dialog.isShowing) dialog.dismiss()
                                } catch (ignored: Exception) {}
                                openExternalAppChooser(activity, videoUrl, title)
                            }
                        )
                    }
                }
            }

            dialog.setContentView(composeView)
            dialog.setOnDismissListener {
                dialogLifecycleOwner.registry.currentState = Lifecycle.State.DESTROYED
                if (activeDialog == dialog) activeDialog = null
                if (activeLifecycleOwner == dialogLifecycleOwner) activeLifecycleOwner = null
            }
            dialog.show()
        }
    }

    /**
     * Discovers local UPnP / DLNA / Smart TV devices via SSDP M-SEARCH multicast.
     */
    suspend fun discoverLocalDevices(context: Context): List<PetalCastDevice> = withContext(Dispatchers.IO) {
        val discoveredDevices = mutableMapOf<String, PetalCastDevice>()
        var multicastLock: WifiManager.MulticastLock? = null

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("petal_ssdp_lock")?.apply {
                setReferenceCounted(true)
                acquire()
            }

            val ssdpQueries = listOf(
                "urn:schemas-upnp-org:service:AVTransport:1",
                "urn:schemas-upnp-org:device:MediaRenderer:1",
                "ssdp:all"
            )

            val socket = DatagramSocket()
            socket.soTimeout = 2500

            for (target in ssdpQueries) {
                val message = "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 2\r\n" +
                        "ST: $target\r\n\r\n"

                val sendPacket = DatagramPacket(
                    message.toByteArray(),
                    message.length,
                    InetAddress.getByName("239.255.255.250"),
                    1900
                )
                socket.send(sendPacket)
            }

            val rxBuffer = ByteArray(4096)
            val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < 2500) {
                try {
                    socket.receive(rxPacket)
                    val response = String(rxPacket.data, 0, rxPacket.length)
                    val location = extractHeader(response, "LOCATION")
                    if (!location.isNullOrBlank() && !discoveredDevices.containsKey(location)) {
                        val parsed = fetchAndParseDeviceXml(location)
                        if (parsed != null) {
                            discoveredDevices[location] = parsed
                        }
                    }
                } catch (timeout: java.net.SocketTimeoutException) {
                    break
                } catch (e: Exception) {
                    // Ignore packet parsing hiccups
                }
            }

            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            } catch (ignored: Exception) {}
        }

        discoveredDevices.values.toList()
    }

    private fun extractHeader(response: String, headerName: String): String? {
        val lines = response.split("\r\n")
        for (line in lines) {
            val parts = line.split(":", limit = 2)
            if (parts.size == 2 && parts[0].trim().equals(headerName, ignoreCase = true)) {
                return parts[1].trim()
            }
        }
        return null
    }

    private fun fetchAndParseDeviceXml(locationUrl: String): PetalCastDevice? {
        return try {
            val url = URL(locationUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1500
                readTimeout = 1500
                requestMethod = "GET"
            }
            if (connection.responseCode != 200) return null

            val xml = connection.inputStream.bufferedReader().use(BufferedReader::readText)

            val friendlyName = extractTagValue(xml, "friendlyName") ?: return null
            val manufacturer = extractTagValue(xml, "manufacturer") ?: ""
            val modelName = extractTagValue(xml, "modelName") ?: ""

            // Find AVTransport control URL
            var controlUrl = extractControlUrl(xml, "AVTransport")
            if (controlUrl.isNullOrBlank()) {
                controlUrl = "/upnp/control/rendertr1"
            }

            // Normalize relative control URL
            val finalControlUrl = if (controlUrl.startsWith("http://") || controlUrl.startsWith("https://")) {
                controlUrl
            } else {
                val base = "${url.protocol}://${url.host}:${url.port}"
                if (controlUrl.startsWith("/")) "$base$controlUrl" else "$base/$controlUrl"
            }

            PetalCastDevice(
                friendlyName = friendlyName,
                controlUrl = finalControlUrl,
                locationUrl = locationUrl,
                manufacturer = manufacturer,
                modelName = modelName
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun extractTagValue(xml: String, tag: String): String? {
        val pattern = Pattern.compile("<$tag>(.*?)</$tag>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val matcher = pattern.matcher(xml)
        return if (matcher.find()) matcher.group(1)?.trim() else null
    }

    private fun extractControlUrl(xml: String, serviceTypeKeyword: String): String? {
        val servicePattern = Pattern.compile("<service>(.*?)</service>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val matcher = servicePattern.matcher(xml)
        while (matcher.find()) {
            val block = matcher.group(1) ?: continue
            if (block.contains(serviceTypeKeyword, ignoreCase = true)) {
                return extractTagValue(block, "controlURL")
            }
        }
        return null
    }

    /**
     * Issues UPnP AVTransport SetAVTransportURI & Play commands directly to the device.
     */
    suspend fun playOnDevice(device: PetalCastDevice, videoUrl: String, title: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. SetAVTransportURI
            val escapedUrl = videoUrl.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            val escapedTitle = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

            val didlMetadata = "&lt;DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"&gt;&lt;item id=\"0\" parentID=\"-1\" restricted=\"1\"&gt;&lt;dc:title&gt;$escapedTitle&lt;/dc:title&gt;&lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;&lt;res protocolInfo=\"http-get:*:video/*:*\"&gt;$escapedUrl&lt;/res&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;"

            val setUriSoap = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                    "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                    "<s:Body>" +
                    "<u:SetAVTransportURI xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                    "<InstanceID>0</InstanceID>" +
                    "<CurrentURI>$escapedUrl</CurrentURI>" +
                    "<CurrentURIMetaData>$didlMetadata</CurrentURIMetaData>" +
                    "</u:SetAVTransportURI>" +
                    "</s:Body>" +
                    "</s:Envelope>"

            sendSoap(device.controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI", setUriSoap)

            // 2. Play
            val playSoap = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                    "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                    "<s:Body>" +
                    "<u:Play xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">" +
                    "<InstanceID>0</InstanceID>" +
                    "<Speed>1</Speed>" +
                    "</u:Play>" +
                    "</s:Body>" +
                    "</s:Envelope>"

            sendSoap(device.controlUrl, "urn:schemas-upnp-org:service:AVTransport:1#Play", playSoap)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun sendSoap(controlUrl: String, soapAction: String, body: String): Boolean {
        return try {
            val url = URL(controlUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                setRequestProperty("SOAPACTION", "\"$soapAction\"")
            }
            conn.outputStream.use { os: OutputStream ->
                os.write(body.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        }
    }
}

@Composable
fun PetalCastDeviceSheet(
    videoUrl: String,
    videoTitle: String,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isScanning by remember { mutableStateOf(true) }
    var devices by remember { mutableStateOf<List<PetalCastDevice>>(emptyList()) }
    var castingDeviceName by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val scanDevices = {
        isScanning = true
        scope.launch {
            devices = PetalCastManager.discoverLocalDevices(context)
            isScanning = false
        }
    }

    LaunchedEffect(Unit) {
        scanDevices()
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
                    .align(Alignment.CenterHorizontally),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Title Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.icon_cast),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ui_cast_to_device),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = videoTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = {
                        PetalHapticEngine.getInstance(context).playClick(context)
                        scanDevices()
                    },
                    enabled = !isScanning,
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = if (isScanning) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = {
                        PetalHapticEngine.getInstance(context).playClick(context)
                        onDismiss()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isScanning && devices.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.ui_searching_for_devices),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (devices.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.ui_no_cast_devices_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(devices) { device ->
                        val isThisCasting = castingDeviceName == device.friendlyName
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    PetalHapticEngine.getInstance(context).playClick(context)
                                    castingDeviceName = device.friendlyName
                                    scope.launch {
                                        val success = PetalCastManager.playOnDevice(device, videoUrl, videoTitle)
                                        if (success) {
                                            PetalToast.show(context, context.getString(R.string.ui_casting_to_device, device.friendlyName))
                                            onDismiss()
                                        } else {
                                            PetalToast.show(context, "Could not start stream on ${device.friendlyName}. Opening app chooser…")
                                            onOpenExternal()
                                        }
                                        castingDeviceName = null
                                    }
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Tv,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.friendlyName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (device.modelName.isNotBlank() || device.manufacturer.isNotBlank()) {
                                        Text(
                                            text = listOf(device.manufacturer, device.modelName).filter { it.isNotBlank() }.joinToString(" • "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (isThisCasting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Open with external apps button
            OutlinedButton(
                onClick = {
                    PetalHapticEngine.getInstance(context).playClick(context)
                    onOpenExternal()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.ui_open_with_other_apps))
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
