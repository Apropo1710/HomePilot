package de.homepilot.app

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Shared HomePilot account registry. Passwords are never stored in clear text:
 * only PBKDF2-HMAC-SHA256 hashes with independent random salts are persisted.
 */
internal data class HomePilotAccount(
    val username: String,
    val displayName: String,
    val role: String,
    val salt: String,
    val passwordHash: String,
    val iterations: Int = PASSWORD_ITERATIONS,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson() = JSONObject().apply {
        put("username", username)
        put("displayName", displayName)
        put("role", role)
        put("salt", salt)
        put("passwordHash", passwordHash)
        put("iterations", iterations)
        put("createdAt", createdAt)
    }

    companion object {
        const val ADMIN = "admin"
        const val USER = "user"
        const val PASSWORD_ITERATIONS = 120_000

        fun create(username: String, displayName: String, password: CharArray, role: String): HomePilotAccount {
            require(username.matches(Regex("[A-Za-z0-9_.-]{3,32}"))) { "Benutzername: 3–32 Zeichen (Buchstaben, Zahlen, . _ -)." }
            require(password.size >= 8) { "Das Passwort muss mindestens 8 Zeichen lang sein." }
            require(role == ADMIN || role == USER)
            val saltBytes = ByteArray(16).also(SecureRandom()::nextBytes)
            val salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
            val hash = derive(password, saltBytes, PASSWORD_ITERATIONS)
            return HomePilotAccount(username, displayName.trim().ifBlank { username }, role, salt, hash)
        }

        fun fromJson(json: JSONObject): HomePilotAccount? {
            return try {
                val username = json.getString("username")
                val role = json.getString("role")
                val iterations = json.getInt("iterations")
                if (!username.matches(Regex("[A-Za-z0-9_.-]{3,32}")) ||
                    role !in setOf(ADMIN, USER) || iterations !in 50_000..1_000_000) return null
                HomePilotAccount(username, json.optString("displayName", username), role,
                    json.getString("salt"), json.getString("passwordHash"), iterations,
                    json.optLong("createdAt", 0L))
            } catch (_: Exception) { null }
        }

        fun verify(account: HomePilotAccount, password: CharArray): Boolean {
            val salt = Base64.decode(account.salt, Base64.NO_WRAP)
            val candidate = derive(password, salt, account.iterations)
            return java.security.MessageDigest.isEqual(
                Base64.decode(candidate, Base64.NO_WRAP),
                Base64.decode(account.passwordHash, Base64.NO_WRAP)
            )
        }

        private fun derive(password: CharArray, salt: ByteArray, iterations: Int): String {
            val spec = PBEKeySpec(password, salt, iterations, 256)
            return try {
                val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).encoded
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } finally { spec.clearPassword() }
        }
    }
}

/** Versioned account registry stored in the shared ioBroker HomePilot namespace. */
internal data class HomePilotAccountRegistry(
    val revision: Long = 0,
    val accounts: List<HomePilotAccount> = emptyList()
) {
    fun toJson() = JSONObject().apply {
        put("schemaVersion", 1)
        put("revision", revision)
        put("accounts", JSONArray().also { array -> accounts.forEach { array.put(it.toJson()) } })
    }

    fun find(username: String): HomePilotAccount? =
        accounts.firstOrNull { it.username.equals(username.trim(), ignoreCase = true) }

    fun authenticate(username: String, password: CharArray): HomePilotAccount? =
        find(username)?.takeIf { HomePilotAccount.verify(it, password) }

    fun createInitialAdmin(username: String, displayName: String, password: CharArray): HomePilotAccountRegistry {
        require(accounts.isEmpty()) { "Der Administrator wurde bereits eingerichtet." }
        val admin = HomePilotAccount.create(username, displayName, password, HomePilotAccount.ADMIN)
        return copy(revision = revision + 1, accounts = listOf(admin))
    }

    fun addAccount(actor: HomePilotAccount, username: String, displayName: String, password: CharArray, role: String): HomePilotAccountRegistry {
        require(actor.role == HomePilotAccount.ADMIN) { "Nur Administratoren dürfen Benutzer verwalten." }
        require(find(username) == null) { "Dieser Benutzername ist bereits vergeben." }
        val account = HomePilotAccount.create(username, displayName, password, role)
        return copy(revision = revision + 1, accounts = accounts + account)
    }

    fun resetPassword(actor: HomePilotAccount, username: String, password: CharArray): HomePilotAccountRegistry {
        require(actor.role == HomePilotAccount.ADMIN || actor.username.equals(username, ignoreCase = true)) {
            "Keine Berechtigung, dieses Passwort zu ändern."
        }
        val target = find(username) ?: throw IllegalArgumentException("Benutzer nicht gefunden.")
        val replacement = HomePilotAccount.create(target.username, target.displayName, password, target.role)
        return copy(revision = revision + 1, accounts = accounts.map {
            if (it.username.equals(target.username, ignoreCase = true)) replacement else it
        })
    }

    fun deleteAccount(actor: HomePilotAccount, username: String): HomePilotAccountRegistry {
        require(actor.role == HomePilotAccount.ADMIN) { "Nur Administratoren dürfen Benutzer löschen." }
        val target = find(username) ?: throw IllegalArgumentException("Benutzer nicht gefunden.")
        require(target.role != HomePilotAccount.ADMIN || accounts.count { it.role == HomePilotAccount.ADMIN } > 1) {
            "Der letzte Administrator kann nicht gelöscht werden."
        }
        require(!actor.username.equals(target.username, ignoreCase = true)) {
            "Der angemeldete Administrator kann sich nicht selbst löschen."
        }
        return copy(revision = revision + 1, accounts = accounts.filterNot {
            it.username.equals(target.username, ignoreCase = true)
        })
    }

    companion object {
        fun fromJson(json: JSONObject): HomePilotAccountRegistry? {
            if (json.optInt("schemaVersion", -1) != 1) return null
            val arr = json.optJSONArray("accounts") ?: return null
            val accounts = (0 until arr.length()).map { HomePilotAccount.fromJson(arr.optJSONObject(it) ?: return null) ?: return null }
            if (accounts.map { it.username.lowercase() }.distinct().size != accounts.size) return null
            return HomePilotAccountRegistry(json.optLong("revision", 0L).coerceAtLeast(0L), accounts)
        }
    }
}
