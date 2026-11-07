package com.petal.browser.browser

import android.content.Context
import android.content.SharedPreferences
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

    // Delegate every method to the stateless factory on PetalGeckoView, which builds
    // a fresh anonymous delegate from the suppliers. Because the suppliers are @Volatile
    // var fields above, each call already reads the current activity.
    // We re-create the inner delegate lazily to avoid capturing stale lambdas at construction time.

    private fun inner(): GeckoSession.PromptDelegate =
        com.petal.browser.view.PetalGeckoView.createPromptDelegate(
            session,
            appContext,
            sp,
            incognitoProvider,
            activityProvider
        )

    override fun onAlertPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AlertPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
        inner().onAlertPrompt(session, prompt)

    override fun onButtonPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ButtonPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> =
        inner().onButtonPrompt(session, prompt)

    override fun onAuthPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AuthPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onAuthPrompt(session, prompt)

    override fun onTextPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.TextPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onTextPrompt(session, prompt)

    override fun onChoicePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ChoicePrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onChoicePrompt(session, prompt)

    override fun onColorPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ColorPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onColorPrompt(session, prompt)

    override fun onDateTimePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.DateTimePrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onDateTimePrompt(session, prompt)

    override fun onSharePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.SharePrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onSharePrompt(session, prompt)

    override fun onPopupPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.PopupPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onPopupPrompt(session, prompt)

    override fun onBeforeUnloadPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onBeforeUnloadPrompt(session, prompt)

    override fun onRepostConfirmPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.RepostConfirmPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onRepostConfirmPrompt(session, prompt)

    override fun onLoginSave(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSaveOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onLoginSave(session, request)

    override fun onLoginSelect(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.LoginSelectOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onLoginSelect(session, request)

    override fun onFilePrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.FilePrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onFilePrompt(session, prompt)

    override fun onCreditCardSave(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.CreditCardSaveOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onCreditCardSave(session, request)

    override fun onCreditCardSelect(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.CreditCardSelectOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onCreditCardSelect(session, request)

    override fun onAddressSave(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.AddressSaveOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onAddressSave(session, request)

    override fun onAddressSelect(
        session: GeckoSession,
        request: GeckoSession.PromptDelegate.AutocompleteRequest<org.mozilla.geckoview.Autocomplete.AddressSelectOption>
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onAddressSelect(session, request)

    override fun onSelectIdentityCredentialProvider(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.IdentityCredential.ProviderSelectorPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onSelectIdentityCredentialProvider(session, prompt)

    override fun onSelectIdentityCredentialAccount(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.IdentityCredential.AccountSelectorPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onSelectIdentityCredentialAccount(session, prompt)

    override fun onShowPrivacyPolicyIdentityCredential(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.IdentityCredential.PrivacyPolicyPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? =
        inner().onShowPrivacyPolicyIdentityCredential(session, prompt)
}
