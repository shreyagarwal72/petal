package com.petal.browser.account.mozilla

import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedEnvelope(
    val senderDeviceId: String,
    val sequenceNumber: Long,
    val ivBase64: String,
    val ciphertextBase64: String,
    val timestamp: Long = System.currentTimeMillis()
)

object CryptoEngine {

    private const val EC_CURVE = "secp256r1"
    private const val KEY_AGREEMENT_ALGO = "ECDH"
    private const val CIPHER_ALGO = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val HKDF_ALGO = "HmacSHA256"

    private val secureRandom = SecureRandom()

    fun generateKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec(EC_CURVE), secureRandom)
        return keyGen.generateKeyPair()
    }

    fun parsePublicKey(encodedBytes: ByteArray): PublicKey {
        val spec = X509EncodedKeySpec(encodedBytes)
        val factory = KeyFactory.getInstance("EC")
        return factory.generatePublic(spec)
    }

    fun parsePublicKeyBase64(base64Str: String): PublicKey {
        val bytes = Base64.getDecoder().decode(base64Str)
        return parsePublicKey(bytes)
    }

    fun deriveSharedSecret(privateKey: PrivateKey, peerPublicKey: PublicKey): SecretKey {
        val keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGO)
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        val rawSecret = keyAgreement.generateSecret()

        val salt = "petal-sync-v1-salt".toByteArray(Charsets.UTF_8)
        val info = "petal-sync-aes-gcm-key".toByteArray(Charsets.UTF_8)
        val aesKeyBytes = hkdf(rawSecret, salt, info, 32)
        return SecretKeySpec(aesKeyBytes, "AES")
    }

    fun deriveVerificationCode(sharedSecret: SecretKey): String {
        val mac = Mac.getInstance(HKDF_ALGO)
        mac.init(sharedSecret)
        val hash = mac.doFinal("petal-sync-sas-verification".toByteArray(Charsets.UTF_8))
        val num = ((hash[0].toInt() and 0x7F) shl 24) or
                  ((hash[1].toInt() and 0xFF) shl 16) or
                  ((hash[2].toInt() and 0xFF) shl 8) or
                  (hash[3].toInt() and 0xFF)
        val code = num % 1_000_000
        return String.format("%06d", code)
    }

    fun encrypt(plaintext: ByteArray, key: SecretKey): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance(CIPHER_ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return Pair(iv, ciphertext)
    }

    fun decrypt(iv: ByteArray, ciphertext: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_ALGO)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prkMac = Mac.getInstance(HKDF_ALGO)
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
        prkMac.init(SecretKeySpec(effectiveSalt, HKDF_ALGO))
        val prk = prkMac.doFinal(ikm)

        val infoMac = Mac.getInstance(HKDF_ALGO)
        infoMac.init(SecretKeySpec(prk, HKDF_ALGO))
        val okm = ByteArray(length)
        var previousT = ByteArray(0)
        var generated = 0
        var counter = 1.toByte()

        while (generated < length) {
            infoMac.reset()
            infoMac.update(previousT)
            infoMac.update(info)
            infoMac.update(counter)
            val t = infoMac.doFinal()
            val toCopy = minOf(t.size, length - generated)
            System.arraycopy(t, 0, okm, generated, toCopy)
            generated += toCopy
            previousT = t
            counter++
        }
        return okm
    }
}
