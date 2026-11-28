package com.novaredux.launcher.ui

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi

// The phone's own lock, asked for before something private shows (#4): a
// fingerprint or face where the phone has them, else its PIN, pattern or
// password. NovaRedux keeps no secret of its own. Made with the activity,
// since Android 9's credential screen answers through an activity result.
class DeviceAuth(private val activity: ComponentActivity) {

    private var pending: (() -> Unit)? = null

    private val credentialScreen = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val then = pending
        pending = null
        if (result.resultCode == Activity.RESULT_OK) then?.invoke()
    }

    // False while the phone has no screen lock to ask for.
    val available: Boolean
        get() = activity.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    // Runs onConfirmed once the lock is given; a cancel runs nothing. A phone
    // with no screen lock has nothing to ask for, so it goes straight on.
    fun confirm(title: CharSequence, onConfirmed: () -> Unit) {
        when {
            !available -> onConfirmed()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> prompt(title, onConfirmed)
            else -> askForCredential(title, onConfirmed)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun prompt(title: CharSequence, onConfirmed: () -> Unit) {
        val builder = BiometricPrompt.Builder(activity).setTitle(title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        builder.build().authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onConfirmed()
            }
        )
    }

    // Android 9: the system's own credential screen, which takes a
    // fingerprint too on phones that have one.
    private fun askForCredential(title: CharSequence, onConfirmed: () -> Unit) {
        @Suppress("DEPRECATION")
        val intent = activity.getSystemService(KeyguardManager::class.java)
            ?.createConfirmDeviceCredentialIntent(title, null)
        if (intent == null) {
            onConfirmed()
            return
        }
        pending = onConfirmed
        credentialScreen.launch(intent)
    }
}
