package com.petal.browser.compose.ai

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import javax.crypto.spec.SecretKeySpec

class PetalAiKeyStorageTest {

    private class FakePreferences : SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map

        override fun getString(key: String?, defValue: String?): String? {
            return map[key] as? String ?: defValue
        }

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return map[key] as? MutableSet<String> ?: defValues
        }

        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any?>()
            val toRemove = mutableSetOf<String>()
            var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) toRemove.add(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) map.clear()
                for (r in toRemove) map.remove(r)
                map.putAll(pending)
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Before
    fun setUp() {
        // Use deterministic 256-bit AES key for unit test encryption
        PetalAiKeyStorage.testSecretKey = SecretKeySpec(ByteArray(32) { 42.toByte() }, "AES")
    }

    @Test
    fun testKeyEncryptionAndDecryptionRoundtrip() {
        val vaultPrefs = FakePreferences()
        val originalKey = "sk-ant-test-api-key-12345"

        PetalAiKeyStorage.setKeyInPrefs(vaultPrefs, "gemini", originalKey)

        // Raw preference value must be encrypted Base64 and not match the plain text
        val rawStored = vaultPrefs.getString("enc_key_gemini", null)
        assertTrue(rawStored != null && rawStored.isNotBlank())
        assertFalse(rawStored!!.contains("sk-ant-test-api-key-12345"))

        // Decrypted key must match original plain key
        val decrypted = PetalAiKeyStorage.getKeyFromPrefs(vaultPrefs, "gemini")
        assertEquals(originalKey, decrypted)
    }

    @Test
    fun testSetEmptyKeyRemovesEntry() {
        val vaultPrefs = FakePreferences()
        PetalAiKeyStorage.setKeyInPrefs(vaultPrefs, "groq", "test-key-to-delete")
        assertTrue(vaultPrefs.contains("enc_key_groq"))

        // Setting blank or empty key should remove the encrypted entry
        PetalAiKeyStorage.setKeyInPrefs(vaultPrefs, "groq", "   ")
        assertFalse(vaultPrefs.contains("enc_key_groq"))
        assertEquals("", PetalAiKeyStorage.getKeyFromPrefs(vaultPrefs, "groq"))
    }

    @Test
    fun testOneTimeMigrationDeletesPlainKeys() {
        val defaultPrefs = FakePreferences()
        val vaultPrefs = FakePreferences()

        // Setup legacy unencrypted keys in default SharedPreferences
        defaultPrefs.edit()
            .putString("sp_ai_key_gemini", "gemini-plain-key-1")
            .putString("sp_ai_key_groq", "groq-plain-key-2")
            .putString("sp_ai_key_custom", "custom-plain-key-3")
            .apply()

        assertFalse(vaultPrefs.getBoolean(PetalAiKeyStorage.KEY_MIGRATED, false))

        // Execute migration
        PetalAiKeyStorage.migratePlainKeys(defaultPrefs, vaultPrefs)

        // Verify plain keys were permanently deleted from default SharedPreferences
        assertFalse(defaultPrefs.contains("sp_ai_key_gemini"))
        assertFalse(defaultPrefs.contains("sp_ai_key_groq"))
        assertFalse(defaultPrefs.contains("sp_ai_key_custom"))

        // Verify migration flag is persisted
        assertTrue(vaultPrefs.getBoolean(PetalAiKeyStorage.KEY_MIGRATED, false))

        // Verify keys were securely moved to encrypted storage
        assertEquals("gemini-plain-key-1", PetalAiKeyStorage.getKeyFromPrefs(vaultPrefs, "gemini"))
        assertEquals("groq-plain-key-2", PetalAiKeyStorage.getKeyFromPrefs(vaultPrefs, "groq"))
        assertEquals("custom-plain-key-3", PetalAiKeyStorage.getKeyFromPrefs(vaultPrefs, "custom"))
    }

    @Test
    fun testMigrationRunsOnlyOnce() {
        val defaultPrefs = FakePreferences()
        val vaultPrefs = FakePreferences()

        defaultPrefs.edit().putString("sp_ai_key_gemini", "key-once").apply()
        PetalAiKeyStorage.migratePlainKeys(defaultPrefs, vaultPrefs)

        // Modify encrypted key in vault
        PetalAiKeyStorage.setKeyInPrefs(vaultPrefs, "gemini", "updated-vault-key")

        // Add another plain key to default prefs
        defaultPrefs.edit().putString("sp_ai_key_gemini", "new-plain-key").apply()

        // Running migration again must do nothing because KEY_MIGRATED is true
        PetalAiKeyStorage.migratePlainKeys(defaultPrefs, vaultPrefs)
        assertEquals("updated-vault-key", PetalAiKeyStorage.getKeyFromPrefs(vaultPrefs, "gemini"))
    }
}
