package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.model.BrowserTab
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages encrypted local storage for private browsing sessions and private PIN lock state.
 * Uses hardware-backed Android KeyStore with AES-256-GCM.
 * Ensures private tabs and session resume states are never stored in plaintext.
 */
class SecurePrivateSessionManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("naxxivo_private_session_prefs", Context.MODE_PRIVATE)

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator =
                    KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            // Logically fall back gracefully if keystore unavailable
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            null
        }
    }

    private fun encrypt(plaintext: String): String? {
        return try {
            val secretKey = getSecretKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            
            // Format: Base64(IV) + ":" + Base64(Ciphertext)
            val encodedIv = Base64.encodeToString(iv, Base64.NO_WRAP)
            val encodedPayload = Base64.encodeToString(cipherText, Base64.NO_WRAP)
            "$encodedIv:$encodedPayload"
        } catch (e: Exception) {
            null
        }
    }

    private fun decrypt(encryptedData: String): String? {
        return try {
            val parts = encryptedData.split(":")
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)

            val secretKey = getSecretKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(cipherText)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    // --- Private Session Resume ---

    fun isPrivateResumeEnabled(): Boolean {
        return prefs.getBoolean(PREF_PRIVATE_RESUME_ENABLED, false)
    }

    fun setPrivateResumeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_PRIVATE_RESUME_ENABLED, enabled).apply()
        if (!enabled) {
            clearEncryptedPrivateSession()
        }
    }

    fun hasSavedPrivateSession(): Boolean {
        return prefs.contains(PREF_ENCRYPTED_SESSION_DATA)
    }

    fun saveEncryptedPrivateSession(tabs: List<BrowserTab>) {
        if (!isPrivateResumeEnabled()) return
        val validTabs = tabs.filter { it.isIncognito && it.url.isNotBlank() && it.url != "about:blank" }
        if (validTabs.isEmpty()) {
            clearEncryptedPrivateSession()
            return
        }

        try {
            val array = JSONArray()
            for (tab in validTabs) {
                val obj = JSONObject().apply {
                    put("id", tab.id)
                    put("url", tab.url)
                    put("title", tab.title)
                    put("isDesktopMode", tab.isDesktopMode)
                    tab.videoPlaybackSeconds?.let { put("videoPlaybackSeconds", it.toDouble()) }
                    put("timestamp", System.currentTimeMillis())
                }
                array.put(obj)
            }
            val jsonString = array.toString()
            val encrypted = encrypt(jsonString)
            if (encrypted != null) {
                prefs.edit().putString(PREF_ENCRYPTED_SESSION_DATA, encrypted).apply()
            }
        } catch (e: Exception) {
            // Ignore failure to prevent crashes
        }
    }

    fun loadEncryptedPrivateSession(): List<BrowserTab> {
        if (!isPrivateResumeEnabled()) return emptyList()
        val encrypted = prefs.getString(PREF_ENCRYPTED_SESSION_DATA, null) ?: return emptyList()
        val decrypted = decrypt(encrypted) ?: return emptyList()

        return try {
            val array = JSONArray(decrypted)
            val list = mutableListOf<BrowserTab>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val videoTime = if (obj.has("videoPlaybackSeconds")) obj.getDouble("videoPlaybackSeconds").toFloat() else null
                list.add(
                    BrowserTab(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        url = obj.optString("url", ""),
                        title = obj.optString("title", "Private Tab"),
                        displayUrl = obj.optString("url", ""),
                        isDesktopMode = obj.optBoolean("isDesktopMode", false),
                        isIncognito = true,
                        videoPlaybackSeconds = videoTime
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearEncryptedPrivateSession() {
        prefs.edit().remove(PREF_ENCRYPTED_SESSION_DATA).apply()
    }

    // --- Private Session Lock (PIN) ---

    fun isPinLockConfigured(): Boolean {
        return prefs.contains(PREF_PIN_HASH) && prefs.contains(PREF_PIN_SALT)
    }

    fun setPinLock(pin: String): Boolean {
        if (pin.length < 4) return false
        try {
            val random = SecureRandom()
            val saltBytes = ByteArray(16)
            random.nextBytes(saltBytes)
            val saltEncoded = Base64.encodeToString(saltBytes, Base64.NO_WRAP)

            val hash = hashPin(pin, saltBytes)
            prefs.edit()
                .putString(PREF_PIN_HASH, hash)
                .putString(PREF_PIN_SALT, saltEncoded)
                .apply()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun verifyPin(pin: String): Boolean {
        if (!isPinLockConfigured()) return true
        val savedHash = prefs.getString(PREF_PIN_HASH, null) ?: return true
        val saltEncoded = prefs.getString(PREF_PIN_SALT, null) ?: return true
        val saltBytes = Base64.decode(saltEncoded, Base64.NO_WRAP)

        val inputHash = hashPin(pin, saltBytes)
        return inputHash == savedHash
    }

    fun removePinLock() {
        prefs.edit()
            .remove(PREF_PIN_HASH)
            .remove(PREF_PIN_SALT)
            .apply()
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val hashBytes = digest.digest(pin.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }

    // --- Smart Private Site Protection Domains ---

    fun getSmartPrivateDomains(): Set<String> {
        val encrypted = prefs.getString(PREF_ENCRYPTED_PRIVATE_DOMAINS, null)
        if (encrypted == null) {
            // First time initialization: populate curated default sensitive domains
            saveSmartPrivateDomains(DEFAULT_PRIVATE_DOMAINS)
            return DEFAULT_PRIVATE_DOMAINS
        }
        val decrypted = decrypt(encrypted) ?: return DEFAULT_PRIVATE_DOMAINS
        return try {
            val array = JSONArray(decrypted)
            val result = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                val clean = UrlUtils.extractCanonicalHost(array.getString(i))
                if (clean.isNotBlank()) {
                    result.add(clean)
                }
            }
            if (result.isEmpty()) DEFAULT_PRIVATE_DOMAINS else result
        } catch (e: Exception) {
            DEFAULT_PRIVATE_DOMAINS
        }
    }

    fun saveSmartPrivateDomains(domains: Set<String>) {
        try {
            val array = JSONArray()
            for (d in domains) {
                val clean = UrlUtils.extractCanonicalHost(d)
                if (clean.isNotBlank()) {
                    array.put(clean)
                }
            }
            val encrypted = encrypt(array.toString())
            if (encrypted != null) {
                prefs.edit().putString(PREF_ENCRYPTED_PRIVATE_DOMAINS, encrypted).apply()
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    fun addSmartPrivateDomain(domain: String): Set<String> {
        val clean = UrlUtils.extractCanonicalHost(domain)
        if (clean.isBlank()) return getSmartPrivateDomains()
        val current = getSmartPrivateDomains().toMutableSet()
        current.add(clean)
        saveSmartPrivateDomains(current)
        return current
    }

    fun removeSmartPrivateDomain(domain: String): Set<String> {
        val clean = UrlUtils.extractCanonicalHost(domain)
        val current = getSmartPrivateDomains().toMutableSet()
        current.remove(clean)
        current.remove(clean.removePrefix("www."))
        saveSmartPrivateDomains(current)
        return current
    }

    fun resetSmartPrivateDomainsToDefault(): Set<String> {
        saveSmartPrivateDomains(DEFAULT_PRIVATE_DOMAINS)
        return DEFAULT_PRIVATE_DOMAINS
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "NaxxivoPrivateSessionKey_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128

        private const val PREF_PRIVATE_RESUME_ENABLED = "pref_private_resume_enabled"
        private const val PREF_ENCRYPTED_SESSION_DATA = "pref_encrypted_session_data"
        private const val PREF_PIN_HASH = "pref_pin_hash"
        private const val PREF_PIN_SALT = "pref_pin_salt"
        private const val PREF_ENCRYPTED_PRIVATE_DOMAINS = "pref_encrypted_private_domains"

        val DEFAULT_PRIVATE_DOMAINS: Set<String> = setOf(
            "pornhub.com",
            "xvideos.com",
            "xnxx.com",
            "xhamster.com",
            "onlyfans.com",
            "redtube.com",
            "youporn.com",
            "chaturbate.com",
            "spankbang.com",
            "stripchat.com"
        )
    }
}
