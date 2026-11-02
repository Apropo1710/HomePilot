package de.homepilot.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores ioBroker login credentials using Android Keystore-backed encryption.
 * Credentials are intentionally kept separate from the app's ordinary preferences.
 */
internal class IoBrokerCredentialsStore(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        "homepilot_iobroker_credentials",
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun username(): String = preferences.getString(KEY_USERNAME, "").orEmpty()

    fun password(): String = preferences.getString(KEY_PASSWORD, "").orEmpty()

    fun hasCredentials(): Boolean =
        username().isNotBlank() && password().isNotBlank()

    fun save(username: String, password: String) {
        preferences.edit()
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun clear() {
        preferences.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .apply()
    }

    private companion object {
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
    }
}
