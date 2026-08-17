package dev.shivampingale.vcport

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class BiometricVault(private val context: Context) {
    private val prefs = context.getSharedPreferences("vc_port_bio", Context.MODE_PRIVATE)

    fun isAvailable(): Boolean {
        val manager = BiometricManager.from(context)
        if (manager.canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS) {
            return true
        }
        return manager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun hasFactors(volumePath: String): Boolean =
        prefs.contains(keyFor(volumePath))

    fun clear(volumePath: String) {
        prefs.edit()
            .remove(keyFor(volumePath))
            .remove(ivFor(volumePath))
            .apply()
    }

    fun store(activity: FragmentActivity, volumePath: String, bundle: FactorBundle, onDone: (Boolean) -> Unit) {
        ensureKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        prompt(activity, cipher, "Save unlock factors") { unlocked ->
            if (unlocked == null) {
                onDone(false)
                return@prompt
            }
            val encrypted = unlocked.doFinal(FactorCodec.encode(bundle))
            prefs.edit()
                .putString(keyFor(volumePath), Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(ivFor(volumePath), Base64.encodeToString(unlocked.iv, Base64.NO_WRAP))
                .apply()
            onDone(true)
        }
    }

    /**
     * System PIN / pattern / password, fingerprint, or face. No Keystore crypto.
     * Used on Create volume and Open volume when phone unlock is selected.
     */
    fun confirm(activity: FragmentActivity, title: String, done: (Boolean) -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    done(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    done(false)
                }

                override fun onAuthenticationFailed() {
                    // Keep the sheet up until success or cancel.
                }
            }
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Fingerprint, face, or screen lock PIN / password")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(authenticators())
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        prompt.authenticate(builder.build())
    }

    fun load(activity: FragmentActivity, volumePath: String, onDone: (FactorBundle?) -> Unit) {
        val blob = prefs.getString(keyFor(volumePath), null) ?: return onDone(null)
        val iv = prefs.getString(ivFor(volumePath), null) ?: return onDone(null)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            prompt(activity, cipher, "Unlock with fingerprint, face, or screen lock") { unlocked ->
                if (unlocked == null) {
                    onDone(null)
                    return@prompt
                }
                try {
                    val plain = unlocked.doFinal(Base64.decode(blob, Base64.NO_WRAP))
                    onDone(FactorCodec.decode(plain))
                } catch (_: Exception) {
                    onDone(null)
                }
            }
        } catch (_: Exception) {
            onDone(null)
        }
    }

    private fun authenticators(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
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
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("VC Port")
            .setAllowedAuthenticators(authenticators())
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            builder.setNegativeButtonText("Cancel")
        }
        prompt.authenticate(builder.build(), BiometricPrompt.CryptoObject(cipher))
    }

    private fun ensureKey() {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(KEY_ALIAS)) return
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generator.init(keySpec(strongBox = true))
                generator.generateKey()
                return
            } catch (_: Exception) {
                // Device has no StrongBox; fall back to TEE.
            }
        }
        generator.init(keySpec(strongBox = false))
        generator.generateKey()
    }

    private fun keySpec(strongBox: Boolean): KeyGenParameterSpec {
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            spec.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
            spec.setInvalidatedByBiometricEnrollment(false)
        } else {
            spec.setInvalidatedByBiometricEnrollment(true)
        }
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            spec.setIsStrongBoxBacked(true)
        }
        return spec.build()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(KEY_ALIAS, null) as SecretKey
    }

    private fun keyFor(path: String) = "pw:$path"
    private fun ivFor(path: String) = "iv:$path"

    companion object {
        const val KEY_ALIAS = "vc_port_volume_key_v2"
        const val LEGACY_KEY_ALIAS = "vc_port_volume_key"
    }
}
