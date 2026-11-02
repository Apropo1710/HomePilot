package de.homepilot.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned representation of HomePilot's shared ioBroker configuration.
 *
 * This model deliberately does not perform network writes or migration itself.
 * The sync layer must only initialize a missing remote document after confirming
 * that the object/state is genuinely absent; read/ACL/network errors must never
 * be interpreted as an empty configuration.
 */
internal data class HomePilotSharedConfig(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val revision: Long = 0L,
    val updatedAt: Long = 0L,
    val rooms: List<String> = DEFAULT_ROOMS,
    val deviceNames: Map<String, String> = emptyMap(),
    val deviceRooms: Map<String, String> = emptyMap(),
    val deviceKinds: Map<String, String> = emptyMap(),
    val hiddenDeviceItems: Set<String> = emptySet()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("revision", revision)
        put("updatedAt", updatedAt)
        put("rooms", JSONArray(rooms))
        put("deviceNames", stringMapToJson(deviceNames))
        put("deviceRooms", stringMapToJson(deviceRooms))
        put("deviceKinds", stringMapToJson(deviceKinds))
        put("hiddenDeviceItems", JSONArray(hiddenDeviceItems.sorted()))
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        val DEFAULT_ROOMS = listOf(
            "Wohnzimmer", "Küche", "Schlafzimmer", "Bad",
            "Hauswirtschaft", "Nicht zugeordnet"
        )

        /**
         * Returns null for malformed or unsupported documents instead of silently
         * replacing remote data with defaults.
         */
        fun fromJson(json: JSONObject): HomePilotSharedConfig? {
            val version = json.optInt("schemaVersion", -1)
            if (version !in 1..CURRENT_SCHEMA_VERSION) return null

            val roomsArray = json.optJSONArray("rooms") ?: return null
            val rooms = (0 until roomsArray.length())
                .mapNotNull { roomsArray.optString(it).takeIf(String::isNotBlank) }
                .distinct()
            if (rooms.isEmpty()) return null

            val names = readStringMap(json.optJSONObject("deviceNames")) ?: return null
            val mappings = readStringMap(json.optJSONObject("deviceRooms")) ?: return null
            val kinds = readStringMap(json.optJSONObject("deviceKinds")) ?: return null
            val hiddenArray = json.optJSONArray("hiddenDeviceItems") ?: JSONArray()
            val hidden = (0 until hiddenArray.length())
                .mapNotNull { hiddenArray.optString(it).takeIf(String::isNotBlank) }
                .toSet()

            return HomePilotSharedConfig(
                schemaVersion = version,
                revision = json.optLong("revision", 0L).coerceAtLeast(0L),
                updatedAt = json.optLong("updatedAt", 0L).coerceAtLeast(0L),
                rooms = rooms,
                deviceNames = names,
                deviceRooms = mappings,
                deviceKinds = kinds,
                hiddenDeviceItems = hidden
            )
        }

        /**
         * Creates the initial document from existing local preferences.
         * Call only after the sync layer has verified that no remote config exists.
         */
        fun initialFromLocal(
            rooms: List<String>,
            deviceNames: Map<String, String>,
            deviceRooms: Map<String, String>,
            deviceKinds: Map<String, String>,
            hiddenDeviceItems: Set<String>,
            now: Long = System.currentTimeMillis()
        ): HomePilotSharedConfig = HomePilotSharedConfig(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            revision = 1L,
            updatedAt = now,
            rooms = rooms.filter(String::isNotBlank).distinct().ifEmpty { DEFAULT_ROOMS },
            deviceNames = deviceNames,
            deviceRooms = deviceRooms,
            deviceKinds = deviceKinds,
            hiddenDeviceItems = hiddenDeviceItems
        )

        private fun readStringMap(json: JSONObject?): Map<String, String>? {
            if (json == null) return emptyMap()
            val result = LinkedHashMap<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.opt(key)
                if (value !is String) return null
                result[key] = value
            }
            return result
        }

        private fun stringMapToJson(values: Map<String, String>) = JSONObject().apply {
            values.toSortedMap().forEach { (key, value) -> put(key, value) }
        }
    }
}

/**
 * Per-ioBroker-user preferences. Never merge this document into shared config.
 */
internal data class HomePilotUserProfile(
    val schemaVersion: Int = HomePilotSharedConfig.CURRENT_SCHEMA_VERSION,
    val revision: Long = 0L,
    val updatedAt: Long = 0L,
    val favoriteIds: Set<String> = emptySet(),
    val favoriteOrder: List<String> = emptyList(),
    val disconnectTimeoutMinutes: Int = 30
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("revision", revision)
        put("updatedAt", updatedAt)
        put("favoriteIds", JSONArray(favoriteIds.sorted()))
        put("favoriteOrder", JSONArray(favoriteOrder))
        put("disconnectTimeoutMinutes", disconnectTimeoutMinutes)
    }

    companion object {
        fun fromJson(json: JSONObject): HomePilotUserProfile? {
            val version = json.optInt("schemaVersion", -1)
            if (version !in 1..HomePilotSharedConfig.CURRENT_SCHEMA_VERSION) return null
            val timeout = json.optInt("disconnectTimeoutMinutes", 30)
            if (timeout !in setOf(30, 60, 0)) return null
            val favorites = json.optJSONArray("favoriteIds") ?: JSONArray()
            val order = json.optJSONArray("favoriteOrder") ?: JSONArray()
            return HomePilotUserProfile(
                schemaVersion = version,
                revision = json.optLong("revision", 0L).coerceAtLeast(0L),
                updatedAt = json.optLong("updatedAt", 0L).coerceAtLeast(0L),
                favoriteIds = (0 until favorites.length())
                    .mapNotNull { favorites.optString(it).takeIf(String::isNotBlank) }.toSet(),
                favoriteOrder = (0 until order.length())
                    .mapNotNull { order.optString(it).takeIf(String::isNotBlank) },
                disconnectTimeoutMinutes = timeout
            )
        }
    }
}

/** Encode arbitrary ioBroker usernames safely as a state/object ID segment. */
internal fun homePilotProfileIdSegment(username: String): String {
    val normalized = username.trim()
    require(normalized.isNotBlank()) { "ioBroker-Benutzername darf nicht leer sein" }
    return normalized.map { char ->
        if (char.isLetterOrDigit() || char == '_' || char == '-') char else '_'
    }.joinToString("").take(80).ifBlank { "user" }
}
