package com.petal.browser.account

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.preference.PreferenceManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import org.json.JSONObject

enum class AvatarType { PRESET, GALLERY_URI, GOOGLE_URL }

data class GoogleUserProfile(
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val avatarType: AvatarType = AvatarType.PRESET,
    val avatarPresetId: String = "app_icon",
    val customAvatarUri: String? = null,
    val isSignedIn: Boolean = false,
    val globalGoogleLogin: Boolean = true
)

sealed class GoogleSignInResult {
    data class Success(val profile: GoogleUserProfile) : GoogleSignInResult()
    data class Failure(val message: String) : GoogleSignInResult()
}

object GoogleAccountManager {

    // Web application OAuth client ID from Google Cloud Console.
    // Credential Manager uses this as the audience for the ID token it requests -
    // required even though this app has no backend of its own.
    private const val WEB_CLIENT_ID =
        "755813875491-tfaor37ei7a72lc5g0ghachduetf9fj6.apps.googleusercontent.com"

    private const val KEY_IS_SIGNED_IN = "sp_google_account_signed_in"
    private const val KEY_EMAIL = "sp_google_account_email"
    private const val KEY_DISPLAY_NAME = "sp_google_account_display_name"
    private const val KEY_AVATAR_URL = "sp_google_account_avatar_url"
    private const val KEY_AVATAR_TYPE = "sp_user_avatar_type"
    private const val KEY_AVATAR_PRESET = "sp_user_avatar_preset"
    private const val KEY_CUSTOM_AVATAR_URI = "sp_user_custom_avatar_uri"
    private const val KEY_GLOBAL_GOOGLE_LOGIN = "sp_global_google_login"

    val builtinAvatarPresets = listOf(
        "app_icon" to "App Icon (Default)",
        "petal_flower" to "Petal",
        "cosmic_star" to "Cosmic Star",
        "cyber_shield" to "Cyber Shield",
        "rocket_boost" to "Rocket",
        "ocean_wave" to "Ocean",
        "ninja_cat" to "Ninja",
        "sparkle" to "Sparkles",
        "bot_avatar" to "Cyber Bot"
    )

    var currentProfile by mutableStateOf(
        GoogleUserProfile(
            email = "user@petalbrowser.org",
            displayName = "Petal Explorer",
            isSignedIn = false
        )
    )
        private set

    fun init(context: Context) {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            val isSignedIn = sp.getBoolean(KEY_IS_SIGNED_IN, false)
            val email = sp.getString(KEY_EMAIL, "user@petalbrowser.org") ?: "user@petalbrowser.org"
            val displayName = sp.getString(KEY_DISPLAY_NAME, "Petal Explorer") ?: "Petal Explorer"
            val avatarUrl = sp.getString(KEY_AVATAR_URL, null)
            val avatarTypeStr = sp.getString(KEY_AVATAR_TYPE, AvatarType.PRESET.name) ?: AvatarType.PRESET.name
            val avatarType = try { AvatarType.valueOf(avatarTypeStr) } catch (_: Throwable) { AvatarType.PRESET }
            val avatarPresetId = sp.getString(KEY_AVATAR_PRESET, "petal_flower") ?: "petal_flower"
            var customAvatarUri = sp.getString(KEY_CUSTOM_AVATAR_URI, null)
            val globalGoogleLogin = sp.getBoolean(KEY_GLOBAL_GOOGLE_LOGIN, true)

            // Ensure custom avatar is permanently stored in internal filesDir (never deleted by cache clear)
            if (avatarType == AvatarType.GALLERY_URI) {
                val permanentFile = java.io.File(context.filesDir, "petal_user_avatar.png")
                if (permanentFile.exists() && permanentFile.length() > 0) {
                    customAvatarUri = android.net.Uri.fromFile(permanentFile).toString()
                } else if (!customAvatarUri.isNullOrEmpty()) {
                    try {
                        val uri = android.net.Uri.parse(customAvatarUri)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            java.io.FileOutputStream(permanentFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (permanentFile.exists() && permanentFile.length() > 0) {
                            customAvatarUri = android.net.Uri.fromFile(permanentFile).toString()
                            sp.edit().putString(KEY_CUSTOM_AVATAR_URI, customAvatarUri).apply()
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            }

            currentProfile = GoogleUserProfile(
                email = email,
                displayName = displayName,
                avatarUrl = avatarUrl,
                avatarType = avatarType,
                avatarPresetId = avatarPresetId,
                customAvatarUri = customAvatarUri,
                isSignedIn = isSignedIn,
                globalGoogleLogin = globalGoogleLogin
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun updateDisplayName(context: Context, newName: String) {
        try {
            val trimmed = newName.trim().take(15).ifEmpty { "Petal Explorer" }
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit().putString(KEY_DISPLAY_NAME, trimmed).apply()
            currentProfile = currentProfile.copy(displayName = trimmed)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun updateAvatarPreset(context: Context, presetId: String) {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit()
                .putString(KEY_AVATAR_TYPE, AvatarType.PRESET.name)
                .putString(KEY_AVATAR_PRESET, presetId)
                .apply()
            currentProfile = currentProfile.copy(
                avatarType = AvatarType.PRESET,
                avatarPresetId = presetId
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun saveCroppedAvatar(context: Context, croppedBitmap: android.graphics.Bitmap) {
        try {
            val permanentFile = java.io.File(context.filesDir, "petal_user_avatar.png")
            java.io.FileOutputStream(permanentFile).use { out ->
                croppedBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
            val persistentUriString = android.net.Uri.fromFile(permanentFile).toString()
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit()
                .putString(KEY_AVATAR_TYPE, AvatarType.GALLERY_URI.name)
                .putString(KEY_CUSTOM_AVATAR_URI, persistentUriString)
                .apply()

            currentProfile = currentProfile.copy(
                avatarType = AvatarType.GALLERY_URI,
                customAvatarUri = persistentUriString
            )
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun updateAvatarGalleryUri(context: Context, uriString: String) {
        try {
            val uri = android.net.Uri.parse(uriString)
            val permanentFile = java.io.File(context.filesDir, "petal_user_avatar.png")

            if (uri.path != permanentFile.absolutePath) {
                if (uri.scheme == "content") {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Throwable) {}
                }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(permanentFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val persistentUriString = android.net.Uri.fromFile(permanentFile).toString()

            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit()
                .putString(KEY_AVATAR_TYPE, AvatarType.GALLERY_URI.name)
                .putString(KEY_CUSTOM_AVATAR_URI, persistentUriString)
                .apply()

            currentProfile = currentProfile.copy(
                avatarType = AvatarType.GALLERY_URI,
                customAvatarUri = persistentUriString
            )
        } catch (e: Throwable) {
            e.printStackTrace()
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit()
                .putString(KEY_AVATAR_TYPE, AvatarType.GALLERY_URI.name)
                .putString(KEY_CUSTOM_AVATAR_URI, uriString)
                .apply()
            currentProfile = currentProfile.copy(
                avatarType = AvatarType.GALLERY_URI,
                customAvatarUri = uriString
            )
        }
    }

    fun setGlobalGoogleLogin(context: Context, enabled: Boolean) {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit().putBoolean(KEY_GLOBAL_GOOGLE_LOGIN, enabled).apply()
            currentProfile = currentProfile.copy(globalGoogleLogin = enabled)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun persistSignedInProfile(context: Context, email: String, displayName: String, avatarUrl: String?) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val effectiveAvatarType = if (!avatarUrl.isNullOrEmpty()) AvatarType.GOOGLE_URL else currentProfile.avatarType
        sp.edit()
            .putBoolean(KEY_IS_SIGNED_IN, true)
            .putString(KEY_EMAIL, email)
            .putString(KEY_DISPLAY_NAME, displayName)
            .putString(KEY_AVATAR_URL, avatarUrl)
            .putString(KEY_AVATAR_TYPE, effectiveAvatarType.name)
            .apply()

        currentProfile = currentProfile.copy(
            email = email,
            displayName = displayName,
            avatarUrl = avatarUrl,
            avatarType = effectiveAvatarType,
            isSignedIn = true
        )
    }

    // ---- Legacy Google Sign-In (play-services-auth) ----
    // Deprecated by Google in favor of Credential Manager, but used here as the active
    // sign-in path: it sidesteps a known, still-open Credential Manager bug
    // ("[16] Account reauth failed") that some devices hit even with fully correct
    // OAuth config. Uses the classic Intent + onActivityResult-style flow instead of
    // Credential Manager's IPC path.

    private fun buildLegacySignInClient(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .requestProfile()
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    /**
     * Returns the Intent to launch (via an ActivityResultLauncher) to start the legacy
     * Google Sign-In account picker + consent flow.
     */
    fun createLegacySignInIntent(context: Context): Intent {
        return buildLegacySignInClient(context).signInIntent
    }

    /**
     * Call this with the Intent returned in the ActivityResultLauncher's callback after
     * createLegacySignInIntent's Intent completes.
     */
    fun handleLegacySignInResult(context: Context, data: Intent?): GoogleSignInResult {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)

            val email = account.email
                ?: return GoogleSignInResult.Failure("Google did not return an email for this account")
            val displayName = account.displayName ?: email.substringBefore("@")
            val avatarUrl = account.photoUrl?.toString()

            persistSignedInProfile(context, email, displayName, avatarUrl)
            GoogleSignInResult.Success(currentProfile)
        } catch (e: ApiException) {
            e.printStackTrace()
            GoogleSignInResult.Failure("Sign-in failed (code ${e.statusCode})")
        } catch (e: Throwable) {
            e.printStackTrace()
            GoogleSignInResult.Failure(e.message ?: "Unknown sign-in error")
        }
    }

    /**
     * Signs out of the legacy Google Sign-In client. Call alongside signOut() when using
     * the legacy sign-in path.
     */
    suspend fun legacySignOut(context: Context) {
        try {
            buildLegacySignInClient(context).signOut()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        signOut(context)
    }

    /**
     * Launches the real Google Sign-In flow via Credential Manager. This shows Google's own
     * account picker + consent screen - the user explicitly chooses an account and approves
     * sharing their basic profile. Reads the standard OpenID claims (email, name, picture) from
     * the returned ID token to populate the account section. There is no backend for this app,
     * so the token is only decoded for display, never treated as a verified auth credential.
     *
     * Tries a silent, authorized-accounts-only request first, then falls back to the full
     * account picker if that fails. Trying both is a documented workaround for a known
     * Credential Manager / Play Services quirk that can throw
     * "[16] Account reauth failed" (GetCredentialCancellationException) even when the app's
     * OAuth config (SHA-1, client IDs, consent screen) is entirely correct - see
     * https://github.com/android/identity-samples/issues/90.
     *
     * Must be called with an Activity context (required by Credential Manager / Play Services
     * Auth to show the account picker UI).
     */
    suspend fun signIn(context: Context): GoogleSignInResult {
        val credentialManager = CredentialManager.create(context)

        val response = try {
            // Attempt 1: silent request for an already-authorized Google account.
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(WEB_CLIENT_ID)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            credentialManager.getCredential(context, request)
        } catch (e: GetCredentialException) {
            e.printStackTrace()
            try {
                // Attempt 2: fall back to the full account picker + consent screen.
                val signInOption = GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()
                credentialManager.getCredential(context, request)
            } catch (e2: GetCredentialException) {
                e2.printStackTrace()
                return GoogleSignInResult.Failure(e2.message ?: "Sign-in was cancelled or unavailable")
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            return GoogleSignInResult.Failure(e.message ?: "Unknown sign-in error")
        }

        return try {
            val credential = response.credential
            when (credential) {
                is CustomCredential -> {
                    if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val claims = decodeIdTokenClaims(googleIdTokenCredential.idToken)

                        val email = claims?.optString("email")?.takeIf { it.isNotBlank() }
                            ?: return GoogleSignInResult.Failure("Google did not return an email for this account")
                        val displayName = googleIdTokenCredential.displayName
                            ?: claims.optString("name").takeIf { it.isNotBlank() }
                            ?: email.substringBefore("@")
                        val avatarUrl = googleIdTokenCredential.profilePictureUri?.toString()
                            ?: claims.optString("picture").takeIf { it.isNotBlank() }

                        persistSignedInProfile(context, email, displayName, avatarUrl)
                        GoogleSignInResult.Success(currentProfile)
                    } else if (credential.type == androidx.credentials.PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL) {
                        // Successfully authenticated using Google Passkey / WebAuthn Public Key Credential
                        val rawJson = credential.data.getString("androidx.credentials.BUNDLE_KEY_AUTHENTICATION_RESPONSE_JSON")
                        val json = if (rawJson != null) JSONObject(rawJson) else null
                        val userEmail = json?.optJSONObject("response")?.optJSONObject("user")?.optString("name")
                            ?: currentProfile.email
                        persistSignedInProfile(context, userEmail, currentProfile.displayName, currentProfile.avatarUrl)
                        GoogleSignInResult.Success(currentProfile)
                    } else {
                        GoogleSignInResult.Failure("Unexpected credential type: ${credential.type}")
                    }
                }
                else -> GoogleSignInResult.Failure("Unsupported credential response")
            }
        } catch (e: GoogleIdTokenParsingException) {
            e.printStackTrace()
            GoogleSignInResult.Failure("Could not parse the credential returned by Google")
        } catch (e: Throwable) {
            e.printStackTrace()
            GoogleSignInResult.Failure(e.message ?: "Unknown sign-in error")
        }
    }

    /**
     * Decodes the (unverified) payload segment of the JWT ID token purely to read the standard
     * profile claims for display. This is safe only because there is no backend relying on this
     * value for authorization - it is used for UI display only. If this app ever adds a backend,
     * the ID token must be sent there and verified server-side instead of trusted client-side.
     */
    private fun decodeIdTokenClaims(idToken: String): JSONObject? {
        return try {
            val parts = idToken.split(".")
            if (parts.size < 2) return null
            val payload = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            JSONObject(String(payload, Charsets.UTF_8))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun signOut(context: Context) {
        try {
            val sp = PreferenceManager.getDefaultSharedPreferences(context)
            sp.edit()
                .putBoolean(KEY_IS_SIGNED_IN, false)
                .apply()
            currentProfile = currentProfile.copy(isSignedIn = false)

            // Also clears the Credential Manager's cached sign-in state so the account
            // picker is shown again next time instead of silently re-signing in.
            try {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }


}
