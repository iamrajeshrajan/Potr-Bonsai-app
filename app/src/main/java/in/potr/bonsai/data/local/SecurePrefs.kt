package `in`.potr.bonsai.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores studio sync metadata and encrypts sensitive WooCommerce / WordPress credentials
 * using an Android Keystore AES-256-GCM key.
 */
class SecurePrefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("potr_bonsai_secure_prefs", Context.MODE_PRIVATE)

    var googleEmail: String?
        get() = prefs.getString(KEY_GOOGLE_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_GOOGLE_EMAIL, value).apply()

    var driveFolderId: String?
        get() = prefs.getString(KEY_DRIVE_FOLDER_ID, null)
        set(value) = prefs.edit().putString(KEY_DRIVE_FOLDER_ID, value).apply()

    var drivePhotosFolderId: String?
        get() = prefs.getString(KEY_DRIVE_PHOTOS_FOLDER_ID, null)
        set(value) = prefs.edit().putString(KEY_DRIVE_PHOTOS_FOLDER_ID, value).apply()

    var driveBackupFileId: String?
        get() = prefs.getString(KEY_DRIVE_BACKUP_FILE_ID, null)
        set(value) = prefs.edit().putString(KEY_DRIVE_BACKUP_FILE_ID, value).apply()

    var masterSheetId: String?
        get() = prefs.getString(KEY_MASTER_SHEET_ID, null)
        set(value) = prefs.edit().putString(KEY_MASTER_SHEET_ID, value).apply()

    var lastBackupEpochMs: Long
        get() = prefs.getLong(KEY_LAST_BACKUP_EPOCH_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP_EPOCH_MS, value).apply()

    var lastBackupError: String?
        get() = prefs.getString(KEY_LAST_BACKUP_ERROR, null)
        set(value) = prefs.edit().putString(KEY_LAST_BACKUP_ERROR, value).apply()

    var youtubePrivacyStatus: String
        get() = prefs.getString(KEY_YT_PRIVACY, "unlisted") ?: "unlisted"
        set(value) = prefs.edit().putString(KEY_YT_PRIVACY, value).apply()

    var wooStoreUrl: String
        get() = prefs.getString(KEY_WC_STORE_URL, "https://potr.in") ?: "https://potr.in"
        set(value) = prefs.edit().putString(KEY_WC_STORE_URL, value.ifBlank { "https://potr.in" }).apply()

    var wooWpUser: String
        get() = prefs.getString(KEY_WC_WP_USER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WC_WP_USER, value).apply()

    var wooConsumerKey: String
        get() = getEncrypted(KEY_WC_CK)
        set(value) = putEncrypted(KEY_WC_CK, value)

    var wooConsumerSecret: String
        get() = getEncrypted(KEY_WC_CS)
        set(value) = putEncrypted(KEY_WC_CS, value)

    var wooWpAppPassword: String
        get() = getEncrypted(KEY_WC_WP_PASS)
        set(value) = putEncrypted(KEY_WC_WP_PASS, value)

    val hasWooKeys: Boolean
        get() = wooConsumerKey.isNotBlank() && wooConsumerSecret.isNotBlank()

    val hasWpAppPassword: Boolean
        get() = wooWpUser.isNotBlank() && wooWpAppPassword.isNotBlank()

    fun clearGoogleSession() {
        prefs.edit()
            .remove(KEY_GOOGLE_EMAIL)
            .remove(KEY_DRIVE_FOLDER_ID)
            .remove(KEY_DRIVE_PHOTOS_FOLDER_ID)
            .remove(KEY_DRIVE_BACKUP_FILE_ID)
            .remove(KEY_LAST_BACKUP_ERROR)
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun putEncrypted(key: String, plainText: String) {
        if (plainText.isBlank()) {
            prefs.edit().remove(key).apply()
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + cipherBytes.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)
            val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
            prefs.edit().putString(key, encoded).apply()
        } catch (_: Exception) {
            // Do not silently write plaintext secrets if Keystore is unavailable
        }
    }

    private fun getEncrypted(key: String): String {
        val encoded = prefs.getString(key, null) ?: return ""
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val ivLen = combined[0].toInt() and 0xFF
            val iv = combined.copyOfRange(1, 1 + ivLen)
            val cipherBytes = combined.copyOfRange(1 + ivLen, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "potr_bonsai_creds_aes_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        private const val KEY_GOOGLE_EMAIL = "google_email"
        private const val KEY_DRIVE_FOLDER_ID = "drive_folder_id"
        private const val KEY_DRIVE_PHOTOS_FOLDER_ID = "drive_photos_folder_id"
        private const val KEY_DRIVE_BACKUP_FILE_ID = "drive_backup_file_id"
        private const val KEY_MASTER_SHEET_ID = "master_sheet_id"
        private const val KEY_LAST_BACKUP_EPOCH_MS = "last_backup_epoch_ms"
        private const val KEY_LAST_BACKUP_ERROR = "last_backup_error"
        private const val KEY_YT_PRIVACY = "yt_privacy"
        private const val KEY_WC_STORE_URL = "wc_store_url"
        private const val KEY_WC_WP_USER = "wc_wp_user"
        private const val KEY_WC_CK = "wc_ck_enc"
        private const val KEY_WC_CS = "wc_cs_enc"
        private const val KEY_WC_WP_PASS = "wc_wp_pass_enc"
    }
}
