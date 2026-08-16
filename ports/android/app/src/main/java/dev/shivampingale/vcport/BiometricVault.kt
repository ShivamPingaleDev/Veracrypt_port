package dev.shivampingale.vcport

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

class BiometricVault(private val context: Context) {
    private val prefs = context.getSharedPreferences("vc_port_bio", Context.MODE_PRIVATE)
    private val keyAlias = "vc_port_volume_key"

    fun isAvailable(): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun hasPassword(volumePath: String): Boolean =
        prefs.contains(keyFor(volumePath))

    fun store(activity: FragmentActivity, volumePath: String, password: String, pim: Int, onDone: (Boolean) -> Unit) {
        ensureKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        prompt(activity, cipher, "Save volume password") { unlocked ->
            if (unlocked == null) {
                onDone(false)
                return@prompt
            }
            val payload = "$pim\n$password".toByteArray()
            val encrypted = unlocked.doFinal(payload)
            prefs.edit()
                .putString(keyFor(volumePath), Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(ivFor(volumePath), Base64.encodeToString(unlocked.iv, Base64.NO_WRAP))
                .apply()
            onDone(true)
        }
    }

    fun load(activity: FragmentActivity, volumePath: String, onDone: (Pair<String, Int>?) -> Unit) {
        val blob = prefs.getString(keyFor(volumePath), null) ?: return onDone(null)
        val iv = prefs.getString(ivFor(volumePath), null) ?: return onDone(null)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        prompt(activity, cipher, "Unlock volume") { unlocked ->
            if (unlocked == null) {
                onDone(null)
                return@prompt
            }
            val plain = String(unlocked.doFinal(Base64.decode(blob, Base64.NO_WRAP)))
            val parts = plain.split("\n", limit = 2)
            onDone(parts[1] to parts[0].toInt())
        }
    }

    private fun prompt(activity: FragmentActivity, cipher: Cipher, title: String, done: (Cipher?) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    done(result.cryptoObject?.cipher)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    done(null)
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("VC Port")
                .setNegativeButtonText("Cancel")
                .build(),
            BiometricPrompt.CryptoObject(cipher)
        )
    }

    private fun ensureKey() {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(keyAlias)) return
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        generator.generateKey()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(keyAlias, null) as SecretKey
    }

    private fun keyFor(path: String) = "pw:$path"
    private fun ivFor(path: String) = "iv:$path"
}
