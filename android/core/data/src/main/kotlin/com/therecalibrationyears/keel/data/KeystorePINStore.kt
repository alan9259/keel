package com.therecalibrationyears.keel.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first

/**
 * The PIN's salt and hash, encrypted with an AES-GCM key held in the Android Keystore (the
 * counterpart of the iOS Keychain store; plan §1.3). The key never leaves the Keystore and goes
 * with the app on uninstall, so a copied preferences file reveals nothing. A blob that can't be
 * opened (the key was lost) reads as no PIN, so the lock can never trap her.
 */
class KeystorePINStore(private val prefs: DataStore<Preferences>) : PINSecureStore {
    override suspend fun hasPIN(): Boolean = load() != null

    override suspend fun save(salt: ByteArray, hash: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val blob = cipher.iv + cipher.doFinal(byteArrayOf(salt.size.toByte()) + salt + hash)
        prefs.edit { it[BLOB] = Base64.encodeToString(blob, Base64.NO_WRAP) }
    }

    override suspend fun load(): Pair<ByteArray, ByteArray>? {
        val stored = prefs.data.first()[BLOB] ?: return null
        return runCatching {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: return null, GCMParameterSpec(128, blob, 0, IV_BYTES))
            val plain = cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
            val saltSize = plain[0].toInt()
            plain.copyOfRange(1, 1 + saltSize) to plain.copyOfRange(1 + saltSize, plain.size)
        }.getOrNull()
    }

    override suspend fun clear() {
        prefs.edit { it.remove(BLOB) }
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private fun keyStore() = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun existingKey(): SecretKey? = (keyStore().getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun key(): SecretKey = existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
        init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore" // not-copy: Keystore provider
        const val ALIAS = "keel.pin.key" // not-copy: Keystore alias
        const val TRANSFORMATION = "AES/GCM/NoPadding" // not-copy: cipher
        const val IV_BYTES = 12
        val BLOB = stringPreferencesKey("keel.pin")
    }
}
