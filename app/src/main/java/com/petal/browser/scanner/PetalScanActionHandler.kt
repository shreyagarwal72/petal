package com.petal.browser.scanner

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.PersistableBundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import com.petal.browser.R
import com.petal.browser.activity.MainActivity
import com.petal.browser.haptics.PetalHapticEngine
import com.petal.browser.unit.PetalBrowserUtils
import com.petal.browser.view.PetalToast

/**
 * Handles user actions on [PetalScanResult] instances (Open, Copy, Share, Dial, Connect, etc.).
 */
object PetalScanActionHandler {

    /**
     * Triggers tactile feedback and optional audio beep on successful detection.
     */
    fun performDetectionFeedback(context: Context) {
        if (PetalScannerSettings.isVibrateEnabled(context)) {
            try {
                PetalHapticEngine.getInstance(context).play(PetalHapticEngine.Pattern.HEAVY_CLICK, 1.0f)
            } catch (_: Exception) {}
        }

        if (PetalScannerSettings.isBeepEnabled(context)) {
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                toneGen.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Opens a URL in the browser, either in the current tab, a new tab, or external intent.
     */
    fun openUrl(context: Context, url: String, inNewTab: Boolean = false) {
        try {
            val uri = Uri.parse(url)
            if (context is MainActivity) {
                if (inNewTab || context.currentAlbumController == null) {
                    context.addAlbum("Web Page", url, true)
                } else {
                    context.showAlbum(context.currentAlbumController, url)
                }
            } else {
                PetalBrowserUtils.intentURL(context, uri)
            }
        } catch (e: Exception) {
            PetalToast.show(context, "Could not open URL: ${e.message}", Toast.LENGTH_SHORT)
        }
    }

    /**
     * Copies text to the clipboard with sensitive masking on Android 13+ if sensitive.
     */
    fun copyToClipboard(context: Context, text: String, isSensitive: Boolean = false) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val clip = ClipData.newPlainText("Scanned Code", text)
            if (isSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            cm.setPrimaryClip(clip)
            PetalToast.show(context, context.getString(R.string.quick_tile_scanner_copied), Toast.LENGTH_SHORT)
        } catch (_: Exception) {}
    }

    /**
     * Shares scanned text through Android Sharesheet.
     */
    fun shareText(context: Context, text: String, title: String = "Share Scanned Code") {
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(sendIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {}
    }

    /**
     * Performs a web search using default search provider.
     */
    fun searchWeb(context: Context, query: String) {
        val searchUrl = "https://www.google.com/search?q=" + Uri.encode(query)
        openUrl(context, searchUrl, inNewTab = true)
    }

    /**
     * Attempts to connect to Wi-Fi via WifiNetworkSuggestion on API 29+,
     * or copies password and directs user to Wi-Fi settings.
     */
    fun connectWifi(context: Context, wifi: ScanPayload.Wifi) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                if (wifiManager != null) {
                    val builder = WifiNetworkSuggestion.Builder()
                        .setSsid(wifi.ssid)
                        .setIsHiddenSsid(wifi.hidden)

                    when (wifi.authType.uppercase()) {
                        "WPA", "WPA2", "WPA3", "WPA2-PSK", "WPA3-SAE" -> {
                            wifi.password?.let { builder.setWpa2Passphrase(it) }
                        }
                        "OPEN", "NOPASS" -> {}
                    }

                    val status = wifiManager.addNetworkSuggestions(listOf(builder.build()))
                    if (status == android.net.wifi.WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) {
                        PetalToast.show(context, "Wi-Fi suggestion added: ${wifi.ssid}", Toast.LENGTH_LONG)
                        return
                    }
                }
            }

            // Fallback: Copy password and open Wi-Fi settings
            if (!wifi.password.isNullOrEmpty()) {
                copyToClipboard(context, wifi.password, isSensitive = true)
                PetalToast.show(context, "Password copied. Select network in Settings.", Toast.LENGTH_LONG)
            }
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            PetalToast.show(context, "Unable to configure Wi-Fi: ${e.message}", Toast.LENGTH_SHORT)
        }
    }

    /**
     * Launches the system Contact creation intent.
     */
    fun addContact(context: Context, contact: ScanPayload.Contact) {
        try {
            val intent = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
                putExtra(ContactsContract.Intents.Insert.NAME, contact.name)
                contact.phone?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
                contact.email?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
                contact.org?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
                contact.note?.let { putExtra(ContactsContract.Intents.Insert.NOTES, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            PetalToast.show(context, "Unable to add contact: ${e.message}", Toast.LENGTH_SHORT)
        }
    }

    /**
     * Dials a phone number via Intent.ACTION_DIAL.
     */
    fun dialPhone(context: Context, phone: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    /**
     * Composes an SMS message via Intent.ACTION_SENDTO.
     */
    fun sendSms(context: Context, sms: ScanPayload.Sms) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(sms.number)}")).apply {
                sms.message?.let { putExtra("sms_body", it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    /**
     * Composes an email via Intent.ACTION_SENDTO.
     */
    fun composeEmail(context: Context, email: ScanPayload.Email) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(email.address)}")).apply {
                email.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                email.body?.let { putExtra(Intent.EXTRA_TEXT, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    /**
     * Opens geographic coordinates or location query in Maps.
     */
    fun openMap(context: Context, geo: ScanPayload.Geo) {
        try {
            val uriStr = if (!geo.query.isNullOrEmpty()) {
                "geo:${geo.latitude},${geo.longitude}?q=${Uri.encode(geo.query)}"
            } else {
                "geo:${geo.latitude},${geo.longitude}"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriStr)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    /**
     * Adds an event to the calendar via Intent.ACTION_INSERT.
     */
    fun addCalendarEvent(context: Context, cal: ScanPayload.Calendar) {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, cal.title)
                cal.startTime?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
                cal.endTime?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
                cal.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                cal.description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
