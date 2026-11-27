package com.petal.browser.engine.gecko

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.petal.browser.R
import com.petal.browser.passwords.PetalCredential
import com.petal.browser.passwords.PetalCredentialVault
import com.petal.browser.passwords.PetalSavePasswordSheet
import com.petal.browser.ui.components.PetalChoiceOption
import com.petal.browser.ui.components.PetalExpressivePromptBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.prompt.Choice
import mozilla.components.concept.engine.prompt.PromptRequest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * PetalBrowserPromptBridge
 * ─────────────────────────────────────────────────────────────────────────────
 * Official Mozilla Components 159.0.1 prompt view contract implementation.
 * Listens for [PromptRequest] instances in [BrowserStore] and displays Petal's
 * Liquid Glass and Material 3 Expressive bottom sheets and dialogs.
 */
class PetalBrowserPromptBridge(
    private val activity: FragmentActivity,
    private val store: BrowserStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handledPromptUids = ConcurrentHashMap.newKeySet<String>()
    private var collectionJob: kotlinx.coroutines.Job? = null

    fun start() {
        if (collectionJob?.isActive == true) return
        collectionJob = scope.launch {
            var lastTabId: String? = null
            store.stateFlow
                .map { state -> state.selectedTab?.let { tab -> tab.id to tab.content.promptRequests } }
                .distinctUntilChanged()
                .collect { pair ->
                    if (pair == null) {
                        handledPromptUids.clear()
                        lastTabId = null
                        return@collect
                    }
                    val (tabId, requests) = pair
                    if (lastTabId != tabId) {
                        handledPromptUids.clear()
                        lastTabId = tabId
                    }
                    val nextRequest = requests.firstOrNull() ?: return@collect
                    if (handledPromptUids.add(nextRequest.uid)) {
                        presentPrompt(tabId, nextRequest)
                    }
                }
        }
    }

    fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        handledPromptUids.clear()
    }

    private fun consume(tabId: String, request: PromptRequest) {
        store.dispatch(ContentAction.ConsumePromptRequestAction(tabId, request))
        handledPromptUids.remove(request.uid)
    }

    private fun presentPrompt(tabId: String, request: PromptRequest) {
        if (activity.isFinishing || (android.os.Build.VERSION.SDK_INT >= 17 && activity.isDestroyed)) {
            handledPromptUids.remove(request.uid)
            return
        }

        when (request) {
            is PromptRequest.Alert -> {
                val appName = activity.getString(R.string.app_name)
                PetalExpressivePromptBridge.showAlert(
                    activity,
                    request.title ?: appName,
                    request.message ?: ""
                ) {
                    request.onConfirm(false)
                    consume(tabId, request)
                }
            }

            is PromptRequest.Confirm -> {
                val appName = activity.getString(R.string.app_name)
                PetalExpressivePromptBridge.showConfirm(
                    activity,
                    request.title ?: appName,
                    request.message ?: "",
                    onConfirm = {
                        request.onConfirmPositiveButton(false)
                        consume(tabId, request)
                    },
                    onCancel = {
                        request.onConfirmNegativeButton(false)
                        consume(tabId, request)
                    }
                )
            }

            is PromptRequest.TextPrompt -> {
                val appName = activity.getString(R.string.app_name)
                PetalExpressivePromptBridge.showPrompt(
                    activity,
                    request.title ?: appName,
                    request.inputLabel ?: "",
                    request.inputValue,
                    onConfirm = { value ->
                        request.onConfirm(false, value)
                        consume(tabId, request)
                    },
                    onCancel = {
                        request.onDismiss()
                        consume(tabId, request)
                    }
                )
            }

            is PromptRequest.SingleChoice -> {
                val rows = request.choices.map { choice ->
                    PetalChoiceOption(
                        id = choice.id,
                        label = choice.label,
                        selected = choice.selected,
                        disabled = !choice.enable,
                        isSeparator = choice.isASeparator
                    )
                }
                PetalExpressivePromptBridge.showChoice(
                    activity,
                    null,
                    rows,
                    multiple = false,
                    onConfirm = { selectedIds ->
                        val selectedId = selectedIds.firstOrNull()
                        val choice = request.choices.firstOrNull { it.id == selectedId }
                        if (choice != null) {
                            request.onConfirm(choice)
                        } else {
                            request.onDismiss()
                        }
                        consume(tabId, request)
                    },
                    onCancel = {
                        request.onDismiss()
                        consume(tabId, request)
                    }
                )
            }

            is PromptRequest.MultipleChoice -> {
                val rows = request.choices.map { choice ->
                    PetalChoiceOption(
                        id = choice.id,
                        label = choice.label,
                        selected = choice.selected,
                        disabled = !choice.enable,
                        isSeparator = choice.isASeparator
                    )
                }
                PetalExpressivePromptBridge.showChoice(
                    activity,
                    null,
                    rows,
                    multiple = true,
                    onConfirm = { selectedIds ->
                        val selected = request.choices.filter { selectedIds.contains(it.id) }.toTypedArray()
                        request.onConfirm(selected)
                        consume(tabId, request)
                    },
                    onCancel = {
                        request.onDismiss()
                        consume(tabId, request)
                    }
                )
            }

            is PromptRequest.MenuChoice -> {
                val rows = request.choices.map { choice ->
                    PetalChoiceOption(
                        id = choice.id,
                        label = choice.label,
                        selected = choice.selected,
                        disabled = !choice.enable,
                        isSeparator = choice.isASeparator
                    )
                }
                PetalExpressivePromptBridge.showChoice(
                    activity,
                    null,
                    rows,
                    multiple = false,
                    onConfirm = { selectedIds ->
                        val selectedId = selectedIds.firstOrNull()
                        val choice = request.choices.firstOrNull { it.id == selectedId }
                        if (choice != null) {
                            request.onConfirm(choice)
                        } else {
                            request.onDismiss()
                        }
                        consume(tabId, request)
                    },
                    onCancel = {
                        request.onDismiss()
                        consume(tabId, request)
                    }
                )
            }

            is PromptRequest.Color -> {
                PetalExpressivePromptBridge.showColor(
                    activity,
                    null,
                    request.defaultColor,
                    onConfirm = { hex ->
                        request.onConfirm(hex)
                        consume(tabId, request)
                    },
                    onCancel = {
                        request.onDismiss()
                        consume(tabId, request)
                    }
                )
            }

            is PromptRequest.TimeSelection -> {
                val (mode, pattern) = when (request.type) {
                    PromptRequest.TimeSelection.Type.TIME -> "time" to "HH:mm"
                    PromptRequest.TimeSelection.Type.DATE_AND_TIME -> "datetime-local" to "yyyy-MM-dd'T'HH:mm"
                    PromptRequest.TimeSelection.Type.MONTH -> "month" to "yyyy-MM"
                    else -> "date" to "yyyy-MM-dd"
                }
                val format = SimpleDateFormat(pattern, Locale.US)
                val initialStr = request.initialDate?.let { format.format(it) }
                val minStr = request.minimumDate?.let { format.format(it) }
                val maxStr = request.maximumDate?.let { format.format(it) }

                PetalExpressivePromptBridge.showDateTime(
                    activity,
                    request.title,
                    mode,
                    initialStr,
                    minStr,
                    maxStr,
                    onConfirm = { valueStr ->
                        try {
                            val parsedDate = format.parse(valueStr)
                            if (parsedDate != null) request.onConfirm(parsedDate) else request.onDismiss()
                        } catch (_: Throwable) {
                            request.onDismiss()
                        }
                        consume(tabId, request)
                    },
                    onCancel = {
                        request.onDismiss()
                        consume(tabId, request)
                    }
                )
            }

            is PromptRequest.Authentication -> {
                val appName = activity.getString(R.string.app_name)
                val dialogTitle = request.title ?: appName
                PetalExpressivePromptBridge.showAuth(
                    activity,
                    dialogTitle,
                    request.message ?: "Sign In",
                    request.onlyShowPassword,
                    request.userName,
                    onConfirm = { user, pass ->
                        request.onConfirm(user, pass)
                        consume(tabId, request)
                    },
                    onCancel = {
                        request.onDismiss()
                        consume(tabId, request)
                    }
                )
            }

            is PromptRequest.Popup -> {
                val sp = PreferenceManager.getDefaultSharedPreferences(activity)
                val blockPopups = sp.getBoolean("sp_block_popups", sp.getBoolean("profileStandard_javascriptPopUp", true))
                val target = request.targetUri
                val host = try { if (!target.isNullOrBlank()) android.net.Uri.parse(target).host else null } catch (_: Throwable) { null }
                val allowedHosts = sp.getStringSet("sp_popup_allowed_domains", emptySet()) ?: emptySet()

                if (host != null && allowedHosts.contains(host)) {
                    request.onAllow()
                    consume(tabId, request)
                    return
                }

                val isIncognito = store.state.selectedTab?.content?.private ?: false
                if (blockPopups) {
                    val mainAct = activity as? com.petal.browser.activity.MainActivity
                    if (mainAct != null && !target.isNullOrBlank()) {
                        com.petal.browser.compose.menu.PetalPopupBlockedBridge.show(
                            activity = mainAct,
                            targetUrl = target,
                            isIncognito = isIncognito,
                            onAllowOnce = {
                                request.onAllow()
                                consume(tabId, request)
                            },
                            onAlwaysAllow = {
                                if (host != null && !isIncognito) {
                                    val updated = (sp.getStringSet("sp_popup_allowed_domains", emptySet()) ?: emptySet()) + host
                                    sp.edit().putStringSet("sp_popup_allowed_domains", updated).apply()
                                }
                                request.onAllow()
                                consume(tabId, request)
                            },
                            onDismiss = {
                                request.onDeny()
                                consume(tabId, request)
                            }
                        )
                    } else {
                        request.onDeny()
                        consume(tabId, request)
                    }
                } else {
                    request.onAllow()
                    consume(tabId, request)
                }
            }

            is PromptRequest.Share -> {
                try {
                    val shareData = request.data
                    val body = listOf(shareData.text, shareData.url).filter { !it.isNullOrBlank() }.joinToString("\n")
                    if (body.isBlank()) {
                        request.onFailure()
                        consume(tabId, request)
                        return
                    }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, body)
                        if (!shareData.title.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, shareData.title)
                    }
                    activity.startActivity(Intent.createChooser(send, null))
                    request.onSuccess()
                } catch (_: Throwable) {
                    request.onFailure()
                } finally {
                    consume(tabId, request)
                }
            }

            is PromptRequest.BeforeUnload -> {
                val appName = activity.getString(R.string.app_name)
                PetalExpressivePromptBridge.showConfirm(
                    activity,
                    appName,
                    activity.getString(R.string.hint_before_unload),
                    onConfirm = {
                        request.onLeave()
                        consume(tabId, request)
                    },
                    onCancel = {
                        request.onStay()
                        consume(tabId, request)
                    }
                )
            }

            is PromptRequest.Repost -> {
                val appName = activity.getString(R.string.app_name)
                PetalExpressivePromptBridge.showConfirm(
                    activity,
                    appName,
                    activity.getString(R.string.hint_repost_confirm),
                    onConfirm = {
                        request.onConfirm()
                        consume(tabId, request)
                    },
                    onCancel = {
                        request.onDismiss()
                        consume(tabId, request)
                    }
                )
            }

            is PromptRequest.SaveLoginPrompt -> {
                val tab = store.state.tabs.firstOrNull { it.id == tabId }
                val loginEntry = request.logins.firstOrNull()
                if (tab?.content?.private == true || loginEntry == null) {
                    request.onDismiss()
                    consume(tabId, request)
                    return
                }

                val rawOrigin = loginEntry.origin.ifBlank { loginEntry.formActionOrigin.orEmpty() }
                val domain = PetalCredentialVault.cleanDomain(rawOrigin)
                val username = loginEntry.username.orEmpty()
                val password = loginEntry.password.orEmpty()
                if (password.isBlank() || domain.isBlank()) {
                    request.onDismiss()
                    consume(tabId, request)
                    return
                }

                PetalCredentialVault.init(activity)
                if (PetalCredentialVault.isNeverSave(activity, domain)) {
                    request.onDismiss()
                    consume(tabId, request)
                    return
                }

                val existing = PetalCredentialVault.findByDomainAndUsername(domain, username)
                if (existing != null && existing.password == password) {
                    PetalCredentialVault.markUsed(existing.id)
                    request.onDismiss()
                    consume(tabId, request)
                    return
                }

                PetalSavePasswordSheet.show(
                    activity = activity,
                    domain = domain,
                    initialUsername = username,
                    initialPassword = password,
                    onSave = { finalUser, finalPass ->
                        val guid = existing?.id ?: loginEntry.guid ?: UUID.randomUUID().toString()
                        request.onConfirm(loginEntry.copy(guid = guid, username = finalUser, password = finalPass))
                        consume(tabId, request)
                    },
                    onNeverSave = {
                        PetalCredentialVault.addNeverSaveDomain(activity, domain)
                        request.onDismiss()
                        consume(tabId, request)
                    },
                    onDismiss = {
                        request.onDismiss()
                        consume(tabId, request)
                    }
                )
            }

            is PromptRequest.SelectLoginPrompt -> {
                if (request.logins.isEmpty()) {
                    request.onDismiss()
                    consume(tabId, request)
                    return
                }
                if (request.logins.size == 1) {
                    val onlyLogin = request.logins.first()
                    PetalExpressivePromptBridge.showConfirm(
                        activity,
                        "Autofill Account",
                        "Sign in as ${onlyLogin.username.orEmpty().ifBlank { "saved account" }} on ${onlyLogin.origin}?",
                        onConfirm = {
                            onlyLogin.guid?.let { PetalCredentialVault.markUsed(it) }
                            request.onConfirm(onlyLogin)
                            consume(tabId, request)
                        },
                        onCancel = {
                            request.onDismiss()
                            consume(tabId, request)
                        }
                    )
                } else {
                    val options = request.logins.mapIndexed { index, login ->
                        PetalChoiceOption(
                            id = index.toString(),
                            label = login.username.orEmpty().ifBlank { "Account ${index + 1}" },
                            selected = index == 0
                        )
                    }
                    PetalExpressivePromptBridge.showChoice(
                        activity,
                        "Select Account",
                        options,
                        multiple = false,
                        onConfirm = { selectedIds ->
                            val selectedIndex = selectedIds.firstOrNull()?.toIntOrNull() ?: 0
                            val chosen = request.logins.getOrNull(selectedIndex) ?: request.logins.first()
                            chosen.guid?.let { PetalCredentialVault.markUsed(it) }
                            request.onConfirm(chosen)
                            consume(tabId, request)
                        },
                        onCancel = {
                            request.onDismiss()
                            consume(tabId, request)
                        }
                    )
                }
            }

            else -> {
                // For any other prompt request types (e.g. SaveCreditCard, SaveAddress), dismiss gracefully
                if (request is PromptRequest.Dismissible) {
                    request.onDismiss()
                }
                consume(tabId, request)
            }
        }
    }
}
