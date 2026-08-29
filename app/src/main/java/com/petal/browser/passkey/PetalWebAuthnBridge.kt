package com.petal.browser.passkey

import android.app.Activity
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * PetalWebAuthnBridge
 * ─────────────────────────────────────────────────────────────────────────────
 * Complete WebAuthn (FIDO2 / Passkeys) engine for Petal Browser.
 * Intercepts standard W3C navigator.credentials.create() and navigator.credentials.get()
 * calls from web pages and delegates directly to Android Credential Manager (biometrics,
 * security keys, and Google Password Manager passkeys).
 */
object PetalWebAuthnBridge {
    private const val TAG = "PetalWebAuthn"
    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    const val WEBAUTHN_POLYFILL_JS: String = """
    (function() {
        if (window.__petal_webauthn_installed) return;
        window.__petal_webauthn_installed = true;

        const callbacks = new Map();

        function base64urlToArrayBuffer(base64url) {
            if (!base64url) return new ArrayBuffer(0);
            let base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
            while (base64.length % 4) base64 += '=';
            const binary = atob(base64);
            const bytes = new Uint8Array(binary.length);
            for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
            return bytes.buffer;
        }

        function arrayBufferToBase64url(buffer) {
            if (!buffer) return '';
            const bytes = new Uint8Array(buffer);
            let binary = '';
            for (let i = 0; i < bytes.byteLength; i++) binary += String.fromCharCode(bytes[i]);
            return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
        }

        function bufferSourceToBase64url(val) {
            if (!val) return '';
            if (val instanceof ArrayBuffer) return arrayBufferToBase64url(val);
            if (ArrayBuffer.isView(val)) return arrayBufferToBase64url(val.buffer);
            if (typeof val === 'string') return val;
            return '';
        }

        window.__petal_webauthn_resolve = function(callbackId, jsonString) {
            const cb = callbacks.get(callbackId);
            if (!cb) return;
            callbacks.delete(callbackId);

            try {
                const data = typeof jsonString === 'string' ? JSON.parse(jsonString) : jsonString;
                const rawId = base64urlToArrayBuffer(data.rawId || data.id);
                const type = data.type || 'public-key';

                if (cb.isCreate) {
                    const clientDataJSON = base64urlToArrayBuffer(data.response.clientDataJSON);
                    const attestationObject = base64urlToArrayBuffer(data.response.attestationObject);
                    const transports = data.response.transports || ['internal', 'hybrid'];

                    const cred = {
                        id: data.id,
                        rawId: rawId,
                        type: type,
                        authenticatorAttachment: data.authenticatorAttachment || 'platform',
                        response: {
                            clientDataJSON: clientDataJSON,
                            attestationObject: attestationObject,
                            getTransports: function() { return transports; },
                            getAuthenticatorData: function() { return new ArrayBuffer(0); },
                            getPublicKey: function() { return null; },
                            getPublicKeyAlgorithm: function() { return -7; }
                        },
                        getClientExtensionResults: function() { return {}; }
                    };
                    cb.resolve(cred);
                } else {
                    const clientDataJSON = base64urlToArrayBuffer(data.response.clientDataJSON);
                    const authenticatorData = base64urlToArrayBuffer(data.response.authenticatorData);
                    const signature = base64urlToArrayBuffer(data.response.signature);
                    const userHandle = data.response.userHandle ? base64urlToArrayBuffer(data.response.userHandle) : null;

                    const cred = {
                        id: data.id,
                        rawId: rawId,
                        type: type,
                        authenticatorAttachment: data.authenticatorAttachment || 'platform',
                        response: {
                            clientDataJSON: clientDataJSON,
                            authenticatorData: authenticatorData,
                            signature: signature,
                            userHandle: userHandle
                        },
                        getClientExtensionResults: function() { return {}; }
                    };
                    cb.resolve(cred);
                }
            } catch (err) {
                cb.reject(new DOMException(err.message || 'Failed to parse credential response', 'NotSupportedError'));
            }
        };

        window.__petal_webauthn_reject = function(callbackId, errorName, errorMessage) {
            const cb = callbacks.get(callbackId);
            if (!cb) return;
            callbacks.delete(callbackId);
            cb.reject(new DOMException(errorMessage || 'Passkey operation cancelled or failed', errorName || 'NotAllowedError'));
        };

        if (window.PublicKeyCredential) {
            window.PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable = function() {
                return Promise.resolve(true);
            };
            window.PublicKeyCredential.isConditionalMediationAvailable = function() {
                return Promise.resolve(true);
            };
        }

        const origCredentials = navigator.credentials;
        if (origCredentials) {
            const origCreate = origCredentials.create.bind(origCredentials);
            const origGet = origCredentials.get.bind(origCredentials);

            navigator.credentials.create = function(options) {
                if (options && options.publicKey && window.PetalWebAuthn) {
                    return new Promise(function(resolve, reject) {
                        try {
                            const pk = options.publicKey;
                            const req = {
                                challenge: bufferSourceToBase64url(pk.challenge),
                                rp: pk.rp || { name: window.location.hostname, id: window.location.hostname },
                                user: {
                                    id: bufferSourceToBase64url(pk.user ? pk.user.id : ''),
                                    name: pk.user ? (pk.user.name || '') : '',
                                    displayName: pk.user ? (pk.user.displayName || '') : ''
                                },
                                pubKeyCredParams: pk.pubKeyCredParams || [{ type: 'public-key', alg: -7 }, { type: 'public-key', alg: -257 }],
                                timeout: pk.timeout || 60000,
                                attestation: pk.attestation || 'none',
                                authenticatorSelection: pk.authenticatorSelection || {}
                            };
                            if (pk.excludeCredentials && Array.isArray(pk.excludeCredentials)) {
                                req.excludeCredentials = pk.excludeCredentials.map(function(c) {
                                    return {
                                        id: bufferSourceToBase64url(c.id),
                                        type: c.type || 'public-key',
                                        transports: c.transports || []
                                    };
                                });
                            }

                            const callbackId = 'petal_create_' + Date.now() + '_' + Math.random().toString(36).substr(2, 8);
                            callbacks.set(callbackId, { resolve: resolve, reject: reject, isCreate: true });
                            window.PetalWebAuthn.createPasskey(JSON.stringify(req), callbackId);
                        } catch (err) {
                            reject(new DOMException(err.message || 'Passkey creation failed', 'NotSupportedError'));
                        }
                    });
                }
                return origCreate(options);
            };

            navigator.credentials.get = function(options) {
                if (options && options.publicKey && window.PetalWebAuthn) {
                    return new Promise(function(resolve, reject) {
                        try {
                            const pk = options.publicKey;
                            const req = {
                                challenge: bufferSourceToBase64url(pk.challenge),
                                rpId: pk.rpId || window.location.hostname,
                                timeout: pk.timeout || 60000,
                                userVerification: pk.userVerification || 'preferred'
                            };
                            if (pk.allowCredentials && Array.isArray(pk.allowCredentials)) {
                                req.allowCredentials = pk.allowCredentials.map(function(c) {
                                    return {
                                        id: bufferSourceToBase64url(c.id),
                                        type: c.type || 'public-key',
                                        transports: c.transports || []
                                    };
                                });
                            }

                            const callbackId = 'petal_get_' + Date.now() + '_' + Math.random().toString(36).substr(2, 8);
                            callbacks.set(callbackId, { resolve: resolve, reject: reject, isCreate: false });
                            window.PetalWebAuthn.getPasskey(JSON.stringify(req), callbackId);
                        } catch (err) {
                            reject(new DOMException(err.message || 'Passkey assertion failed', 'NotSupportedError'));
                        }
                    });
                }
                return origGet(options);
            };
        }
    })();
    """

    class WebAuthnJavascriptInterface(private val webView: WebView) {
        @JavascriptInterface
        fun createPasskey(requestJson: String, callbackId: String) {
            val context = webView.context
            val activity = (context as? Activity) ?: return

            bridgeScope.launch {
                try {
                    val credentialManager = CredentialManager.create(activity)
                    val createRequest = CreatePublicKeyCredentialRequest(requestJson = requestJson)
                    val response = credentialManager.createCredential(activity, createRequest)

                    if (response is CreatePublicKeyCredentialResponse) {
                        val regJson = response.registrationResponseJson
                        dispatchSuccess(webView, callbackId, regJson)
                    } else {
                        dispatchError(webView, callbackId, "NotSupportedError", "Unsupported passkey creation response")
                    }
                } catch (e: CreateCredentialCancellationException) {
                    Log.d(TAG, "Passkey creation cancelled by user", e)
                    dispatchError(webView, callbackId, "NotAllowedError", "Passkey creation was cancelled")
                } catch (e: CreateCredentialException) {
                    Log.e(TAG, "Passkey creation failed", e)
                    dispatchError(webView, callbackId, "NotAllowedError", e.message ?: "Passkey creation failed")
                } catch (e: Throwable) {
                    Log.e(TAG, "Unexpected passkey creation error", e)
                    dispatchError(webView, callbackId, "NotSupportedError", e.message ?: "Unexpected passkey error")
                }
            }
        }

        @JavascriptInterface
        fun getPasskey(requestJson: String, callbackId: String) {
            val context = webView.context
            val activity = (context as? Activity) ?: return

            bridgeScope.launch {
                try {
                    val credentialManager = CredentialManager.create(activity)
                    val getOption = GetPublicKeyCredentialOption(requestJson = requestJson)
                    val getRequest = GetCredentialRequest.Builder()
                        .addCredentialOption(getOption)
                        .build()
                    val response = credentialManager.getCredential(activity, getRequest)

                    val credential = response.credential
                    if (credential is PublicKeyCredential) {
                        val authJson = credential.authenticationResponseJson
                        dispatchSuccess(webView, callbackId, authJson)
                    } else if (credential is CustomCredential && credential.type == PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL) {
                        val rawJson = credential.data.getString("androidx.credentials.BUNDLE_KEY_AUTHENTICATION_RESPONSE_JSON")
                            ?: "{}"
                        dispatchSuccess(webView, callbackId, rawJson)
                    } else {
                        dispatchError(webView, callbackId, "NotAllowedError", "No matching passkey found")
                    }
                } catch (e: GetCredentialCancellationException) {
                    Log.d(TAG, "Passkey assertion cancelled by user", e)
                    dispatchError(webView, callbackId, "NotAllowedError", "Passkey authentication was cancelled")
                } catch (e: GetCredentialException) {
                    Log.e(TAG, "Passkey assertion failed", e)
                    dispatchError(webView, callbackId, "NotAllowedError", e.message ?: "Passkey authentication failed")
                } catch (e: Throwable) {
                    Log.e(TAG, "Unexpected passkey assertion error", e)
                    dispatchError(webView, callbackId, "NotSupportedError", e.message ?: "Unexpected passkey error")
                }
            }
        }

        private fun dispatchSuccess(webView: WebView, callbackId: String, json: String) {
            val escapedJson = JSONObject.quote(json)
            webView.post {
                webView.evaluateJavascript("window.__petal_webauthn_resolve('$callbackId', $escapedJson);", null)
            }
        }

        private fun dispatchError(webView: WebView, callbackId: String, errorName: String, message: String) {
            val escapedMsg = JSONObject.quote(message)
            webView.post {
                webView.evaluateJavascript("window.__petal_webauthn_reject('$callbackId', '$errorName', $escapedMsg);", null)
            }
        }
    }
}
