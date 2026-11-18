package de.homepilot.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.zxing.common.BitMatrix
import de.homepilot.app.sync.HomePilotPeerSessionHandler
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.Socket
import java.security.MessageDigest

/** ioBroker state IDs of the shared HomePilot documents. */
internal object HomePilotIoBrokerIds {
    const val ACCOUNTS = "0_userdata.0.HomePilot.accounts"
    const val CONFIG = "0_userdata.0.HomePilot.config"
    fun profile(username: String) = "0_userdata.0.HomePilot.profiles.${homePilotProfileIdSegment(username)}"
}

/**
 * Einheitliche Ablage der ioBroker-Adresse (Schlüssel `server` in den HomePilot-Einstellungen).
 * Eingaben wie `192.168.178.54`, `host:8084` oder `ws://host:8084` werden zu einer ws(s)-URL.
 */
internal object IoBrokerAddress {
    const val DEFAULT_PORT = 8084
    const val DEFAULT_URL = "ws://192.168.178.54:8084"
    private const val KEY = "server"

    fun normalize(input: String?): String? {
        var value = input?.trim()?.trimEnd('/') ?: return null
        if (value.isEmpty()) return null
        val secure = value.startsWith("wss://", ignoreCase = true)
        value = when {
            value.startsWith("ws://", ignoreCase = true) -> value.substring(5)
            secure -> value.substring(6)
            value.contains("://") -> return null
            else -> value
        }
        val authority = value.substringBefore('/').substringBefore('?')
        if (authority.isEmpty() || authority.any { it.isWhitespace() }) return null
        val host: String
        val port: Int
        if (authority.startsWith("[")) {
            val end = authority.indexOf(']')
            if (end <= 1) return null
            host = authority.substring(0, end + 1)
            val rest = authority.substring(end + 1)
            port = if (rest.isEmpty()) DEFAULT_PORT else rest.removePrefix(":").toIntOrNull() ?: return null
        } else if (authority.count { it == ':' } == 1) {
            host = authority.substringBefore(':')
            port = authority.substringAfter(':').toIntOrNull() ?: return null
        } else if (authority.contains(':')) {
            return null
        } else {
            host = authority
            port = DEFAULT_PORT
        }
        if (host.isEmpty() || port !in 1..65535) return null
        return (if (secure) "wss://" else "ws://") + host + ":" + port
    }

    fun saved(prefs: SharedPreferences): String? = normalize(prefs.getString(KEY, null))

    fun store(prefs: SharedPreferences, url: String) {
        val normalized = normalize(url) ?: return
        prefs.edit().putString(KEY, normalized).apply()
    }
}

/** Gemeinsame Regel für alle synchronisierten Dokumente (ioBroker und gekoppelte Smartphones). */
internal object HomePilotDocMerge {
    /**
     * True, wenn [candidate] den aktuellen Stand ersetzen soll: höhere Revision gewinnt; bei gleicher
     * Revision und unterschiedlichem Inhalt die neuere Änderungszeit, danach ein Inhalts-Digest.
     * Damit landen alle Geräte deterministisch beim selben Stand.
     */
    fun isNewer(candidateRevision: Long, candidateUpdatedAt: Long, candidate: JSONObject,
                currentRevision: Long, currentUpdatedAt: Long, current: JSONObject): Boolean {
        if (candidateRevision != currentRevision) return candidateRevision > currentRevision
        val a = candidate.toString()
        val b = current.toString()
        if (a == b) return false
        if (candidateUpdatedAt != currentUpdatedAt) return candidateUpdatedAt > currentUpdatedAt
        return digest(a) > digest(b)
    }

    fun isNewer(candidate: HomePilotAccountRegistry, current: HomePilotAccountRegistry) =
        isNewer(candidate.revision, 0L, candidate.toJson(), current.revision, 0L, current.toJson())

    fun isNewer(candidate: HomePilotSharedConfig, current: HomePilotSharedConfig) =
        isNewer(candidate.revision, candidate.updatedAt, candidate.toJson(), current.revision, current.updatedAt, current.toJson())

    fun isNewer(candidate: HomePilotUserProfile, current: HomePilotUserProfile) =
        isNewer(candidate.revision, candidate.updatedAt, candidate.toJson(), current.revision, current.updatedAt, current.toJson())

    fun digest(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

internal sealed interface HomePilotAccountLoadResult {
    data class Loaded(val registry: HomePilotAccountRegistry) : HomePilotAccountLoadResult
    data object Missing : HomePilotAccountLoadResult
    data class Invalid(val backupSaved: Boolean) : HomePilotAccountLoadResult
}

/**
 * Lokale Kontenliste. Sie ist auf jedem Smartphone vorhanden, damit die Anmeldung auch ohne ioBroker
 * funktioniert, und wird mit ioBroker und gekoppelten Smartphones abgeglichen.
 */
internal class HomePilotAccountStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("homepilot_accounts", Context.MODE_PRIVATE)

    fun load(): HomePilotAccountLoadResult = synchronized(LOCK) {
        if (!prefs.contains(KEY)) return@synchronized HomePilotAccountLoadResult.Missing
        val stored = prefs.all[KEY]
        if (stored !is String) return@synchronized HomePilotAccountLoadResult.Invalid(backupCorruptValue(stored))
        val registry = runCatching { HomePilotAccountRegistry.fromJson(JSONObject(stored)) }.getOrNull()
        if (registry != null) HomePilotAccountLoadResult.Loaded(registry)
        else HomePilotAccountLoadResult.Invalid(backupCorruptValue(stored))
    }

    fun loadedRegistry(): HomePilotAccountRegistry? = (load() as? HomePilotAccountLoadResult.Loaded)?.registry

    private fun backupCorruptValue(value: Any?): Boolean {
        val backupValue = value as? String ?: "<ungültiger Wert: ${value?.javaClass?.name ?: "null"}> ${value ?: ""}"
        val backupKey = CORRUPT_BACKUP_PREFIX + HomePilotDocMerge.digest(backupValue)
        if (prefs.contains(backupKey)) return true
        return prefs.edit().putString(backupKey, backupValue).commit()
    }

    /** Schreibt nur, wenn lokal noch gar keine Kontenliste existiert. */
    fun saveInitial(registry: HomePilotAccountRegistry): Boolean = synchronized(LOCK) {
        if (prefs.contains(KEY)) return@synchronized false
        prefs.edit().putString(KEY, registry.toJson().toString()).commit()
    }

    /** Übernimmt eine Kontenliste nur, wenn lokal keine oder eine gültige, leere Liste vorhanden ist. */
    fun replaceEmptyWithImported(registry: HomePilotAccountRegistry): Boolean = synchronized(LOCK) {
        if (registry.accounts.isEmpty()) return@synchronized false
        when (val local = load()) {
            HomePilotAccountLoadResult.Missing -> Unit
            is HomePilotAccountLoadResult.Invalid -> return@synchronized false
            is HomePilotAccountLoadResult.Loaded -> if (local.registry.accounts.isNotEmpty()) return@synchronized false
        }
        prefs.edit().putString(KEY, registry.toJson().toString()).commit()
    }

    /**
     * Gleicht eine Kontenliste aus ioBroker oder von einem gekoppelten Smartphone ab.
     * Beschädigte lokale Daten werden nie überschrieben. True, wenn sich lokal etwas geändert hat.
     */
    fun mergeRemote(remote: HomePilotAccountRegistry): Boolean = synchronized(LOCK) {
        if (remote.accounts.isEmpty()) return@synchronized false
        val take = when (val local = load()) {
            HomePilotAccountLoadResult.Missing -> true
            is HomePilotAccountLoadResult.Invalid -> false
            is HomePilotAccountLoadResult.Loaded ->
                local.registry.accounts.isEmpty() || HomePilotDocMerge.isNewer(remote, local.registry)
        }
        if (!take) return@synchronized false
        prefs.edit().putString(KEY, remote.toJson().toString()).commit()
    }

    /** Lokale Änderung (Konto anlegen, Passwort, Löschen): nur auf Basis des aktuellen Stands. */
    fun save(expectedRevision: Long, registry: HomePilotAccountRegistry): String? = synchronized(LOCK) {
        when (val loaded = load()) {
            is HomePilotAccountLoadResult.Loaded -> when {
                loaded.registry.revision != expectedRevision ->
                    "Die Kontenliste wurde zwischenzeitlich geändert. Bitte erneut versuchen."
                registry.revision != expectedRevision + 1 -> "Ungültige Versionsnummer der Kontenliste."
                prefs.edit().putString(KEY, registry.toJson().toString()).commit() -> null
                else -> "Konten konnten nicht lokal gespeichert werden."
            }
            HomePilotAccountLoadResult.Missing -> "Die lokale Kontenliste ist nicht vorhanden."
            is HomePilotAccountLoadResult.Invalid -> "Die vorhandenen Kontendaten sind beschädigt. Sie wurden nicht überschrieben."
        }
    }

    private companion object {
        const val KEY = "homepilot_account_registry_v1"
        const val CORRUPT_BACKUP_PREFIX = "homepilot_account_registry_corrupt_backup_"
        val LOCK = Any()
    }
}

/**
 * Lokale Kopie der gemeinsamen Konfiguration (Räume, Geräte) und der Benutzerprofile. Sie ist die
 * Grundlage für den Abgleich mit ioBroker und mit gekoppelten Smartphones.
 */
internal class HomePilotDocStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("homepilot_sync_docs", Context.MODE_PRIVATE)

    fun config(): HomePilotSharedConfig? = synchronized(LOCK) {
        prefs.getString(KEY_CONFIG, null)?.let { raw ->
            runCatching { HomePilotSharedConfig.fromJson(JSONObject(raw)) }.getOrNull()
        }
    }

    /** Lokale Änderung: wird immer übernommen. */
    fun putLocalConfig(config: HomePilotSharedConfig): Boolean = synchronized(LOCK) {
        prefs.edit().putString(KEY_CONFIG, config.toJson().toString()).commit()
    }

    /** Fremder Stand: nur übernehmen, wenn er neuer ist. True, wenn sich lokal etwas geändert hat. */
    fun mergeConfig(remote: HomePilotSharedConfig): Boolean = synchronized(LOCK) {
        val current = config()
        if (current != null && !HomePilotDocMerge.isNewer(remote, current)) return@synchronized false
        prefs.edit().putString(KEY_CONFIG, remote.toJson().toString()).commit()
    }

    fun profile(username: String): HomePilotUserProfile? = synchronized(LOCK) {
        prefs.getString(profileKey(username), null)?.let { raw ->
            runCatching { HomePilotUserProfile.fromJson(JSONObject(raw)) }.getOrNull()
        }
    }

    fun profiles(): Map<String, HomePilotUserProfile> = synchronized(LOCK) {
        prefs.all.keys.filter { it.startsWith(PROFILE_PREFIX) }.mapNotNull { key ->
            val username = key.removePrefix(PROFILE_PREFIX)
            profile(username)?.let { username to it }
        }.toMap()
    }

    fun putLocalProfile(username: String, profile: HomePilotUserProfile): Boolean = synchronized(LOCK) {
        if (!isValidUsername(username)) return@synchronized false
        prefs.edit().putString(profileKey(username), profile.toJson().toString()).commit()
    }

    fun mergeProfile(username: String, remote: HomePilotUserProfile): Boolean = synchronized(LOCK) {
        if (!isValidUsername(username)) return@synchronized false
        val current = profile(username)
        if (current != null && !HomePilotDocMerge.isNewer(remote, current)) return@synchronized false
        prefs.edit().putString(profileKey(username), remote.toJson().toString()).commit()
    }

    private fun profileKey(username: String) = PROFILE_PREFIX + username.trim().lowercase()

    companion object {
        private const val KEY_CONFIG = "shared_config_v1"
        private const val PROFILE_PREFIX = "profile_v1:"
        private val LOCK = Any()
        fun isValidUsername(username: String) = username.trim().matches(Regex("[A-Za-z0-9_.-]{3,32}"))
    }
}

/**
 * Einrichtung eines neuen Smartphones per QR-Code (Kontenliste und ioBroker-Adresse). Der QR enthält
 * nur Passwort-Hashes (PBKDF2), keine Klartextpasswörter, und ist 10 Minuten gültig.
 */
internal object HomePilotSetupTransfer {
    const val PREFIX = "HOMEPILOT-SETUP:"
    const val VALIDITY_MS = 10 * 60 * 1000L
    private const val CLOCK_SKEW_MS = 5 * 60 * 1000L

    data class Payload(val registry: HomePilotAccountRegistry, val ioBrokerUrl: String?)

    fun create(registry: HomePilotAccountRegistry, ioBrokerUrl: String?, now: Long = System.currentTimeMillis()): String =
        PREFIX + JSONObject().apply {
            put("v", 1)
            put("ts", now)
            IoBrokerAddress.normalize(ioBrokerUrl)?.let { put("iobroker", it) }
            put("registry", registry.toJson())
        }.toString()

    fun parse(raw: String?, now: Long = System.currentTimeMillis()): Payload? {
        if (raw == null || !raw.startsWith(PREFIX) || raw.length > 8_000) return null
        return runCatching {
            val json = JSONObject(raw.removePrefix(PREFIX))
            if (json.getInt("v") != 1) return null
            val ts = json.getLong("ts")
            if (ts < now - VALIDITY_MS || ts > now + CLOCK_SKEW_MS) return null
            val registry = HomePilotAccountRegistry.fromJson(json.getJSONObject("registry")) ?: return null
            if (registry.accounts.isEmpty()) return null
            val url = json.optString("iobroker", "").takeIf { it.isNotBlank() }?.let { IoBrokerAddress.normalize(it) ?: return null }
            Payload(registry, url)
        }.getOrNull()
    }
}

internal fun BitMatrix.toSetupBitmap(): Bitmap {
    val pixels = IntArray(width * height) { index ->
        if (this[index % width, index / width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

/** UI-Signale für Daten, die von gekoppelten Smartphones kamen. */
internal object PeerSyncSignals {
    /** Wird auf dem Main-Thread erhöht, wenn ein gekoppeltes Gerät lokale Daten geändert hat. */
    var dataRevision by mutableIntStateOf(0)
    /** Wird nach einer neuen Kopplung erhöht, damit der automatische Abgleich sofort neu startet. */
    var pairingRevision by mutableIntStateOf(0)
    var lastStatus by mutableStateOf("")
}

/**
 * Tauscht über eine bereits gegenseitig authentifizierte Verbindung Konten, gemeinsame Konfiguration,
 * Benutzerprofile und die ioBroker-Adresse mit einem gekoppelten Smartphone aus. Beide Seiten senden
 * einen Datenstand; übernommen wird nach [HomePilotDocMerge].
 */
internal class HomePilotPeerDataSync(context: Context) : HomePilotPeerSessionHandler {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onAuthenticated(socket: Socket, asServer: Boolean): String {
        socket.soTimeout = IO_TIMEOUT_MS
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        val remote: JSONObject
        if (asServer) {
            remote = readFrame(input)
            writeFrame(output, buildSnapshot())
        } else {
            writeFrame(output, buildSnapshot())
            remote = readFrame(input)
        }
        val result = applySnapshot(remote)
        val changes = listOfNotNull(
            "Konten".takeIf { result.accountsChanged },
            "Räume und Geräte".takeIf { result.configChanged },
            "${result.profilesChanged} Benutzerprofile".takeIf { result.profilesChanged > 0 },
        )
        val message = if (changes.isEmpty()) "Mit gekoppeltem Gerät abgeglichen – keine Änderungen."
            else "Mit gekoppeltem Gerät abgeglichen: ${changes.joinToString(", ")} aktualisiert."
        mainHandler.post {
            PeerSyncSignals.lastStatus = message
            if (result.anyChange) PeerSyncSignals.dataRevision++
        }
        return message
    }

    data class ApplyResult(val accountsChanged: Boolean, val configChanged: Boolean, val profilesChanged: Int) {
        val anyChange get() = accountsChanged || configChanged || profilesChanged > 0
    }

    internal fun buildSnapshot(): JSONObject = synchronized(APPLY_LOCK) {
        val snapshot = JSONObject().put("v", PROTOCOL_VERSION)
        HomePilotAccountStore(appContext).loadedRegistry()?.takeIf { it.accounts.isNotEmpty() }?.let {
            snapshot.put("accounts", it.toJson())
        }
        val docs = HomePilotDocStore(appContext)
        docs.config()?.let { snapshot.put("config", it.toJson()) }
        snapshot.put("profiles", JSONObject().apply { docs.profiles().forEach { (user, profile) -> put(user, profile.toJson()) } })
        IoBrokerAddress.saved(settings())?.let { snapshot.put("iobroker", it) }
        snapshot
    }

    internal fun applySnapshot(remote: JSONObject): ApplyResult = synchronized(APPLY_LOCK) {
        require(remote.optInt("v", -1) == PROTOCOL_VERSION) { "Inkompatible HomePilot-Version auf dem anderen Gerät." }
        val accountsChanged = remote.optJSONObject("accounts")?.let { HomePilotAccountRegistry.fromJson(it) }
            ?.let { HomePilotAccountStore(appContext).mergeRemote(it) } ?: false
        val docs = HomePilotDocStore(appContext)
        val configChanged = remote.optJSONObject("config")?.let { HomePilotSharedConfig.fromJson(it) }
            ?.let { docs.mergeConfig(it) } ?: false
        var profilesChanged = 0
        remote.optJSONObject("profiles")?.let { profiles ->
            require(profiles.length() <= MAX_PROFILES) { "Zu viele Benutzerprofile vom anderen Gerät." }
            profiles.keys().forEach { user ->
                val profile = profiles.optJSONObject(user)?.let { HomePilotUserProfile.fromJson(it) } ?: return@forEach
                if (docs.mergeProfile(user, profile)) profilesChanged++
            }
        }
        remote.optString("iobroker").takeIf { it.isNotBlank() }?.let { url ->
            val settings = settings()
            if (settings.getString("server", null).isNullOrBlank()) IoBrokerAddress.normalize(url)?.let { IoBrokerAddress.store(settings, it) }
        }
        ApplyResult(accountsChanged, configChanged, profilesChanged)
    }

    private fun settings() = appContext.getSharedPreferences("homepilot", Context.MODE_PRIVATE)

    private fun writeFrame(output: DataOutputStream, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_FRAME_BYTES) { "Die lokalen Daten sind für die Übertragung zu groß." }
        output.writeInt(MAGIC)
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    private fun readFrame(input: DataInputStream): JSONObject {
        require(input.readInt() == MAGIC) { "Ungültige Daten vom gekoppelten Gerät." }
        val length = input.readInt()
        require(length in 2..MAX_FRAME_BYTES) { "Ungültige Datenmenge vom gekoppelten Gerät." }
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(bytes, offset, length - offset)
            if (count < 0) throw EOFException("Verbindung während des Abgleichs beendet.")
            offset += count
        }
        return JSONObject(String(bytes, Charsets.UTF_8))
    }

    companion object {
        private const val PROTOCOL_VERSION = 1
        private const val MAGIC = 0x48505344 // "HPSD"
        private const val MAX_FRAME_BYTES = 4 * 1024 * 1024
        private const val MAX_PROFILES = 200
        private const val IO_TIMEOUT_MS = 20_000
        private val APPLY_LOCK = Any()
    }
}
