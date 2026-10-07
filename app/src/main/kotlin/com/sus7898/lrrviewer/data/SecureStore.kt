package com.sus7898.lrrviewer.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts small secrets (the LANraragi API key) with an AES-256-GCM key that lives in the
 * Android Keystore. The key material never leaves the secure hardware / TEE, so a copy of the
 * app's data directory does not reveal the API key.
 */
class SecureStore {

    private val keyAlias = "lrrviewer_secrets_v1"
    private val transformation = "AES/GCM/NoPadding"
    private val ivLength = 12

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    /** Returns base64(iv || ciphertext || tag). */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + ct)
    }

    /** Returns the plaintext, or an empty string if the blob cannot be decrypted (e.g. key was wiped). */
    fun decrypt(blob: String): String = runCatching {
        val all = Base64.getDecoder().decode(blob)
        require(all.size > ivLength)
        val iv = all.copyOfRange(0, ivLength)
        val ct = all.copyOfRange(ivLength, all.size)
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ct), Charsets.UTF_8)
    }.getOrDefault("")
}
