package com.petal.browser.browser

import android.content.Context
import android.content.SharedPreferences
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * PetalPromptDelegateWrapper
 * ──────────────────────────────────────────────────────────────────────────────
 * Wraps Petal's prompt UI with the same re-bind pattern as PetalContentDelegateWrapper.
 *
 * GeckoEngineSession (android-components) does NOT install a promptDelegate — the slot
 * is null until the app fills it. Petal takes sole ownership of every prompt method.
 * The only correctness requirement is that after an Activity recreation the delegate's
 * [activityProvider] and [incognitoProvider] lambdas point at the NEW activity/view
 * instead of the destroyed one. [rebind] updates those without replacing the delegate
 * on the live GeckoSession, so we avoid the double-wrap problem the other delegates faced.
 *
 * Marked [PetalOwnedDelegate] so that re-binding in [PetalTabViewController.bindTab]
 * detects existing instances and calls [rebind] instead of wrapping again.
 */
internal class PetalPromptDelegateWrapper(
    private val engine: GeckoSession.PromptDelegate? = null,
    private val session: GeckoSession,
    private val appContext: Context,
    private val sp: SharedPreferences,
    @Volatile private var incognitoProvider: () -> Boolean,
    @Volatile private var activityProvider: () -> android.app.Activity?
) : GeckoSession.PromptDelegate, PetalOwnedDelegate {

    /** Update the live-context suppliers when the host Activity is recreated. */
    fun rebind(
        newIncognitoProvider: () -> Boolean,
        newActivityProvider: () -> android.app.Activity?
    ) {
        this.incognitoProvider = newIncognitoProvider
        this.activityProvider = newActivityProvider
    }

    private fun inner(): GeckoSession.PromptDelegate =
        com.petal.browser.engine.gecko.PetalEngineBridge.createPromptDelegate(
            session,
            appContext,
            sp,
            incognitoProvider,
            activityProvider
        )

    override fun onAlertPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AlertPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val act = activityProvider()
        val res = if (act != null) inner().onAlertPrompt(session, prompt) else null
        return res ?: engine?.onAlertPrompt(session, prompt) ?: GeckoResult.fromValue(prompt.dismiss())
    }

    override fun onButtonPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ButtonPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val act = activityProvider()
        val res = if (act != null) inner().onButtonPrompt(session, prompt) else null
        return res ?: engine?.onButtonPrompt(session, prompt) ?: GeckoResult.fromValue(prompt.dismiss())
    }

    override fun onAuthPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AuthPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val act = activityProvider()
        val res = if (act != null) inner().onAuthPrompt(session, prompt) else null
        return res ?: engine?.onAuthPrompt(session, prompt) ?: GeckoResult.fromValue(prompt.dismiss())
    }

    override fun onTextPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.TextPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val act = activityProvider()
        return if (act != null) inner().onTextPrompt(session, prompt)
               else engine?.onTextPrompt(session, prompt) ?: GeckoResult.fromValue(prompt.dismiss())
    }

    override fun onChoicePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ChoicePrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val act = activityProvider()
        return if (act != null) inner().onChoicePrompt(session, prompt)
               else engine?.onChoicePrompt(session, prompt) ?: GeckoResult.fromValue(prompt.dismiss())
    }

    override fun onColorPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ColorPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val act = activityProvider()
        return if (act != null) inner().onColorPrompt(session, prompt)
               else engine?.onColorPrompt(session, prompt) ?: GeckoResult.fromValue(prompt.dismiss())
    }

    override fun onDateTimePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.DateTimePrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val act = activityProvider()
        return if (act != null) inner().onDateTimePrompt(session, prompt)
               else engine?.onDateTimePrompt(session, prompt) ?: GeckoResult.fromValue(prompt.dismiss())
    }

    override fun onSharePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.SharePrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val act = activityProvider()
        return if (act != null) inner().onSharePrompt(session, prompt)
               else engine?.onSharePrompt(session, prompt) ?: GeckoResult.fromValue(prompt.confirm(GeckoSession.PromptDelegate.SharePrompt.Result.ABORT))
    }

    override fun onPopupPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.PopupPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onPopupPrompt(session, prompt) ?: engine?.onPopupPrompt(session, prompt)

    override fun onBeforeUnloadPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val act = activityProvider()
        return if (act != null) inner().onBeforeUnloadPrompt(session, prompt)
               else engine?.onBeforeUnloadPrompt(session, prompt) ?: GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
    }

    override fun onRepostConfirmPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.RepostConfirmPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val act = activityProvider()
        return if (act != null) inner().onRepostConfirmPrompt(session, prompt)
               else engine?.onRepostConfirmPrompt(session, prompt) ?: GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
    }

    override fun onLoginSave(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSaveOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onLoginSave(session, request) ?: engine?.onLoginSave(session, request)

    override fun onLoginSelect(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSelectOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onLoginSelect(session, request) ?: engine?.onLoginSelect(session, request)

    override fun onFilePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FilePrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val act = activityProvider()
        return if (act != null) inner().onFilePrompt(session, prompt)
               else engine?.onFilePrompt(session, prompt) ?: GeckoResult.fromValue(prompt.dismiss())
    }

    override fun onCreditCardSave(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.CreditCardSaveOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        engine?.onCreditCardSave(session, request) ?: inner().onCreditCardSave(session, request)

    override fun onCreditCardSelect(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.CreditCardSelectOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        engine?.onCreditCardSelect(session, request) ?: inner().onCreditCardSelect(session, request)

    override fun onAddressSave(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.AddressSaveOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        engine?.onAddressSave(session, request) ?: inner().onAddressSave(session, request)

    override fun onAddressSelect(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.AddressSelectOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        engine?.onAddressSelect(session, request) ?: inner().onAddressSelect(session, request)

    override fun onSelectIdentityCredentialProvider(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.IdentityCredential.ProviderSelectorPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val act = activityProvider()
        return if (act != null) inner().onSelectIdentityCredentialProvider(session, prompt)
               else engine?.onSelectIdentityCredentialProvider(session, prompt) ?: GeckoResult.fromValue(prompt.dismiss())
    }

    override fun onSelectIdentityCredentialAccount(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.IdentityCredential.AccountSelectorPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val act = activityProvider()
        return if (act != null) inner().onSelectIdentityCredentialAccount(session, prompt)
               else engine?.onSelectIdentityCredentialAccount(session, prompt) ?: GeckoResult.fromValue(prompt.dismiss())
    }

    override fun onShowPrivacyPolicyIdentityCredential(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.IdentityCredential.PrivacyPolicyPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val act = activityProvider()
        return if (act != null) inner().onShowPrivacyPolicyIdentityCredential(session, prompt)
               else engine?.onShowPrivacyPolicyIdentityCredential(session, prompt) ?: GeckoResult.fromValue(prompt.dismiss())
    }
}
