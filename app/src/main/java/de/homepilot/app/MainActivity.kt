package de.homepilot.app

import android.os.Bundle
import android.content.Context
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.content.Intent
import android.provider.Settings
import org.json.JSONObject
import org.json.JSONArray
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Blue = Color(0xFF1769AA)
private val Green = Color(0xFF16845B)
private val Muted = Color(0xFF263746)
private val LightBg = Color(0xFFF3F6FA)
private val DarkBg = Color(0xFF101923)
private val LightText = Color(0xFF172334)
private val DarkText = Color(0xFFEAF1F8)

private class IoBrokerClient(private val credentialStore: IoBrokerCredentialsStore) {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var socket: IoBrokerSocket? = null
    @Volatile private var connected = false
    @Volatile private var activeAddress: String? = null
    private val stateCache = java.util.concurrent.ConcurrentHashMap<String, JSONObject>()
    private val pendingConnect = java.util.concurrent.CopyOnWriteArrayList<(Boolean, String) -> Unit>()

    fun disconnect() {
        connected = false
        socket?.off()
        socket?.disconnect()
        socket?.close()
        socket = null
        activeAddress = null
    }

    private fun post(block: () -> Unit) = mainHandler.post(block)

    fun connect(url: String, onResult: (Boolean, String) -> Unit) {
        val address = url.trim().trimEnd('/')
        if (socket?.isActive() == true && connected && activeAddress == address) {
            post { onResult(true, "WebSocket-Verbindung zu ioBroker besteht bereits") }
            return
        }
        if (socket?.isActive() == true && activeAddress == address) {
            // Socket already owns the reconnect loop; don't replace it on activity resume.
            post { onResult(false, "WebSocket-Wiederverbindung läuft bereits") }
            return
        }
        disconnect()
        activeAddress = address
        try {
            require(address.startsWith("ws://") || address.startsWith("wss://")) {
                "WebSocket-Adresse muss mit ws:// oder wss:// beginnen"
            }
            val client = IoBrokerSocket(address, "name=homepilot.android")
            socket = client
            var reported = false
            client.on("connect") {
                reported = false
                client.emit("authenticate", IoBrokerAck { args ->
                    val ok = args.firstOrNull() as? Boolean ?: false
                    if (!ok) {
                        post { if (!reported) { reported = true; onResult(false, "ioBroker hat die Verbindung nicht authentifiziert.") } }
                    } else {
                        connected = true
                        client.emit("subscribe", "*")
                        client.emit("subscribeObjects", "*")
                        // Authentication is sufficient; do not block startup on an unrelated Shelly test state.
                        post {
                            if (!reported) {
                                reported = true
                                onResult(true, "WebSocket-Verbindung zu ioBroker hergestellt")
                            }
                        }
                    }
                })
            }
            client.on("stateChange") { args ->
                val id = args.getOrNull(0)?.toString() ?: return@on
                val state = args.getOrNull(1) as? JSONObject
                if (state == null) stateCache.remove(id) else stateCache[id] = state
            }
            client.on("disconnect") {
                if (socket === client) {
                    connected = false
                    post { if (socket === client) onResult(false, "WebSocket-Verbindung unterbrochen – Wiederverbindung läuft") }
                }
            }
            client.on("connect_error") { args ->
                if (socket === client) {
                    connected = false
                    val message = args.firstOrNull()?.toString() ?: "Verbindung fehlgeschlagen"
                    post { if (socket === client && !reported) { reported = true; onResult(false, "WebSocket: $message") } }
                }
            }
            client.connect()
        } catch (e: Exception) {
            connected = false
            onResult(false, e.message ?: e.javaClass.simpleName)
        }
    }

    fun cachedNumber(id: String): Double? {
        val raw = stateCache[id]?.opt("val")
        return when (raw) { is Number -> raw.toDouble(); is String -> raw.toDoubleOrNull(); else -> null }
    }

    fun cachedText(id: String): String? {
        val raw = stateCache[id]?.opt("val")
        return when (raw) { is String -> raw.trim().takeIf { it.isNotBlank() }; is Number, is Boolean -> raw.toString(); else -> null }
    }

    private fun requireSocket(): IoBrokerSocket =
        socket?.takeIf { connected && it.connected() } ?: throw IllegalStateException("Keine WebSocket-Verbindung zu ioBroker")

    /** Reads an ioBroker object; null object means it does not exist or could not be decoded. */
    fun getObject(id: String, callback: (JSONObject?, String?) -> Unit) {
        val client = try { requireSocket() } catch (e: Exception) {
            post { callback(null, e.message ?: "Nicht verbunden") }
            return
        }
        client.emit("getObject", id, IoBrokerAck { args ->
            val error = args.firstOrNull()?.takeUnless { it == JSONObject.NULL }
            val raw = args.getOrNull(1)
            val obj = when (raw) {
                is JSONObject -> raw
                is String -> try { JSONObject(raw) } catch (_: Exception) { null }
                is Map<*, *> -> JSONObject().apply {
                    raw.forEach { (key, value) -> if (key is String) put(key, value) }
                }
                else -> null
            }
            post { callback(obj, error?.toString()) }
        })
    }

    /** Creates or updates an ioBroker object. The supplied definition must be valid. */
    fun setObject(id: String, objectDefinition: JSONObject, callback: (Boolean, String?) -> Unit) {
        val client = try { requireSocket() } catch (e: Exception) {
            post { callback(false, e.message ?: "Nicht verbunden") }
            return
        }
        client.emit("setObject", id, objectDefinition, IoBrokerAck { args ->
            val error = args.firstOrNull()?.takeUnless { it == JSONObject.NULL }
            post { callback(error == null, error?.toString()) }
        })
    }

    /**
     * Reads a JSON document stored as a string-valued ioBroker state.
     * Missing or malformed data is not silently converted to an empty object.
     */
    fun getJsonState(id: String, callback: (JSONObject?, String?) -> Unit) {
        val client = try { requireSocket() } catch (e: Exception) {
            post { callback(null, e.message ?: "Nicht verbunden") }
            return
        }
        client.emit("getState", id, IoBrokerAck { args ->
            val error = args.firstOrNull()?.takeUnless { it == JSONObject.NULL }
            val state = args.getOrNull(1) as? JSONObject
            if (state != null) stateCache[id] = state
            val raw = state?.opt("val")
            val json = when (raw) {
                is JSONObject -> raw
                is String -> try { JSONObject(raw) } catch (_: Exception) { null }
                else -> null
            }
            val resultError = when {
                error != null -> error.toString()
                state == null -> "Datenpunkt $id existiert noch nicht oder liefert keinen Zustand"
                json == null -> "Datenpunkt $id enthält kein gültiges JSON-Objekt"
                else -> null
            }
            post { callback(json, resultError) }
        })
    }

    /** Stores JSON serialized as a string in ioBroker state.val. */
    fun setJsonState(id: String, value: JSONObject, callback: (Boolean, String?) -> Unit) {
        val client = try { requireSocket() } catch (e: Exception) {
            post { callback(false, e.message ?: "Nicht verbunden") }
            return
        }
        client.emit("setState", id, JSONObject().put("val", value.toString()).put("ack", false), IoBrokerAck { args ->
            val error = args.firstOrNull()?.takeUnless { it == JSONObject.NULL }
            post { callback(error == null, error?.toString()) }
        })
    }

    /**
     * Reads a JSON state, creating it only when its ioBroker object is confirmed
     * absent. Existing objects with missing/unreadable state are treated as errors:
     * they must never be silently initialized from local preferences.
     */
    fun ensureJsonState(
        id: String,
        displayName: String,
        initialValue: JSONObject,
        callback: (JSONObject?, Boolean, String?) -> Unit
    ) {
        getObject(id) { existingObject, objectError ->
            if (objectError != null) {
                callback(null, false, "Objekt $id konnte nicht gelesen werden: $objectError")
                return@getObject
            }
            if (existingObject != null) {
                getJsonState(id) { json, stateError ->
                    if (stateError != null || json == null) {
                        callback(null, false, "Vorhandenes Objekt $id hat keinen lesbaren JSON-Zustand. Keine automatische Initialisierung. $stateError")
                    } else {
                        callback(json, false, null)
                    }
                }
                return@getObject
            }

            val definition = JSONObject()
                .put("type", "state")
                .put("common", JSONObject()
                    .put("name", displayName)
                    .put("type", "string")
                    .put("role", "json")
                    .put("read", true)
                    .put("write", true))
                .put("native", JSONObject())

            setObject(id, definition) { objectCreated, createError ->
                if (!objectCreated) {
                    callback(null, false, "Objekt $id konnte nicht angelegt werden: $createError")
                    return@setObject
                }
                setJsonState(id, initialValue) { stateWritten, stateWriteError ->
                    if (!stateWritten) {
                        callback(null, false, "Erstzustand für $id konnte nicht geschrieben werden: $stateWriteError")
                    } else {
                        callback(initialValue, true, null)
                    }
                }
            }
        }
    }

    fun getState(baseUrl: String, id: String, callback: (Boolean?, String?) -> Unit) {
        val client = try { requireSocket() } catch (e: Exception) { callback(null, e.message); return }
        client.emit("getState", id, IoBrokerAck { args ->
            val error = args.firstOrNull()
            val state = args.getOrNull(1) as? JSONObject
            if (state != null) stateCache[id] = state
            val value = state?.opt("val")
            post {
                if (error != null) callback(null, error.toString())
                else when (value) {
                    is Boolean -> callback(value, null)
                    is String -> callback(value.toBooleanStrictOrNull(), null)
                    else -> callback(null, "Kein boolescher Wert für $id")
                }
            }
        })
    }

    fun getTextState(baseUrl: String, id: String, callback: (String?) -> Unit) {
        val client = try { requireSocket() } catch (_: Exception) { callback(null); return }
        client.emit("getState", id, IoBrokerAck { args ->
            val state = args.getOrNull(1) as? JSONObject
            if (state != null) stateCache[id] = state
            val value = state?.opt("val")
            val text = when (value) { is String -> value.trim(); is Number, is Boolean -> value.toString(); else -> "" }
            post { callback(text.takeIf { it.isNotBlank() }) }
        })
    }

    fun getNumericState(baseUrl: String, id: String, callback: (Double?) -> Unit) {
        val client = try { requireSocket() } catch (_: Exception) { callback(null); return }
        client.emit("getState", id, IoBrokerAck { args ->
            val state = args.getOrNull(1) as? JSONObject
            if (state != null) stateCache[id] = state
            val raw = state?.opt("val")
            val value = when (raw) { is Number -> raw.toDouble(); is String -> raw.toDoubleOrNull(); else -> null }
            post { callback(value?.takeIf { it.isFinite() }) }
        })
    }

    private fun decodeObjectsReply(args: Array<Any?>): Pair<JSONObject?, String?> {
        fun asObjects(value: Any?): JSONObject? = when (value) {
            is JSONObject -> value
            is String -> try { JSONObject(value) } catch (_: Exception) { null }
            is Map<*, *> -> JSONObject().apply {
                value.forEach { (key, item) -> if (key is String) put(key, item) }
            }
            else -> null
        }
        val first = args.getOrNull(0)
        val second = args.getOrNull(1)
        val viewResult = (second as? JSONObject) ?: (first as? JSONObject)
        val rows = viewResult?.optJSONArray("rows")
        if (rows != null) {
            val mapped = JSONObject()
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val id = row.optString("id")
                val value = row.optJSONObject("value")
                if (id.isNotBlank() && value != null) mapped.put(id, value)
            }
            return mapped to null
        }
        val firstObjects = asObjects(first)
        if (firstObjects != null) return firstObjects to null
        val secondObjects = asObjects(second)
        if (secondObjects != null) {
            val error = first?.takeUnless { it == JSONObject.NULL }?.toString()
            return secondObjects to error
        }
        val types = args.joinToString { it?.javaClass?.simpleName ?: "null" }
        val detail = "Antwortformat unerwartet (Argumente=${args.size}, Typen=[$types], erstes Argument=${first?.toString()?.take(160) ?: "null"})"
        return null to (first?.takeUnless { it == JSONObject.NULL }?.toString() ?: detail)
    }

    private val objectQueryDiagnostics = java.util.concurrent.ConcurrentHashMap<String, String>()
    fun objectQueryDiagnostic(adapter: String): String = objectQueryDiagnostics[adapter] ?: "$adapter: noch keine Antwort"

    fun discoverSwitches(baseUrl: String, adapter: String, callback: (List<DeviceItem>, String?) -> Unit) {
        val client = try { requireSocket() } catch (e: Exception) { callback(emptyList(), e.message); return }
        client.emit("getObjectView", "system", "state", JSONObject().put("startkey", "$adapter.").put("endkey", "$adapter.\u9999"), IoBrokerAck { args ->
            val (objects, replyError) = decodeObjectsReply(args)
            if (replyError != null || objects == null) {
                val detail = "$adapter getObjectView: $replyError"
                objectQueryDiagnostics[adapter] = detail
                post { callback(emptyList(), detail) }
                return@IoBrokerAck
            }
            val allIds = mutableListOf<String>()
            val iterator = objects.keys()
            while (iterator.hasNext()) allIds.add(iterator.next().toString())
            val candidates = mutableListOf<DeviceItem>()
            for (id in allIds) {
                val obj = objects.optJSONObject(id) ?: continue
                val common = obj.optJSONObject("common") ?: continue
                val role = common.optString("role", "").lowercase()
                val type = common.optString("type", "")
                val rawName = common.opt("name")
                val name = when (rawName) { is JSONObject -> rawName.optString("de").ifBlank { rawName.optString("en") }; else -> rawName?.toString().orEmpty() }
                val isOutput = when (adapter) {
                    "shelly.0" -> id.matches(Regex("shelly\\.0\\..*(?:Relay\\d*\\.(?:Switch|Output)|\\.Switch)$", RegexOption.IGNORE_CASE))
                    "hue.0" -> type == "boolean" && (id.substringAfterLast('.').lowercase() in setOf("on", "all_on", "state", "switch") || role.contains("switch") || role.contains("light"))
                    else -> type == "boolean" && common.optBoolean("write", false) && (id.substringAfterLast('.').lowercase() in setOf("on", "switch", "state", "power", "light", "relay") || role.contains("switch") || role.contains("light") || role.contains("button"))
                }
                if (!isOutput) continue
                val parent = id.substringBeforeLast('.')
                fun objectName(objectId: String): String {
                    val c = objects.optJSONObject(objectId)?.optJSONObject("common") ?: return ""
                    val raw = c.opt("name")
                    val v = when (raw) { is JSONObject -> raw.optString("de").ifBlank { raw.optString("en") }; else -> raw?.toString().orEmpty() }.trim()
                    return if (v.isBlank() || v.startsWith("{") || v.equals("switch", true) || v.equals("state", true) || v.equals("output", true) || v.matches(Regex("(?i)(relay|channel|device)\\s*#?\\d*"))) "" else v
                }
                var ancestor = parent
                var friendly = ""
                while (ancestor.startsWith("$adapter.") && ancestor.isNotBlank()) {
                    friendly = objectName(ancestor)
                    if (friendly.isNotBlank()) break
                    val next = ancestor.substringBeforeLast('.', "")
                    if (next.isBlank() || next == ancestor || next == adapter) break
                    ancestor = next
                }
                val fallback = friendly.ifBlank { name.trim().takeIf { it.isNotBlank() && !it.startsWith("{") && !it.equals("switch", true) } ?: when(adapter) { "hue.0" -> "Hue-Licht"; "tuya" -> "Tuya-Gerät"; else -> "Shelly-Gerät" } }
                fun related(vararg endings: String): String? = allIds.firstOrNull { candidate ->
                    candidate.startsWith("$parent.", true) && candidate.substringAfterLast('.').lowercase() in endings.map { it.lowercase() } &&
                        objects.optJSONObject(candidate)?.optJSONObject("common")?.optBoolean("write", false) == true
                }
                val power = allIds.firstOrNull { candidate -> candidate.substringBeforeLast('.') == parent &&
                    (candidate.substringAfterLast('.').contains("power", true) || objects.optJSONObject(candidate)?.optJSONObject("common")?.optString("unit") == "W") }
                val energy = allIds.firstOrNull { candidate -> candidate.substringBeforeLast('.') == parent &&
                    (candidate.substringAfterLast('.').contains("energy", true) || objects.optJSONObject(candidate)?.optJSONObject("common")?.optString("unit").orEmpty().let { it.equals("Wh", true) || it.equals("kWh", true) }) }
                val unit = energy?.let { objects.optJSONObject(it)?.optJSONObject("common")?.optString("unit", "").orEmpty() }.orEmpty()
                val hue = adapter == "hue.0"
                candidates.add(DeviceItem(fallback, when { hue -> "Hue · ioBroker"; adapter == "tuya" -> "Tuya · ioBroker"; else -> "Shelly · ioBroker" },
                    if (hue) "light" else "plug", false, "", id, power, true, room = "Nicht zugeordnet",
                    brightnessId = if (hue) related("bri", "brightness", "level", "dim") else null,
                    colorTempId = if (hue) related("ct", "colortemperature", "color_temperature", "temperature") else null,
                    hueId = if (hue) related("hue", "colorhue") else null, saturationId = if (hue) related("sat", "saturation") else null,
                    energyId = energy, energyUnit = unit))
            }
            val sampleIds = allIds.take(5).joinToString()
            objectQueryDiagnostics[adapter] = "$adapter getObjectView: ${allIds.size} Objekte empfangen, ${candidates.size} passende Schalter/Lichter. Beispiele: ${sampleIds.ifBlank { "keine" }}"
            if (adapter != "shelly.0" || candidates.isEmpty()) {
                post { callback(candidates.sortedBy { it.name.lowercase() }, null) }
            } else {
                val resolved = java.util.Collections.synchronizedList(candidates.toMutableList())
                val remaining = java.util.concurrent.atomic.AtomicInteger(candidates.size)
                candidates.forEachIndexed { index, item ->
                    val root = item.stateId.orEmpty().replace(Regex("\\.Relay\\d*\\.(Switch|Output)$", RegexOption.IGNORE_CASE), "")
                    getTextState(baseUrl, "$root.name") { liveName ->
                        if (!liveName.isNullOrBlank()) resolved[index] = item.copy(name = liveName.trim())
                        if (remaining.decrementAndGet() == 0) post { callback(resolved.sortedBy { it.name.lowercase() }, null) }
                    }
                }
            }
        })
    }

    fun inspectAdapterStates(baseUrl: String, adapter: String, callback: (Set<String>, String?) -> Unit) {
        val client = try { requireSocket() } catch (e: Exception) {
            callback(emptySet(), e.message ?: "Keine WebSocket-Verbindung")
            return
        }
        client.emit("getObjectView", "system", "state", JSONObject().put("startkey", "$adapter.").put("endkey", "$adapter.\u9999"), IoBrokerAck { args ->
            val (objects, replyError) = decodeObjectsReply(args)
            if (replyError != null || objects == null) {
                val detail = "$adapter getObjectView: $replyError"
                post { callback(emptySet(), detail) }
                return@IoBrokerAck
            }
            val ids = mutableSetOf<String>()
            val iterator = objects.keys()
            while (iterator.hasNext()) ids.add(iterator.next())
            post { callback(ids, null) }
        })
    }

    fun setState(baseUrl: String, id: String, value: Boolean, callback: (Boolean, String) -> Unit) {
        val client = try { requireSocket() } catch (e: Exception) { callback(false, e.message ?: "Nicht verbunden"); return }
        client.emit("setState", id, JSONObject().put("val", value).put("ack", false), IoBrokerAck { args ->
            val error = args.firstOrNull()
            post { callback(error == null, if (error == null) "Schaltbefehl gesendet" else "Schalten fehlgeschlagen: $error") }
        })
    }

    fun setTextState(baseUrl: String, id: String, value: String, callback: (Boolean, String) -> Unit) {
        setAnyState(id, value, "Befehl gesendet", "Befehl fehlgeschlagen", callback)
    }

    fun setNumericState(baseUrl: String, id: String, value: Double, callback: (Boolean, String) -> Unit) {
        setAnyState(id, value, "Lichteinstellung gespeichert", "Lichtwert konnte nicht gesetzt werden", callback)
    }

    private fun setAnyState(id: String, value: Any, success: String, failure: String, callback: (Boolean, String) -> Unit) {
        val client = try { requireSocket() } catch (e: Exception) { callback(false, e.message ?: "Nicht verbunden"); return }
        client.emit("setState", id, JSONObject().put("val", value).put("ack", false), IoBrokerAck { args ->
            val error = args.firstOrNull()
            post { callback(error == null, if (error == null) success else "$failure: $error") }
        })
    }
}
/** ioBroker-backed HomePilot account registry with revision conflict protection. */
private class HomePilotAccountRepository(private val client: IoBrokerClient) {
    companion object { const val STATE_ID = "0_userdata.0.HomePilot.accounts" }

    fun load(callback: (HomePilotAccountRegistry?, String?) -> Unit) {
        client.ensureJsonState(STATE_ID, "HomePilot account registry", HomePilotAccountRegistry().toJson()) { json, _, error ->
            if (error != null || json == null) {
                callback(null, error ?: "Konten konnten nicht geladen werden.")
                return@ensureJsonState
            }
            val registry = HomePilotAccountRegistry.fromJson(json)
            if (registry == null) callback(null, "Die HomePilot-Kontenliste ist ungültig oder hat ein unbekanntes Format.")
            else callback(registry, null)
        }
    }

    fun save(expectedRevision: Long, registry: HomePilotAccountRegistry, callback: (Boolean, String?) -> Unit) {
        load { current, error ->
            if (error != null || current == null) {
                callback(false, error ?: "Kontenliste konnte nicht geprüft werden.")
                return@load
            }
            if (current.revision != expectedRevision) {
                callback(false, "Die Kontenliste wurde zwischenzeitlich geändert. Bitte erneut laden.")
                return@load
            }
            if (registry.revision != expectedRevision + 1) {
                callback(false, "Ungültige Versionsnummer der Kontenliste.")
                return@load
            }
            client.setJsonState(STATE_ID, registry.toJson(), callback)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureWaterNotificationChannel()
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7301)
        }
        setContent { HomePilotApp() }
    }

    private fun ensureWaterNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("homepilot_water_alarm", "HomePilot Wasseralarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Sofortige Warnung bei erkanntem Wasser"
                enableVibration(true)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Zuhause", Icons.Default.Home),
    ROOMS("Räume", Icons.Default.HomeWork),
    FAVORITES("Favoriten", Icons.Default.Star),
    ENERGY("Energie", Icons.Default.Bolt),
    MORE("Mehr", Icons.Default.MoreHoriz),
    DEVICE_HEALTH("Geräteprobleme", Icons.Default.HealthAndSafety),
    LIGHTS("Licht", Icons.Default.Lightbulb),
    DEVICES("Geräte", Icons.Default.Devices),
    SAFETY("Sicherheit", Icons.Default.Shield),
    SETTINGS("Einstellungen", Icons.Default.Settings),
    MOVA("MOVA 1000", Icons.Default.Park),
    ROIDMI("Roidmi EVE Plus", Icons.Default.CleaningServices)
}

private data class DeviceItem(
    val name: String,
    val subtitle: String,
    val kind: String,
    val initialOn: Boolean = false,
    val detail: String = "",
    val stateId: String? = null,
    val powerId: String? = null,
    val writable: Boolean = true,
    val powerWatts: Double? = null,
    val room: String = "Nicht zugeordnet",
    val brightnessId: String? = null,
    val colorTempId: String? = null,
    val hueId: String? = null,
    val saturationId: String? = null,
    val currentBrightness: Double? = null,
    val currentColorTemp: Double? = null,
    val currentHue: Double? = null,
    val currentSaturation: Double? = null,
    val energyId: String? = null,
    val energyUnit: String? = null,
    val energyValue: Double? = null
)

private fun postWaterAlarmNotification(context: Context) {
    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel("homepilot_water_alarm") == null) {
        manager.createNotificationChannel(NotificationChannel("homepilot_water_alarm", "HomePilot Wasseralarm", NotificationManager.IMPORTANCE_HIGH))
    }
    val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(context, "homepilot_water_alarm")
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(context)
    }.setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("HomePilot: Wasseralarm!")
        .setContentText("Der Wassermelder Küche hat Wasser erkannt.")
        .setStyle(Notification.BigTextStyle().bigText("Der Shelly Flood in der Küche meldet Wasser. Bitte sofort prüfen."))
        .setCategory(Notification.CATEGORY_ALARM)
        .setAutoCancel(true)
        .build()
    manager.notify(7302, notification)
}

@Composable
private fun HomePilotApp() {
    var dark by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(Tab.HOME) }
    var displayedTab by remember { mutableStateOf(Tab.HOME) }
    val pageAlpha = remember { Animatable(1f) }
    LaunchedEffect(selectedTab) {
        if (displayedTab != selectedTab) {
            pageAlpha.animateTo(0f, animationSpec = tween(durationMillis = 90))
            displayedTab = selectedTab
            pageAlpha.animateTo(1f, animationSpec = tween(durationMillis = 190))
        }
    }
    var tabSwipeDirection by remember { mutableIntStateOf(1) }
    var tabHistory by remember { mutableStateOf(listOf<Tab>()) }
    fun navigateTo(tab: Tab) {
        if (tab != selectedTab) {
            val mainTabs = listOf(Tab.HOME, Tab.FAVORITES, Tab.ENERGY)
            val oldIndex = mainTabs.indexOf(selectedTab)
            val newIndex = mainTabs.indexOf(tab)
            tabSwipeDirection = if (oldIndex >= 0 && newIndex >= 0) {
                if (newIndex >= oldIndex) 1 else -1
            } else 1
            tabHistory = tabHistory + selectedTab
            selectedTab = tab
        }
    }
    var showDemoNotice by remember { mutableStateOf(false) }
    val lights = remember { mutableStateListOf<DeviceItem>() }
    val devices = remember { mutableStateListOf<DeviceItem>() }
    val discoveredLights = remember { mutableStateListOf<DeviceItem>() }
    val discoveredTuyaDevices = remember { mutableStateListOf<DeviceItem>() }
    var showTuyaImportDialog by remember { mutableStateOf(false) }
    var selectedTuyaIds by remember { mutableStateOf(setOf<String>()) }
    var selectedTuyaRooms by remember { mutableStateOf(emptyMap<String, String>()) }
    var discoveredNames by remember { mutableStateOf(emptyMap<String, String>()) }
    var showLightImportDialog by remember { mutableStateOf(false) }
    var scanResultMessage by remember { mutableStateOf<String?>(null) }
    var selectedLightIds by remember { mutableStateOf(setOf<String>()) }
    var floodAlarm by remember { mutableStateOf(false) }
    var floodBattery by remember { mutableIntStateOf(100) }
    var floodBatteryKnown by remember { mutableStateOf(false) }
    var floodKnown by remember { mutableStateOf(false) }
    var floodTemperature by remember { mutableStateOf<Double?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("homepilot", Context.MODE_PRIVATE) }
    val appScope = rememberCoroutineScope()
    var inactivityMinutes by remember { mutableIntStateOf(prefs.getInt("disconnectAfterMinutes", 30).coerceIn(0, 60)) }
    val isFreshInstall = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.firstInstallTime >= packageInfo.lastUpdateTime - 5_000L
        } catch (_: Exception) { false }
    }
    var showBatteryOptimizationDialog by remember { mutableStateOf(isFreshInstall && !prefs.getBoolean("batteryHintShown", false)) }
    LaunchedEffect(isFreshInstall) {
        if (!isFreshInstall && !prefs.getBoolean("batteryHintShown", false)) prefs.edit().putBoolean("batteryHintShown", true).apply()
    }
    var favoriteIds by remember { mutableStateOf(prefs.getStringSet("favoriteDevices", emptySet())?.toSet() ?: emptySet()) }
    var favoriteOrder by remember { mutableStateOf(prefs.getString("favoriteOrder", "")?.split("|")?.filter { it.isNotBlank() } ?: emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var energyPrice by remember { mutableStateOf(prefs.getString("energyPrice", "0.35") ?: "0.35") }
    var rooms by remember { mutableStateOf(prefs.getString("rooms", "Wohnzimmer|Küche|Schlafzimmer|Bad|Hauswirtschaft|Nicht zugeordnet")!!.split("|").toMutableList()) }
    var customNames by remember { mutableStateOf(readStringMap(prefs.getString("deviceNames", "{}"))) }
    var deviceRooms by remember { mutableStateOf(readStringMap(prefs.getString("deviceRooms", "{}"))) }
    var deviceKinds by remember { mutableStateOf(readStringMap(prefs.getString("deviceKinds", "{}"))) }
    var hiddenIds by remember { mutableStateOf(prefs.getStringSet("hiddenDevices", emptySet())?.toSet() ?: emptySet()) }
    var importedLightIds by remember { mutableStateOf(prefs.getStringSet("importedLights", emptySet())?.toSet() ?: emptySet()) }
    var hiddenItems by remember { mutableStateOf(readHiddenItems(prefs.getString("hiddenDeviceItems", "[]"))) }
    var confirmRemoveDevice by remember { mutableStateOf<DeviceItem?>(null) }
    var confirmDeleteRoom by remember { mutableStateOf<String?>(null) }
    var showHiddenDevices by remember { mutableStateOf(false) }
    var selectedRoom by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = selectedRoom != null || selectedTab != Tab.HOME || tabHistory.isNotEmpty()) {
        if (selectedRoom != null) {
            selectedRoom = null
        } else if (tabHistory.isNotEmpty()) {
            selectedTab = tabHistory.last()
            tabHistory = tabHistory.dropLast(1)
        } else {
            selectedTab = Tab.HOME
        }
    }
    var showRoomDialog by remember { mutableStateOf(false) }
    var roomNameInput by remember { mutableStateOf("") }
    var editDevice by remember { mutableStateOf<DeviceItem?>(null) }
    var editName by remember { mutableStateOf("") }
    var editRoom by remember { mutableStateOf("Nicht zugeordnet") }
    var editKind by remember { mutableStateOf("plug") }
    var editKindExpanded by remember { mutableStateOf(false) }
    var editRoomExpanded by remember { mutableStateOf(false) }
    val editableDeviceKinds = listOf("Steckdose" to "plug", "Lampe" to "light", "Sonstiges" to "device")
    var scanStatus by remember { mutableStateOf("") }
    fun applyCustomConfig() {
        for (i in devices.indices) {
            val item = devices[i]
            val id = item.stateId ?: continue
            devices[i] = item.copy(name = customNames[id] ?: item.name, room = deviceRooms[id] ?: when { id.contains("6EDB6C") -> "Wohnzimmer"; id.contains("376CC3") -> "Hauswirtschaft"; else -> "Nicht zugeordnet" }, kind = deviceKinds[id] ?: item.kind)
        }
        for (i in lights.indices) {
            val item = lights[i]; val id = item.stateId ?: continue
            lights[i] = item.copy(name = customNames[id] ?: item.name, room = deviceRooms[id] ?: "Nicht zugeordnet", kind = deviceKinds[id] ?: item.kind)
        }
    }
    var serverUrl by remember { mutableStateOf((prefs.getString("server", null) ?: "ws://192.168.178.54:8084").trim().trimEnd('/').takeIf { it.startsWith("ws://") || it.startsWith("wss://") } ?: "ws://192.168.178.54:8084") }
    var weatherCity by remember { mutableStateOf((prefs.getString("weatherCity", "Kaan") ?: "Kaan").let { if (it.equals("56751 Kaan", ignoreCase = true)) "Kaan" else it }) }
        var connectionStatus by remember { mutableStateOf("Nicht verbunden") }
    var connected by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var showConnectionAlert by remember { mutableStateOf(false) }
    var washOn by remember { mutableStateOf(false) }
    var waterOn by remember { mutableStateOf(false) }
    val credentialStore = remember { IoBrokerCredentialsStore(context) }
    val ioClient = remember { IoBrokerClient(credentialStore) }
    var ioBrokerUsername by remember { mutableStateOf(credentialStore.username()) }
    var ioBrokerPassword by remember { mutableStateOf(credentialStore.password()) }
    var loginAccepted by remember { mutableStateOf(false) }
    var homePilotAuthenticated by remember { mutableStateOf(false) }
    var homePilotRegistry by remember { mutableStateOf<HomePilotAccountRegistry?>(null) }
    var homePilotAccount by remember { mutableStateOf<HomePilotAccount?>(null) }
    var homePilotUsername by remember { mutableStateOf("") }
    var homePilotPassword by remember { mutableStateOf("") }
    var homePilotPasswordConfirmation by remember { mutableStateOf("") }
    var homePilotDisplayName by remember { mutableStateOf("") }
    var homePilotAccountLoading by remember { mutableStateOf(false) }
    val homePilotAccountRepository = remember { HomePilotAccountRepository(ioClient) }
    fun loadHomePilotAccounts() {
        homePilotAccountLoading = true
        homePilotAccountRepository.load { registry, error ->
            homePilotAccountLoading = false
            homePilotRegistry = registry
            if (error != null) connectionStatus = "HomePilot-Konten konnten nicht geladen werden: $error"
        }
    }
    fun saveHomePilotRegistry(expectedRevision: Long, next: HomePilotAccountRegistry, onSaved: (Boolean, String?) -> Unit) {
        homePilotAccountRepository.save(expectedRevision, next) { success, error ->
            if (success) homePilotRegistry = next
            onSaved(success, error)
        }
    }
    var backgroundDisconnectJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose { backgroundDisconnectJob?.cancel(); ioClient.disconnect() } }
    var sharedConfigLoaded by remember { mutableStateOf(false) }
    var sharedConfigRevision by remember { mutableStateOf(0L) }
    var userProfileLoaded by remember { mutableStateOf(false) }
    var userProfileRevision by remember { mutableStateOf(0L) }
    fun saveUserProfile() {
        if (!userProfileLoaded || !connected) return
        val username = (homePilotAccount?.username ?: credentialStore.username()).trim()
        if (username.isBlank()) return
        val nextRevision = userProfileRevision + 1L
        val profile = HomePilotUserProfile(
            revision = nextRevision,
            updatedAt = System.currentTimeMillis(),
            favoriteIds = favoriteIds,
            favoriteOrder = favoriteOrder,
            disconnectTimeoutMinutes = inactivityMinutes
        )
        val profileId = "0_userdata.0.HomePilot.profiles.${homePilotProfileIdSegment(username)}"
        ioClient.getJsonState(profileId) { remote, readError ->
            if (readError != null || remote == null) {
                connectionStatus = "ioBroker verbunden · Profiländerung nicht gespeichert (Remote-Prüfung fehlgeschlagen): $readError"
                return@getJsonState
            }
            val remoteProfile = HomePilotUserProfile.fromJson(remote)
            if (remoteProfile == null || remoteProfile.revision != userProfileRevision) {
                connectionStatus = "HomePilot: Profilkonflikt erkannt – Remote-Profil wurde zwischenzeitlich geändert. Änderung nicht übertragen."
                return@getJsonState
            }
            ioClient.setJsonState(profileId, profile.toJson()) { success, error ->
                if (success) userProfileRevision = maxOf(userProfileRevision, nextRevision)
                else connectionStatus = "ioBroker verbunden · persönliches HomePilot-Profil konnte nicht gespeichert werden: $error"
            }
        }
    }
    fun saveSharedConfiguration() {
        if (!sharedConfigLoaded || !connected) return
        val nextRevision = sharedConfigRevision + 1L
        val config = HomePilotSharedConfig(
            revision = nextRevision,
            updatedAt = System.currentTimeMillis(),
            rooms = rooms.filter(String::isNotBlank).distinct().ifEmpty { HomePilotSharedConfig.DEFAULT_ROOMS },
            deviceNames = customNames,
            deviceRooms = deviceRooms,
            deviceKinds = deviceKinds,
            hiddenDeviceItems = hiddenItems.mapNotNull { it.stateId }.toSet()
        )
        ioClient.getJsonState("0_userdata.0.HomePilot.config") { remote, readError ->
            if (readError != null || remote == null) {
                connectionStatus = "ioBroker verbunden · Konfigurationsänderung nicht gespeichert (Remote-Prüfung fehlgeschlagen): $readError"
                return@getJsonState
            }
            val remoteConfig = HomePilotSharedConfig.fromJson(remote)
            if (remoteConfig == null || remoteConfig.revision != sharedConfigRevision) {
                connectionStatus = "HomePilot: Konflikt erkannt – Remote-Konfiguration wurde zwischenzeitlich geändert. Lokale Änderung nicht übertragen; bitte erneut verbinden/laden."
                return@getJsonState
            }
            ioClient.setJsonState("0_userdata.0.HomePilot.config", config.toJson()) { success, error ->
                if (success) sharedConfigRevision = maxOf(sharedConfigRevision, nextRevision)
                else connectionStatus = "ioBroker verbunden · HomePilot-Konfiguration konnte nicht gespeichert werden: $error"
            }
        }
    }
    fun persistRooms() { prefs.edit().putString("rooms", rooms.joinToString("|")).apply(); saveSharedConfiguration() }
    fun persistNames() { prefs.edit().putString("deviceNames", JSONObject(customNames as Map<*, *>).toString()).apply(); saveSharedConfiguration() }
    fun persistDeviceRooms() { prefs.edit().putString("deviceRooms", JSONObject(deviceRooms as Map<*, *>).toString()).apply(); saveSharedConfiguration() }
    fun persistDeviceKinds() { prefs.edit().putString("deviceKinds", JSONObject(deviceKinds as Map<*, *>).toString()).apply(); saveSharedConfiguration() }
    fun persistHiddenItems() { prefs.edit().putString("hiddenDeviceItems", writeHiddenItems(hiddenItems)).apply(); saveSharedConfiguration() }


    /** First-connect migration: only seed a document when its ioBroker object is absent. */
    fun initializeHomePilotSync() {
        val localShared = HomePilotSharedConfig.initialFromLocal(
            rooms = rooms,
            deviceNames = customNames,
            deviceRooms = deviceRooms,
            deviceKinds = deviceKinds,
            hiddenDeviceItems = hiddenItems.mapNotNull { it.stateId }.toSet()
        )
        ioClient.ensureJsonState(
            "0_userdata.0.HomePilot.config",
            "HomePilot shared configuration",
            localShared.toJson()
        ) { sharedJson, created, sharedError ->
            if (sharedError != null || sharedJson == null) {
                connectionStatus = "ioBroker verbunden · HomePilot-Konfiguration nicht synchronisiert: $sharedError"
                return@ensureJsonState
            }
            val shared = HomePilotSharedConfig.fromJson(sharedJson)
            if (shared == null) {
                connectionStatus = "ioBroker verbunden · Remote-Konfiguration hat ein unbekanntes/ungültiges Format; lokale Daten bleiben unverändert."
                return@ensureJsonState
            }
            sharedConfigRevision = shared.revision
            rooms = shared.rooms.toMutableList()
            customNames = shared.deviceNames
            deviceRooms = shared.deviceRooms
            deviceKinds = shared.deviceKinds
            hiddenIds = shared.hiddenDeviceItems
            prefs.edit()
                .putStringSet("hiddenDevices", hiddenIds)
                .putString("rooms", rooms.joinToString("|"))
                .putString("deviceNames", JSONObject(customNames as Map<*, *>).toString())
                .putString("deviceRooms", JSONObject(deviceRooms as Map<*, *>).toString())
                .putString("deviceKinds", JSONObject(deviceKinds as Map<*, *>).toString())
                .apply()
            applyCustomConfig()
            sharedConfigLoaded = true

            val username = credentialStore.username().trim()
            if (username.isBlank()) {
                connectionStatus = "ioBroker verbunden · gemeinsame Konfiguration geladen; persönliches Profil benötigt einen ioBroker-Benutzer."
                return@ensureJsonState
            }
            val localProfile = HomePilotUserProfile(
                revision = 1L,
                updatedAt = System.currentTimeMillis(),
                favoriteIds = favoriteIds,
                favoriteOrder = favoriteOrder,
                disconnectTimeoutMinutes = inactivityMinutes
            )
            val profileId = "0_userdata.0.HomePilot.profiles.${homePilotProfileIdSegment(username)}"
            ioClient.ensureJsonState(profileId, "HomePilot profile: $username", localProfile.toJson()) { profileJson, _, profileError ->
                if (profileError != null || profileJson == null) {
                    connectionStatus = "ioBroker verbunden · gemeinsame Konfiguration geladen; Profil-Sync fehlgeschlagen: $profileError"
                    return@ensureJsonState
                }
                val profile = HomePilotUserProfile.fromJson(profileJson)
                if (profile == null) {
                    connectionStatus = "ioBroker verbunden · Profilformat ungültig; lokale Profileinstellungen bleiben unverändert."
                    return@ensureJsonState
                }
                userProfileRevision = profile.revision
                favoriteIds = profile.favoriteIds
                favoriteOrder = profile.favoriteOrder
                inactivityMinutes = profile.disconnectTimeoutMinutes
                prefs.edit()
                    .putStringSet("favoriteDevices", profile.favoriteIds)
                    .putString("favoriteOrder", profile.favoriteOrder.joinToString("|"))
                    .putInt("disconnectAfterMinutes", profile.disconnectTimeoutMinutes)
                    .apply()
                userProfileLoaded = true
                connectionStatus = if (created) {
                    "ioBroker verbunden · HomePilot-Konfiguration und Profil erstmalig migriert."
                } else {
                    "ioBroker verbunden · gemeinsame Konfiguration und Benutzerprofil geladen."
                }
            }
        }
    }

    fun connectToIoBroker(manualDeviceSearch: Boolean = false) {
        prefs.edit().putString("server", serverUrl).apply()
        if (connecting) return
        connecting = true
        connectionStatus = "Verbindung wird aufgebaut …"
        ioClient.connect(serverUrl) { ok, msg ->
            connecting = false
            connected = ok
            if (ok) loginAccepted = true
            if (ok) loadHomePilotAccounts()
            connectionStatus = if (ok) "WebSocket-Verbindung zu ioBroker hergestellt" else "Verbindung fehlgeschlagen: $msg"
            showConnectionAlert = !ok && !msg.contains("unterbrochen", ignoreCase = true) && !msg.contains("Wiederverbindung läuft", ignoreCase = true)
            if (ok) {
                initializeHomePilotSync()
                ioClient.inspectAdapterStates(serverUrl, "dreame.0") { movaIds, movaError ->
                    val expectedMovaIds = listOf(
                        "dreame.0.-112285430.status.battery-level",
                        "dreame.0.-112285430.status.status",
                        "dreame.0.-112285430.status.blade-health",
                        "dreame.0.-112285430.status.robot-maintenance-health",
                        "dreame.0.-112285430.remote.mow-zone",
                        "dreame.0.-112285430.remote.pause-mow",
                        "dreame.0.-112285430.remote.stop-mow",
                        "dreame.0.-112285430.remote.start-charge"
                    )
                    if (movaError != null) {
                        connectionStatus = "$connectionStatus · MOVA-Prüfung fehlgeschlagen: $movaError"
                    }
                }
                ioClient.discoverSwitches(serverUrl, "shelly.0") { found, err ->
                    if (err == null) {
                        devices.clear()
                        devices.addAll(found.filter { it.stateId !in hiddenIds })
                        val waterId = "shelly.0.SHPLG-S#6EDB6C#1.Relay0.Switch"
                        val washId = "shelly.0.SHPLG-S#376CC3#1.Relay0.Switch"
                        if (waterId !in hiddenIds && devices.none { it.stateId == waterId }) devices.add(0, DeviceItem("Wasser", "Katzenbrunnen · Wohnzimmer", "plug", false, "", waterId, "shelly.0.SHPLG-S#6EDB6C#1.Relay0.Power", true, room = "Wohnzimmer"))
                        else if (waterId !in hiddenIds) {
                            val wi = devices.indexOfFirst { it.stateId == waterId }
                            if (wi >= 0) devices[wi] = devices[wi].copy(name = "Wasser", subtitle = "Katzenbrunnen · Wohnzimmer", powerId = "shelly.0.SHPLG-S#6EDB6C#1.Relay0.Power", room = "Wohnzimmer")
                        }
                        if (washId !in hiddenIds && devices.none { it.stateId == washId }) devices.add(DeviceItem("Waschmaschine", "Shelly Plug", "plug", false, "", washId, "shelly.0.SHPLG-S#376CC3#1.Relay0.Power", true, room = "Hauswirtschaft"))
                        else if (washId !in hiddenIds) {
                            val wi = devices.indexOfFirst { it.stateId == washId }
                            if (wi >= 0) devices[wi] = devices[wi].copy(name = "Waschmaschine", subtitle = "Shelly Plug", writable = true, powerId = "shelly.0.SHPLG-S#376CC3#1.Relay0.Power", room = "Hauswirtschaft")
                        }
                        applyCustomConfig()
                        refreshStates(ioClient, serverUrl, devices, lights)
                    } else connectionStatus = "$connectionStatus · Shelly-Objektabfrage fehlgeschlagen: $err"
                }
                ioClient.discoverSwitches(serverUrl, "tuya") { tuyaFound, tuyaErr ->
                    if (manualDeviceSearch) {
                        if (tuyaErr == null) {
                            val existingIds = devices.mapNotNull { it.stateId }.toSet() + hiddenIds
                            val candidates = tuyaFound.filter { it.stateId !in existingIds }
                            discoveredTuyaDevices.clear()
                            discoveredTuyaDevices.addAll(candidates)
                            selectedTuyaIds = emptySet()
                            selectedTuyaRooms = candidates.associate { it.stateId.orEmpty() to (deviceRooms[it.stateId.orEmpty()] ?: "Nicht zugeordnet") }
                            discoveredNames = discoveredNames + candidates.associate { it.stateId.orEmpty() to (customNames[it.stateId.orEmpty()] ?: it.name) }
                            showTuyaImportDialog = candidates.isNotEmpty()
                            if (candidates.isEmpty() && tuyaFound.isEmpty()) {
                                scanResultMessage = "Keine Tuya-Schalter gefunden. Prüfe, ob ein Tuya-Adapter (tuya.0, tuya.1 usw.) aktiv ist und schaltbare Datenpunkte bereitstellt."
                            } else if (candidates.isEmpty()) {
                                scanResultMessage = "Keine neuen Tuya-Geräte gefunden. Die bereits eingebundenen oder ausgeblendeten Geräte werden nicht erneut angeboten."
                            }
                        } else {
                            scanResultMessage = "Tuya-Suche fehlgeschlagen: $tuyaErr"
                        }
                    }
                }
                ioClient.discoverSwitches(serverUrl, "hue.0") { found, err ->
                    if (err == null) {
                        val candidates = found.filter { it.stateId !in hiddenIds && it.stateId !in importedLightIds }
                        if (manualDeviceSearch) {
                            discoveredLights.clear()
                            discoveredLights.addAll(candidates)
                            selectedLightIds = emptySet()
                            discoveredNames = discoveredNames + candidates.associate { it.stateId.orEmpty() to (customNames[it.stateId.orEmpty()] ?: it.name) }
                            showLightImportDialog = candidates.isNotEmpty()
                            if (candidates.isEmpty()) {
                                scanResultMessage = if (found.isEmpty()) {
                                    "Keine Hue-Lampen erkannt. Prüfe, ob der ioBroker-Adapter hue.0 aktiv ist und Lampen über ihn bereitstellt."
                                } else {
                                    "Keine neuen Hue-Lampen gefunden. Bereits ausgewählte oder ausgeblendete Lampen werden nicht erneut angeboten."
                                }
                            }
                        }
                        // Restore previously selected lights, but never auto-import new ones.
                        lights.clear()
                        lights.addAll(found.filter { it.stateId in importedLightIds && it.stateId !in hiddenIds })
                        applyCustomConfig()
                        refreshStates(ioClient, serverUrl, devices, lights)
                    } else {
                        connectionStatus = "Hue-Suche fehlgeschlagen: $err"
                        if (manualDeviceSearch) scanResultMessage = "Die Hue-Suche ist fehlgeschlagen. Prüfe die WebSocket-Verbindung und den Adapter hue.0. Details: $err"
                    }
                }
            }
        }
    }

    val hostActivity = context as? ComponentActivity
    DisposableEffect(hostActivity, inactivityMinutes, serverUrl) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    backgroundDisconnectJob?.cancel()
                    if (inactivityMinutes > 0) backgroundDisconnectJob = appScope.launch {
                        delay(inactivityMinutes * 60_000L)
                        ioClient.disconnect()
                        connected = false
                        connecting = false
                        connectionStatus = "Verbindung nach $inactivityMinutes Minuten Hintergrundzeit getrennt"
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    backgroundDisconnectJob?.cancel(); backgroundDisconnectJob = null
                    if (!connected && !connecting && credentialStore.hasCredentials()) connectToIoBroker()
                }
                else -> Unit
            }
        }
        hostActivity?.lifecycle?.addObserver(observer)
        onDispose { hostActivity?.lifecycle?.removeObserver(observer); backgroundDisconnectJob?.cancel() }
    }
    LaunchedEffect(connected, serverUrl) {
        while (connected) {
            delay(8000)
            refreshStates(ioClient, serverUrl, devices, lights)
            ioClient.getState(serverUrl, "shelly.0.SHWT-1#AC5FD3#1.sensor.flood") { alarm, _ ->
                if (alarm != null) {
                    val newlyDetected = alarm && !floodAlarm
                    floodKnown = true
                    floodAlarm = alarm
                    if (newlyDetected) postWaterAlarmNotification(context)
                }
            }
            ioClient.getNumericState(serverUrl, "shelly.0.SHWT-1#AC5FD3#1.sensor.battery") { value ->
                if (value != null) { floodBattery = value.toInt().coerceIn(0, 100); floodBatteryKnown = true }
            }
            ioClient.getNumericState(serverUrl, "shelly.0.SHWT-1#AC5FD3#1.sensor.temperatureC") { value ->
                if (value != null) floodTemperature = value
            }
        }
    }

    val bg = if (dark) DarkBg else LightBg
    val panel = if (dark) Color(0xFF1B2734) else Color.White
    val text = if (dark) DarkText else LightText
    val scheme = if (dark) darkColorScheme(primary = Color(0xFF78B9F3), secondary = Color(0xFF67D4A5), background = bg, surface = panel, onSurface = text, onBackground = text, surfaceVariant = Color(0xFF293747), onSurfaceVariant = Color(0xFFB2C1D0)) else lightColorScheme(primary = Blue, secondary = Green, background = bg, surface = panel, onSurface = text, onBackground = text, surfaceVariant = Color(0xFFE6EDF5), onSurfaceVariant = Muted, outline = Color(0xFFCBD5E1))

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            if (!loginAccepted) {
                HomePilotLoginScreen(
                    status = connectionStatus, loading = connecting,
                    onLogin = {
                        // This ioBroker instance uses an unauthenticated WebSocket.
                        credentialStore.clear()
                        ioClient.disconnect(); connected = false; connecting = false; loginAccepted = false
                        connectToIoBroker()
                    }
                )
            } else if (!homePilotAuthenticated) {
                HomePilotAccountLoginScreen(
                    registry = homePilotRegistry,
                    username = homePilotUsername, onUsername = { homePilotUsername = it },
                    password = homePilotPassword, onPassword = { homePilotPassword = it },
                    passwordConfirmation = homePilotPasswordConfirmation, onPasswordConfirmation = { homePilotPasswordConfirmation = it },
                    displayName = homePilotDisplayName, onDisplayName = { homePilotDisplayName = it },
                    loading = homePilotAccountLoading, status = connectionStatus,
                    onReload = { if (connected) loadHomePilotAccounts() },
                    onLogin = {
                        val registry = homePilotRegistry
                        if (registry == null) connectionStatus = "Kontenliste noch nicht verfügbar. Bitte erneut laden."
                        else if (registry.accounts.isEmpty()) {
                            try {
                                val next = registry.createInitialAdmin(homePilotUsername, homePilotDisplayName, homePilotPassword.toCharArray())
                                saveHomePilotRegistry(registry.revision, next) { saved, error ->
                                    if (saved) { homePilotAccount = next.accounts.first(); homePilotAuthenticated = true; homePilotPassword = ""; homePilotPasswordConfirmation = ""; initializeHomePilotSync() }
                                    else connectionStatus = "Admin konnte nicht angelegt werden: " + error
                                }
                            } catch (e: Exception) { connectionStatus = e.message ?: "Admin-Einrichtung fehlgeschlagen." }
                        } else {
                            val account = registry.authenticate(homePilotUsername, homePilotPassword.toCharArray())
                            if (account == null) connectionStatus = "HomePilot-Benutzername oder Passwort falsch."
                            else { homePilotAccount = account; homePilotAuthenticated = true; homePilotPassword = ""; connectionStatus = "HomePilot angemeldet: " + account.displayName; initializeHomePilotSync() }
                        }
                    }
                )
            } else Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().statusBarsPadding()
                        .pointerInput(selectedTab, selectedRoom) {
                            var horizontalDrag = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, dragAmount ->
                                    horizontalDrag += dragAmount
                                    change.consume()
                                },
                                onDragEnd = {
                                    val mainTabs = listOf(Tab.HOME, Tab.FAVORITES, Tab.ENERGY)
                                    val currentIndex = mainTabs.indexOf(selectedTab)
                                    if (selectedRoom == null && currentIndex >= 0 && kotlin.math.abs(horizontalDrag) > 80f) {
                                        val targetIndex = (currentIndex + if (horizontalDrag < 0) 1 else -1)
                                            .coerceIn(0, mainTabs.lastIndex)
                                        if (targetIndex != currentIndex) navigateTo(mainTabs[targetIndex])
                                    }
                                },
                                onDragCancel = { horizontalDrag = 0f }
                            )
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Header(dark = dark, text = text, connected = connected, status = connectionStatus)
                    if (showDemoNotice) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(if (dark) Color(0xFF47391C) else Color(0xFFFFF1D2))
                                .clickable { showDemoNotice = false }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Icon(Icons.Default.Info, null, tint = Color(0xFFB7791F), modifier = Modifier.size(19.dp))
                            Column(Modifier.weight(1f)) {
                                Text("VORSCHAU-MODUS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (dark) Color(0xFFFFD78A) else Color(0xFF80520A))
                                Text("Beispieldaten · Tippen zum Ausblenden", fontSize = 12.sp, color = if (dark) Color(0xFFFFE7B0) else Color(0xFF80520A))
                            }
                            Icon(Icons.Default.Close, null, tint = Color(0xFFB7791F), modifier = Modifier.size(18.dp))
                        }
                    }
                    Column(
                        Modifier.fillMaxWidth().graphicsLayer { alpha = pageAlpha.value },
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                    key(displayedTab) {
                    when (displayedTab) {
                        Tab.HOME -> HomeScreen(rooms, lights, devices, floodAlarm, floodBattery, floodKnown, floodBatteryKnown, panel, text, selectedRoom, searchQuery, weatherCity, serverUrl, connected, ioClient, onOpenMova = { selectedTab = Tab.MOVA }, onOpenRoidmi = { selectedTab = Tab.ROIDMI }, onSearch = { searchQuery = it }, onSelectRoom = { selectedRoom = it.takeIf { room -> room.isNotBlank() } }, onOpenRoom = { room -> selectedRoom = room }, onTab = { navigateTo(it) }, onSetNumeric = { item, id, value -> ioClient.setNumericState(serverUrl, id, value) { ok, message -> connectionStatus = message } }, onToggle = { item, value ->
                            val id = item.stateId
                            if (id != null && item.writable) ioClient.setState(serverUrl, id, value) { ok, message ->
                                connectionStatus = message
                                if (ok) ioClient.getState(serverUrl, id) { state, err ->
                                    if (state != null) updateDeviceState(devices, lights, id, state)
                                    if (err != null) connectionStatus = err
                                }
                            }
                        })
                        Tab.MOVA -> MovaDetailScreen(serverUrl, connected, ioClient, panel, text) { selectedTab = Tab.HOME }
                        Tab.ROIDMI -> RoidmiDetailScreen(serverUrl, connected, ioClient, panel, text) { selectedTab = Tab.HOME }
                        Tab.LIGHTS -> LightsScreen(lights, rooms, panel, text, connected, selectedRoom, onSelectRoom = { selectedRoom = it },
                            onEditDevice = { item -> editDevice = item; editName = item.name; editRoom = item.room; editKind = deviceKinds[item.stateId] ?: item.kind },
                            onSetNumeric = { item, id, value -> ioClient.setNumericState(serverUrl, id, value) { ok, message -> connectionStatus = message } },
                            onSetDefaultBrightness = { item, percent -> prefs.edit().putInt("lightOnBrightness:" + (item.stateId ?: item.name), percent).apply() },
                            defaultBrightness = { item -> prefs.getInt("lightOnBrightness:" + (item.stateId ?: item.name), 100) },
                            onToggle = { item, value ->
                            val id = item.stateId
                            if (id != null && item.writable) {
                                if (value && item.brightnessId != null) {
                                    // Switch the lamp on first. Many Hue lights reset brightness
                                    // during power-on, so applying brightness before this command
                                    // can be overwritten by the lamp itself.
                                    ioClient.setState(serverUrl, id, true) { ok, message ->
                                        connectionStatus = message
                                        if (ok) {
                                            val percent = prefs.getInt("lightOnBrightness:" + (item.stateId ?: item.name), 100)
                                            Handler(Looper.getMainLooper()).postDelayed({
                                                ioClient.setNumericState(serverUrl, item.brightnessId, percent * 2.54) { brightnessOk, brightnessMessage ->
                                                    connectionStatus = brightnessMessage
                                                    if (brightnessOk) {
                                                        ioClient.getState(serverUrl, id) { state, err ->
                                                            if (state != null) updateDeviceState(devices, lights, id, state)
                                                            if (err != null) connectionStatus = err
                                                        }
                                                    }
                                                }
                                            }, 400)
                                        }
                                    }
                                } else {
                                    ioClient.setState(serverUrl, id, value) { ok, message ->
                                        connectionStatus = message
                                        if (ok) ioClient.getState(serverUrl, id) { state, err -> if (state != null) updateDeviceState(devices, lights, id, state); if (err != null) connectionStatus = err }
                                    }
                                }
                            }
                        })
                        Tab.DEVICES -> DevicesScreen(devices, rooms, panel, text, connected, selectedRoom, onSelectRoom = { selectedRoom = it },
                            onEditDevice = { item -> editDevice = item; editName = item.name; editRoom = item.room; editKind = deviceKinds[item.stateId] ?: item.kind },
                            onToggle = { item, value ->
                                val id = item.stateId
                                if (id != null && item.writable) ioClient.setState(serverUrl, id, value) { ok, message ->
                                    connectionStatus = message
                                    if (ok) ioClient.getState(serverUrl, id) { state, err ->
                                        if (state != null) updateDeviceState(devices, lights, id, state)
                                        if (err != null) connectionStatus = err
                                    }
                                }
                            })
                        Tab.SAFETY -> SafetyScreen(floodAlarm, floodBattery, floodBatteryKnown, panel, text, floodKnown, floodTemperature)
                        Tab.FAVORITES -> FavoritesScreen(devices + lights, favoriteIds, favoriteOrder, panel, text,
                            onToggle = { item ->
                                item.stateId?.let { id ->
                                    val adding = id !in favoriteIds
                                    favoriteIds = if (adding) favoriteIds + id else favoriteIds - id
                                    favoriteOrder = if (adding) (favoriteOrder.filter { it != id } + id) else favoriteOrder.filter { it != id }
                                    prefs.edit().putStringSet("favoriteDevices", favoriteIds).putString("favoriteOrder", favoriteOrder.joinToString("|")).apply(); saveUserProfile()
                                }
                            },
                            onMove = { id, delta ->
                                val current = favoriteOrder.filter { it in favoriteIds } + (favoriteIds - favoriteOrder.toSet()).sorted()
                                val index = current.indexOf(id)
                                val target = (index + delta).coerceIn(0, current.lastIndex)
                                if (index >= 0 && target != index) {
                                    val mutable = current.toMutableList()
                                    val moved = mutable.removeAt(index)
                                    mutable.add(target, moved)
                                    favoriteOrder = mutable
                                    prefs.edit().putString("favoriteOrder", favoriteOrder.joinToString("|")).apply(); saveUserProfile()
                                }
                            },
                            onSwitch = { item, value ->
                                item.stateId?.takeIf { item.writable }?.let { id ->
                                    ioClient.setState(serverUrl, id, value) { ok, message ->
                                        connectionStatus = message
                                        if (ok) ioClient.getState(serverUrl, id) { state, _ -> if (state != null) updateDeviceState(devices, lights, id, state) }
                                    }
                                }
                            },
                            onEdit = { item -> editDevice = item; editName = item.name; editRoom = item.room; editKind = deviceKinds[item.stateId] ?: item.kind })
                        Tab.ENERGY -> EnergyScreen(devices + lights, energyPrice, onPrice = { energyPrice = it; prefs.edit().putString("energyPrice", it).apply() }, panel, text)
                        Tab.DEVICE_HEALTH -> DeviceHealthScreen(devices + lights, connected, connectionStatus, floodBattery, floodBatteryKnown, panel, text)
                        Tab.MORE -> SettingsScreen(dark, onDark = { dark = it }, panel, text, onBack = { navigateTo(Tab.HOME) }, serverUrl = serverUrl, onServerUrl = { serverUrl = it }, weatherCity = weatherCity, onWeatherCity = { weatherCity = it; prefs.edit().putString("weatherCity", it).apply() }, status = connectionStatus, connected = connected, onConnect = { connectToIoBroker(manualDeviceSearch = false) }, onSearchDevices = { if (connected) connectToIoBroker(manualDeviceSearch = true) else { connectionStatus = "Bitte zuerst die ioBroker-Verbindung herstellen."; showConnectionAlert = true } }, onOpenLights = { navigateTo(Tab.LIGHTS) }, onOpenDevices = { navigateTo(Tab.DEVICES) }, onOpenRooms = { navigateTo(Tab.ROOMS) }, onOpenHealth = { navigateTo(Tab.DEVICE_HEALTH) }, onShowHidden = { showHiddenDevices = true }, inactivityMinutes = inactivityMinutes, onInactivityMinutes = { inactivityMinutes = it; prefs.edit().putInt("disconnectAfterMinutes", it).apply(); saveUserProfile() }, onBatterySettings = { openBatterySettings(context) }, ioBrokerUsername = ioBrokerUsername, onIoBrokerUsername = { ioBrokerUsername = it }, ioBrokerPassword = ioBrokerPassword, onIoBrokerPassword = { ioBrokerPassword = it }, onSaveCredentials = { if (ioBrokerUsername.isNotBlank() && ioBrokerPassword.isNotBlank()) { credentialStore.save(ioBrokerUsername, ioBrokerPassword); ioClient.disconnect(); connected = false; connecting = false; loginAccepted = false; connectToIoBroker() } }, onSwitchUser = { ioClient.disconnect(); credentialStore.clear(); ioBrokerUsername = ""; ioBrokerPassword = ""; loginAccepted = false; connected = false; connecting = false; userProfileLoaded = false; userProfileRevision = 0L; favoriteIds = emptySet(); favoriteOrder = emptyList(); inactivityMinutes = 30; prefs.edit().putStringSet("favoriteDevices", emptySet()).putString("favoriteOrder", "").putInt("disconnectAfterMinutes", 30).apply(); connectionStatus = "Bitte mit einem anderen ioBroker-Benutzer anmelden." }, homePilotAccount = homePilotAccount, homePilotAccounts = homePilotRegistry?.accounts ?: emptyList(), onHomePilotSwitch = { homePilotAuthenticated = false; homePilotAccount = null; homePilotPassword = ""; homePilotUsername = ""; connectionStatus = "Bitte mit deinem HomePilot-Konto anmelden." }, onAddHomePilotAccount = { uname, display, pwd, role -> val reg = homePilotRegistry; val actor = homePilotAccount; if (reg != null && actor != null) try { val next = reg.addAccount(actor, uname, display, pwd.toCharArray(), role); saveHomePilotRegistry(reg.revision, next) { ok, err -> connectionStatus = if (ok) "HomePilot-Benutzer angelegt." else "Fehler: $err" } } catch (e: Exception) { connectionStatus = e.message ?: "Benutzer konnte nicht angelegt werden." } }, onResetHomePilotPassword = { uname, pwd -> val reg = homePilotRegistry; val actor = homePilotAccount; if (reg != null && actor != null) try { val next = reg.resetPassword(actor, uname, pwd.toCharArray()); saveHomePilotRegistry(reg.revision, next) { ok, err -> connectionStatus = if (ok) "Passwort zurückgesetzt." else "Fehler: $err" } } catch (e: Exception) { connectionStatus = e.message ?: "Passwort konnte nicht geändert werden." } }, onDeleteHomePilotAccount = { uname -> val reg = homePilotRegistry; val actor = homePilotAccount; if (reg != null && actor != null) try { val next = reg.deleteAccount(actor, uname); saveHomePilotRegistry(reg.revision, next) { ok, err -> connectionStatus = if (ok) "Benutzer gelöscht." else "Fehler: $err" } } catch (e: Exception) { connectionStatus = e.message ?: "Benutzer konnte nicht gelöscht werden." } })
                        Tab.ROOMS -> RoomsScreen(rooms, devices, lights, panel, text, connected = connected,
                            onToggle = { item, value ->
                                val id = item.stateId
                                if (id != null && item.writable) {
                                    if (value && item.kind == "light" && item.brightnessId != null) {
                                        ioClient.setState(serverUrl, id, true) { ok, message ->
                                            connectionStatus = message
                                            if (ok) {
                                                val percent = prefs.getInt("lightOnBrightness:" + (item.stateId ?: item.name), 100)
                                                Handler(Looper.getMainLooper()).postDelayed({
                                                    ioClient.setNumericState(serverUrl, item.brightnessId, percent * 2.54) { brightnessOk, brightnessMessage ->
                                                        connectionStatus = brightnessMessage
                                                        if (brightnessOk) ioClient.getState(serverUrl, id) { state, err -> if (state != null) updateDeviceState(devices, lights, id, state); if (err != null) connectionStatus = err }
                                                    }
                                                }, 400)
                                            }
                                        }
                                    } else ioClient.setState(serverUrl, id, value) { ok, message ->
                                        connectionStatus = message
                                        if (ok) ioClient.getState(serverUrl, id) { state, err -> if (state != null) updateDeviceState(devices, lights, id, state); if (err != null) connectionStatus = err }
                                    }
                                }
                            },
                            onSetNumeric = { item, id, value -> ioClient.setNumericState(serverUrl, id, value) { _, message -> connectionStatus = message } },
                            onSetDefaultBrightness = { item, percent -> prefs.edit().putInt("lightOnBrightness:" + (item.stateId ?: item.name), percent).apply() },
                            defaultBrightness = { item -> prefs.getInt("lightOnBrightness:" + (item.stateId ?: item.name), 100) },
                            onSelectRoom = { selectedRoom = it }, onAddRoom = { roomNameInput = ""; showRoomDialog = true },
                            onRenameRoom = { old, new -> if (new.isNotBlank() && new !in rooms) { rooms = rooms.map { if (it == old) new.trim() else it }.toMutableList(); persistRooms(); devices.indices.forEach { i -> if (devices[i].room == old) devices[i] = devices[i].copy(room = new.trim()) }; lights.indices.forEach { i -> if (lights[i].room == old) lights[i] = lights[i].copy(room = new.trim()) } } },
                            selectedRoom = selectedRoom, onEditDevice = { item -> editDevice = item; editName = item.name; editRoom = item.room; editKind = deviceKinds[item.stateId] ?: item.kind },
                            hiddenItems = hiddenItems,
                            onShowHidden = { showHiddenDevices = true },
                            onRequestDeleteRoom = { room -> confirmDeleteRoom = room })
                        Tab.SETTINGS -> SettingsScreen(dark, onDark = { dark = it }, panel, text, onBack = { navigateTo(Tab.HOME) },
                            serverUrl = serverUrl, onServerUrl = { serverUrl = it },
                            weatherCity = weatherCity, onWeatherCity = { weatherCity = it; prefs.edit().putString("weatherCity", it).apply() },
                            status = connectionStatus, connected = connected,
                            onConnect = { connectToIoBroker(manualDeviceSearch = false) },
                            onSearchDevices = { if (connected) connectToIoBroker(manualDeviceSearch = true) else { connectionStatus = "Bitte zuerst die ioBroker-Verbindung herstellen."; showConnectionAlert = true } },
                            onOpenLights = { navigateTo(Tab.LIGHTS) }, onOpenDevices = { navigateTo(Tab.DEVICES) }, onOpenRooms = { navigateTo(Tab.ROOMS) }, onOpenHealth = { navigateTo(Tab.DEVICE_HEALTH) }, onShowHidden = { showHiddenDevices = true }, inactivityMinutes = inactivityMinutes, onInactivityMinutes = { inactivityMinutes = it; prefs.edit().putInt("disconnectAfterMinutes", it).apply() }, onBatterySettings = { openBatterySettings(context) }, ioBrokerUsername = ioBrokerUsername, onIoBrokerUsername = { ioBrokerUsername = it }, ioBrokerPassword = ioBrokerPassword, onIoBrokerPassword = { ioBrokerPassword = it }, onSaveCredentials = { if (ioBrokerUsername.isNotBlank() && ioBrokerPassword.isNotBlank()) { credentialStore.save(ioBrokerUsername, ioBrokerPassword); ioClient.disconnect(); connected = false; connecting = false; loginAccepted = false; connectToIoBroker() } }, onSwitchUser = { ioClient.disconnect(); credentialStore.clear(); ioBrokerUsername = ""; ioBrokerPassword = ""; loginAccepted = false; connected = false; connecting = false; userProfileLoaded = false; userProfileRevision = 0L; favoriteIds = emptySet(); favoriteOrder = emptyList(); inactivityMinutes = 30; prefs.edit().putStringSet("favoriteDevices", emptySet()).putString("favoriteOrder", "").putInt("disconnectAfterMinutes", 30).apply(); connectionStatus = "Bitte mit einem anderen ioBroker-Benutzer anmelden." }, homePilotAccount = homePilotAccount, homePilotAccounts = homePilotRegistry?.accounts ?: emptyList(), onHomePilotSwitch = { homePilotAuthenticated = false; homePilotAccount = null; homePilotPassword = ""; homePilotUsername = ""; connectionStatus = "Bitte mit deinem HomePilot-Konto anmelden." }, onAddHomePilotAccount = { uname, display, pwd, role -> val reg = homePilotRegistry; val actor = homePilotAccount; if (reg != null && actor != null) try { val next = reg.addAccount(actor, uname, display, pwd.toCharArray(), role); saveHomePilotRegistry(reg.revision, next) { ok, err -> connectionStatus = if (ok) "HomePilot-Benutzer angelegt." else "Fehler: $err" } } catch (e: Exception) { connectionStatus = e.message ?: "Benutzer konnte nicht angelegt werden." } }, onResetHomePilotPassword = { uname, pwd -> val reg = homePilotRegistry; val actor = homePilotAccount; if (reg != null && actor != null) try { val next = reg.resetPassword(actor, uname, pwd.toCharArray()); saveHomePilotRegistry(reg.revision, next) { ok, err -> connectionStatus = if (ok) "Passwort zurückgesetzt." else "Fehler: $err" } } catch (e: Exception) { connectionStatus = e.message ?: "Passwort konnte nicht geändert werden." } }, onDeleteHomePilotAccount = { uname -> val reg = homePilotRegistry; val actor = homePilotAccount; if (reg != null && actor != null) try { val next = reg.deleteAccount(actor, uname); saveHomePilotRegistry(reg.revision, next) { ok, err -> connectionStatus = if (ok) "Benutzer gelöscht." else "Fehler: $err" } } catch (e: Exception) { connectionStatus = e.message ?: "Benutzer konnte nicht gelöscht werden." } })
                    }
                    }
                    }
                    if (showBatteryOptimizationDialog) AlertDialog(
                        onDismissRequest = { showBatteryOptimizationDialog = false; prefs.edit().putBoolean("batteryHintShown", true).apply() },
                        title = { Text("HomePilot im Hintergrund erlauben") },
                        text = { Text("Damit die ioBroker-Verbindung beim kurzen Wechsel zu anderen Apps bestehen bleibt, sollte Android HomePilot nicht durch Akkuoptimierung oder App-Pausierung einschränken. Öffne die App-Einstellungen und stelle Akku auf „Uneingeschränkt“ bzw. deaktiviere „App-Aktivität bei Nichtbenutzung pausieren“.") },
                        confirmButton = { TextButton(onClick = { showBatteryOptimizationDialog = false; prefs.edit().putBoolean("batteryHintShown", true).apply(); openBatterySettings(context) }) { Text("Einstellungen öffnen") } },
                        dismissButton = { TextButton(onClick = { showBatteryOptimizationDialog = false; prefs.edit().putBoolean("batteryHintShown", true).apply() }) { Text("Später") } }
                    )
                    if (showConnectionAlert) AlertDialog(
                        onDismissRequest = { showConnectionAlert = false },
                        icon = { Icon(Icons.Default.WifiOff, null, tint = Color(0xFFD97706)) },
                        title = { Text("Keine Verbindung zu ioBroker") },
                        text = { Text("HomePilot konnte keine Verbindung herstellen. Bitte prüfe, ob ioBroker läuft, die Serveradresse in den Einstellungen stimmt und dein Smartphone eine Verbindung zum Heimnetz hat. Wenn du von unterwegs zugreifst, aktiviere WireGuard bitte selbst und tippe danach auf „Erneut versuchen“.\n\nDetails: $connectionStatus") },
                        confirmButton = {
                            TextButton(onClick = { showConnectionAlert = false; connectToIoBroker() }) { Text("Erneut versuchen") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConnectionAlert = false; navigateTo(Tab.SETTINGS) }) { Text("Einstellungen prüfen") }
                        }
                    )
                    if (showRoomDialog) AlertDialog(onDismissRequest = { showRoomDialog = false }, title = { Text("Raum hinzufügen") }, text = { OutlinedTextField(value = roomNameInput, onValueChange = { roomNameInput = it }, label = { Text("Raumname") }, singleLine = true, trailingIcon = { InputConfirmIcon() }) }, confirmButton = { TextButton(onClick = { val n = roomNameInput.trim(); if (n.isNotBlank() && n !in rooms) { rooms = (rooms.filterNot { it == "Nicht zugeordnet" } + n + "Nicht zugeordnet").toMutableList(); persistRooms() }; showRoomDialog = false }) { Text("Hinzufügen") } }, dismissButton = { TextButton(onClick = { showRoomDialog = false }) { Text("Abbrechen") } })
                    if (editDevice != null) AlertDialog(onDismissRequest = { editDevice = null }, title = { Row(verticalAlignment = Alignment.CenterVertically) { Text("Gerät bearbeiten", modifier = Modifier.weight(1f)); IconButton(onClick = { editDevice?.stateId?.let { id -> favoriteIds = if (id in favoriteIds) favoriteIds - id else favoriteIds + id; prefs.edit().putStringSet("favoriteDevices", favoriteIds).apply() } }) { Icon(Icons.Default.Star, "Favorit", tint = if (editDevice?.stateId in favoriteIds) Blue else Muted) } } }, text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Gerätename") }, singleLine = true, trailingIcon = { InputConfirmIcon() })
                Text("Gerätetyp")
                Box {
                    OutlinedButton(onClick = { editKindExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(editableDeviceKinds.firstOrNull { it.second == editKind }?.first ?: "Sonstiges", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = editKindExpanded, onDismissRequest = { editKindExpanded = false }, modifier = Modifier.heightIn(max = 260.dp)) {
                        editableDeviceKinds.forEach { (label, kind) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { editKind = kind; editKindExpanded = false })
                        }
                    }
                }
                Text("Raum")
                Box {
                    OutlinedButton(onClick = { editRoomExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(editRoom, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = editRoomExpanded, onDismissRequest = { editRoomExpanded = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                        rooms.forEach { room ->
                            DropdownMenuItem(text = { Text(room) }, onClick = { editRoom = room; editRoomExpanded = false })
                        }
                    }
                }
            } }, confirmButton = { TextButton(onClick = { val item = editDevice!!; val id = item.stateId; if (id != null) { customNames = customNames.toMutableMap().apply { put(id, editName.trim().ifBlank { item.name }) }; deviceRooms = deviceRooms.toMutableMap().apply { put(id, editRoom) }; deviceKinds = deviceKinds.toMutableMap().apply { put(id, editKind) }; persistNames(); persistDeviceRooms(); persistDeviceKinds(); val di = devices.indexOfFirst { it.stateId == id }; if (di >= 0) devices[di] = devices[di].copy(name = customNames[id] ?: item.name, room = editRoom, kind = editKind); val li = lights.indexOfFirst { it.stateId == id }; if (li >= 0) lights[li] = lights[li].copy(name = customNames[id] ?: item.name, room = editRoom, kind = editKind) }; editDevice = null }) { Text("Speichern") } }, dismissButton = { Row { TextButton(onClick = { confirmRemoveDevice = editDevice; editDevice = null }) { Text("Aus HomePilot entfernen") }; TextButton(onClick = { editDevice = null }) { Text("Abbrechen") } } })
                    if (confirmRemoveDevice != null) AlertDialog(
                        onDismissRequest = { confirmRemoveDevice = null },
                        title = { Text("Gerät entfernen?") },
                        text = { Text("${confirmRemoveDevice?.name} wird aus HomePilot ausgeblendet. In ioBroker bleibt das Gerät unverändert und kann später wieder eingeblendet werden.") },
                        confirmButton = { TextButton(onClick = {
                            val item = confirmRemoveDevice!!; val id = item.stateId
                            if (id != null) {
                                hiddenIds = hiddenIds + id
                                prefs.edit().putStringSet("hiddenDevices", hiddenIds).apply()
                                hiddenItems = (hiddenItems.filterNot { it.stateId == id } + item)
                                persistHiddenItems()
                                devices.removeAll { it.stateId == id }; lights.removeAll { it.stateId == id }
                            }
                            confirmRemoveDevice = null
                        }) { Text("Entfernen") } },
                        dismissButton = { TextButton(onClick = { confirmRemoveDevice = null }) { Text("Abbrechen") } }
                    )
                    if (confirmDeleteRoom != null) AlertDialog(
                        onDismissRequest = { confirmDeleteRoom = null },
                        title = { Text("Raum löschen?") },
                        text = { Text("Der Raum ${confirmDeleteRoom} wird gelöscht. Die Geräte bleiben erhalten und werden ‘Nicht zugeordnet’.") },
                        confirmButton = { TextButton(onClick = { val room = confirmDeleteRoom; if (room != null) {
                            rooms = (rooms.filterNot { it == room } + if ("Nicht zugeordnet" !in rooms) listOf("Nicht zugeordnet") else emptyList()).toMutableList(); persistRooms()
                            devices.indices.forEach { i -> if (devices[i].room == room) { devices[i] = devices[i].copy(room = "Nicht zugeordnet"); devices[i].stateId?.let { id -> deviceRooms = deviceRooms.toMutableMap().apply { put(id, "Nicht zugeordnet") } } } }
                            lights.indices.forEach { i -> if (lights[i].room == room) { lights[i] = lights[i].copy(room = "Nicht zugeordnet"); lights[i].stateId?.let { id -> deviceRooms = deviceRooms.toMutableMap().apply { put(id, "Nicht zugeordnet") } } } }; persistDeviceRooms()
                        }; confirmDeleteRoom = null }) { Text("Raum löschen") } },
                        dismissButton = { TextButton(onClick = { confirmDeleteRoom = null }) { Text("Abbrechen") } }
                    )
                    if (scanResultMessage != null) AlertDialog(
                        onDismissRequest = { scanResultMessage = null },
                        title = { Text("Gerätesuche") },
                        text = { Text(scanResultMessage ?: "") },
                        confirmButton = { TextButton(onClick = { scanResultMessage = null }) { Text("OK") } }
                    )
                    if (showTuyaImportDialog) AlertDialog(
                        onDismissRequest = { showTuyaImportDialog = false },
                        title = { Text("Tuya-Geräte auswählen") },
                        text = {
                            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Wähle die Tuya-Geräte aus und ordne jedes Gerät direkt einem Raum zu. Die Auswahl wird erst nach Bestätigung übernommen.", color = Muted, fontSize = 12.sp)
                                discoveredTuyaDevices.forEach { item ->
                                    val id = item.stateId.orEmpty()
                                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        Row(Modifier.fillMaxWidth().clickable {
                                            selectedTuyaIds = if (id in selectedTuyaIds) selectedTuyaIds - id else selectedTuyaIds + id
                                        }, verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = id in selectedTuyaIds, onCheckedChange = { checked ->
                                                selectedTuyaIds = if (checked) selectedTuyaIds + id else selectedTuyaIds - id
                                            })
                                            Column(Modifier.weight(1f)) {
                                                OutlinedTextField(value = discoveredNames[id] ?: item.name, onValueChange = { discoveredNames = discoveredNames + (id to it) }, label = { Text("Gerätename") }, singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = { InputConfirmIcon() })
                                                Text(id, color = Muted, fontSize = 9.sp)
                                            }
                                        }
                                        TextButton(onClick = {
                                            hiddenIds = hiddenIds + id
                                            prefs.edit().putStringSet("hiddenDevices", hiddenIds).apply()
                                            hiddenItems = hiddenItems.filterNot { it.stateId == id } + item.copy(name = discoveredNames[id]?.trim().orEmpty().ifBlank { item.name }, room = selectedTuyaRooms[id] ?: "Nicht zugeordnet")
                                            persistHiddenItems()
                                            discoveredTuyaDevices.removeAll { it.stateId == id }
                                            selectedTuyaIds = selectedTuyaIds - id
                                        }) { Text("Ausblenden") }
                                        var roomMenuExpanded by remember(id) { mutableStateOf(false) }
                                        OutlinedButton(onClick = { roomMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Raum: ${selectedTuyaRooms[id] ?: "Nicht zugeordnet"}")
                                        }
                                        DropdownMenu(expanded = roomMenuExpanded, onDismissRequest = { roomMenuExpanded = false }) {
                                            rooms.forEach { room ->
                                                DropdownMenuItem(text = { Text(room) }, onClick = {
                                                    selectedTuyaRooms = selectedTuyaRooms + (id to room)
                                                    roomMenuExpanded = false
                                                })
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val chosen = discoveredTuyaDevices.filter { it.stateId in selectedTuyaIds }
                                chosen.forEach { item ->
                                    val id = item.stateId ?: return@forEach
                                    val room = selectedTuyaRooms[id] ?: "Nicht zugeordnet"
                                    deviceRooms = deviceRooms.toMutableMap().apply { put(id, room) }
                                    val chosenName = discoveredNames[id]?.trim().orEmpty().ifBlank { item.name }
                                    customNames = customNames.toMutableMap().apply { put(id, chosenName) }
                                    if (devices.none { it.stateId == id }) devices.add(item.copy(name = chosenName, room = room))
                                }
                                persistDeviceRooms()
                                persistNames()
                                applyCustomConfig()
                                refreshStates(ioClient, serverUrl, devices, lights)
                                showTuyaImportDialog = false
                                scanResultMessage = "Tuya-Geräte übernommen: ${chosen.size}"
                            }) { Text("Auswahl übernehmen (${selectedTuyaIds.size})") }
                        },
                        dismissButton = { TextButton(onClick = { showTuyaImportDialog = false }) { Text("Überspringen") } }
                    )
                    if (showLightImportDialog) AlertDialog(
                        onDismissRequest = { showLightImportDialog = false },
                        title = { Text("Hue-Geräte auswählen") },
                        text = {
                            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Wähle nur die Lampen aus, die du in HomePilot verwenden möchtest. Andere Datenpunkte werden nicht übernommen.", color = Muted, fontSize = 12.sp)
                                discoveredLights.forEach { item ->
                                    val id = item.stateId.orEmpty()
                                    Row(Modifier.fillMaxWidth().clickable {
                                        selectedLightIds = if (id in selectedLightIds) selectedLightIds - id else selectedLightIds + id
                                    }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = id in selectedLightIds, onCheckedChange = { checked ->
                                            selectedLightIds = if (checked) selectedLightIds + id else selectedLightIds - id
                                        })
                                        Column(Modifier.weight(1f)) {
                                            OutlinedTextField(value = discoveredNames[id] ?: item.name, onValueChange = { discoveredNames = discoveredNames + (id to it) }, label = { Text("Gerätename") }, singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = { InputConfirmIcon() })
                                            Text(id, color = Muted, fontSize = 9.sp)
                                        }
                                    }
                                    TextButton(onClick = {
                                        hiddenIds = hiddenIds + id
                                        prefs.edit().putStringSet("hiddenDevices", hiddenIds).apply()
                                        hiddenItems = hiddenItems.filterNot { it.stateId == id } + item.copy(name = discoveredNames[id]?.trim().orEmpty().ifBlank { item.name })
                                        persistHiddenItems()
                                        discoveredLights.removeAll { it.stateId == id }
                                        selectedLightIds = selectedLightIds - id
                                    }) { Text("Ausblenden") }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val chosen = discoveredLights.filter { it.stateId in selectedLightIds }
                                val chosenIds = chosen.mapNotNull { it.stateId }.toSet()
                                importedLightIds = importedLightIds + chosenIds
                                prefs.edit().putStringSet("importedLights", importedLightIds).apply()
                                chosen.forEach { item -> item.stateId?.let { id -> customNames = customNames.toMutableMap().apply { put(id, discoveredNames[id]?.trim().orEmpty().ifBlank { item.name }) } } }
                                persistNames()
                                lights.addAll(chosen.map { item -> item.copy(name = discoveredNames[item.stateId]?.trim().orEmpty().ifBlank { item.name }) }.filter { item -> lights.none { it.stateId == item.stateId } })
                                applyCustomConfig()
                                refreshStates(ioClient, serverUrl, devices, lights)
                                showLightImportDialog = false
                            }) { Text("Auswahl übernehmen (${selectedLightIds.size})") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showLightImportDialog = false }) { Text("Überspringen") }
                        }
                    )
                    if (showHiddenDevices) AlertDialog(
                        onDismissRequest = { showHiddenDevices = false },
                        title = { Text("Ausgeblendete Geräte") },
                        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                            if (hiddenItems.isEmpty()) Text("Keine ausgeblendeten Geräte.")
                            hiddenItems.forEach { item -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(item.name, fontWeight = FontWeight.SemiBold); Text(item.subtitle, color = Muted, fontSize = 12.sp) }
                                TextButton(onClick = {
                                    val id = item.stateId
                                    if (id != null) { hiddenIds = hiddenIds - id; prefs.edit().putStringSet("hiddenDevices", hiddenIds).apply(); hiddenItems = hiddenItems.filterNot { it.stateId == id }; persistHiddenItems(); if (item.kind == "light") { importedLightIds = importedLightIds + id; prefs.edit().putStringSet("importedLights", importedLightIds).apply(); if (lights.none { it.stateId == id }) lights.add(item) } else { if (devices.none { it.stateId == id }) devices.add(item) }; applyCustomConfig(); refreshStates(ioClient, serverUrl, devices, lights) }
                                }) { Text("Wieder hinzufügen") }
                            } }
                        } },
                        confirmButton = { TextButton(onClick = { showHiddenDevices = false }) { Text("Fertig") } }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                NavigationBar(
                    modifier = Modifier.height(64.dp),
                    containerColor = panel,
                    windowInsets = WindowInsets(0.dp)
                ) {
                    listOf(Tab.HOME, Tab.FAVORITES, Tab.ENERGY, Tab.SETTINGS).forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = {
                                if (tab == Tab.HOME) {
                                    selectedRoom = null
                                    tabHistory = emptyList()
                                    tabSwipeDirection = -1
                                    selectedTab = Tab.HOME
                                } else navigateTo(tab)
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(20.dp)) },
                            label = { Text(tab.label, fontSize = 9.sp, maxLines = 1) },
                            alwaysShowLabel = true
                        )
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(0.dp))
            }
        }
    }
}

@Composable
private fun Header(dark: Boolean, text: Color, connected: Boolean, status: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("HOMEPILOT", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Icon(if (connected) Icons.Default.CheckCircle else Icons.Default.Cancel, contentDescription = if (connected) "ioBroker verbunden" else "ioBroker nicht verbunden", tint = if (connected) Green else Color(0xFFD62828), modifier = Modifier.size(14.dp))
            }
            Text("Smart Home auf einen Blick", color = if (dark) Color(0xFFB2C1D0) else Muted, fontSize = 13.sp)
            if (!connected && status.startsWith("Verbindung fehlgeschlagen")) Text("Verbindung fehlgeschlagen. Bitte VPN prüfen.", color = Color(0xFFD62828), fontSize = 11.sp, maxLines = 2)
        }
    }
}

@Composable
private fun RoomMenu(rooms: List<String>, selectedRoom: String?, panel: Color, text: Color, onSelectRoom: (String) -> Unit, extraItems: List<Pair<String, () -> Unit>> = emptyList()) {
    val entries = rooms.filter { it != "Nicht zugeordnet" }.map { room -> room to { onSelectRoom(room) } } + extraItems
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        entries.chunked(3).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                rowItems.forEach { (label, action) ->
                    val selected = label == selectedRoom
                    Button(
                        onClick = action,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (selected) Blue else panel, contentColor = if (selected) Color.White else text),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp)
                    ) {
                        Text(label, fontSize = 13.sp, maxLines = 2, textAlign = TextAlign.Center, lineHeight = 15.sp)
                    }
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun InputConfirmIcon() {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    IconButton(onClick = {
        keyboardController?.hide()
        focusManager.clearFocus()
    }) {
        Icon(Icons.Default.Check, contentDescription = "Eingabe bestätigen", tint = Green)
    }
}

private data class ForecastDay(val date: String, val code: Int, val high: Double, val low: Double, val rain: Int?)
private fun weatherDescription(code: Int): String = when (code) {
    0 -> "Sonnig"; 1 -> "Überwiegend sonnig"; 2 -> "Heiter bis wolkig"; 3 -> "Bewölkt"
    45, 48 -> "Nebel"; 51, 53, 55 -> "Nieselregen"; 56, 57 -> "Gefrierender Nieselregen"
    61, 63, 65 -> "Regen"; 66, 67 -> "Gefrierender Regen"; 71, 73, 75, 77 -> "Schnee"
    80, 81, 82 -> "Regenschauer"; 85, 86 -> "Schneeschauer"; 95, 96, 99 -> "Gewitter"
    else -> "Wetterlage"
}
private fun fetchWeather(city: String): Pair<String, List<ForecastDay>> {
    fun getJson(url: String): JSONObject {
        val connection: java.net.HttpURLConnection =
            (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Wetterdienst nicht erreichbar")
            }
            val responseText: String = connection.inputStream
                .bufferedReader(Charsets.UTF_8)
                .use { reader: java.io.BufferedReader -> reader.readText() }
            return JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }
    // German postcodes and town names must be resolved in Germany, not globally.
    val entered = city.trim()
    val postcodeAndPlace = Regex("^(\\d{5})\\s+(.+)$").matchEntire(entered)
    val searchTerm = postcodeAndPlace?.groupValues?.get(2) ?: entered
    val encoded = java.net.URLEncoder.encode(searchTerm, "UTF-8")
    val geo = getJson("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=10&language=de&format=json&countryCode=DE")
    val results = geo.optJSONArray("results")
    val place = results?.optJSONObject(0) ?: throw IllegalStateException("Ort in Deutschland nicht gefunden. Bitte Ort oder Postleitzahl prüfen.")
    val lat = place.optDouble("latitude"); val lon = place.optDouble("longitude")
    val display = place.optString("name", city) + (place.optString("admin1").takeIf { it.isNotBlank() }?.let { ", $it" } ?: "")
    val data = getJson("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=5&timezone=Europe%2FBerlin")
    val current = data.optJSONObject("current") ?: throw IllegalStateException("Keine aktuellen Wetterdaten")
    val daily = data.optJSONObject("daily") ?: throw IllegalStateException("Keine Vorhersage verfügbar")
    val dates = daily.optJSONArray("time") ?: JSONArray(); val codes = daily.optJSONArray("weather_code") ?: JSONArray()
    val highs = daily.optJSONArray("temperature_2m_max") ?: JSONArray(); val lows = daily.optJSONArray("temperature_2m_min") ?: JSONArray()
    val rains = daily.optJSONArray("precipitation_probability_max") ?: JSONArray()
    val days = (0 until minOf(5, dates.length())).map { i -> ForecastDay(dates.optString(i), codes.optInt(i), highs.optDouble(i), lows.optDouble(i), if (rains.isNull(i)) null else rains.optInt(i)) }
    return "$display|${current.optDouble("temperature_2m")}|${current.optInt("weather_code")}" to days
}
@Composable
private fun WeatherForecastCard(city: String, panel: Color, text: Color) {
    var currentLabel by remember { mutableStateOf<String?>(null) }
    var days by remember { mutableStateOf<List<ForecastDay>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(city) {
        if (city.isBlank()) { error = "Bitte in den Einstellungen einen Ort eintragen."; days = emptyList(); return@LaunchedEffect }
        loading = true; error = null
        try {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { fetchWeather(city) }
            val parts = result.first.split("|")
            currentLabel = "${parts[0]} · ${parts[1].toDoubleOrNull()?.let { String.format(java.util.Locale.GERMANY, "%.0f °C", it) } ?: "–"} · ${weatherDescription(parts.getOrNull(2)?.toIntOrNull() ?: -1)}"
            days = result.second
            if (days.isEmpty()) error = "Keine Vorhersage verfügbar."
        } catch (e: Exception) { error = e.message?.takeIf { it.isNotBlank() } ?: "Wetterdaten konnten nicht geladen werden."; days = emptyList() }
        finally { loading = false }
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = panel)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.WbSunny, null, tint = Blue)
                Text("Wetter · 5 Tage", color = text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.weight(1f))
                if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            currentLabel?.let { Text(it, color = text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            if (error != null) Text(error!!, color = Muted, fontSize = 12.sp)
            days.forEachIndexed { index, day ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val label = try { val d = java.time.LocalDate.parse(day.date); when (index) { 0 -> "Heute"; 1 -> "Morgen"; else -> d.format(java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale.GERMAN)) } } catch (_: Exception) { day.date }
                    Text(label, color = text, modifier = Modifier.width(62.dp), fontSize = 12.sp)
                    Text(weatherDescription(day.code), color = Muted, modifier = Modifier.weight(1f), fontSize = 12.sp, maxLines = 1)
                    Text("${day.high.toInt()}° / ${day.low.toInt()}°", color = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    day.rain?.let { Text("$it%", color = Blue, fontSize = 11.sp, modifier = Modifier.width(30.dp), textAlign = TextAlign.End) }
                }
            }
            if (days.isNotEmpty()) Text("Regenwahrscheinlichkeit rechts · Quelle: Open-Meteo", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun MovaIllustration(size: androidx.compose.ui.unit.Dp) {
    AsyncImage(
        model = "https://dreame.de/cdn/shop/files/Mova_1000_WB_01.jpg?v=1745999938&width=1000",
        contentDescription = "Originalproduktbild MOVA 1000",
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(Color.White)
    )
}

@Composable
private fun MovaHomeTile(serverUrl: String, connected: Boolean, client: IoBrokerClient, panel: Color, text: Color, onOpen: () -> Unit) {
    var battery by remember { mutableStateOf<Double?>(null) }
    var mowerStatus by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(serverUrl, connected) {
        if (!connected) { battery = null; mowerStatus = null }
        else while (true) {
            client.getNumericState(serverUrl, "dreame.0.-112285430.status.battery-level") { battery = it }
            client.getNumericState(serverUrl, "dreame.0.-112285430.status.status") { mowerStatus = it }
            delay(30_000)
        }
    }
    val status = when (mowerStatus?.toInt()) {
        1, 3, 12 -> "Mäht gerade"
        2 -> "Bereit"
        4 -> "Pausiert"
        5 -> "Fährt zur Ladestation"
        6 -> "Lädt"
        7 -> "Störung"
        8 -> "Regenpause"
        13 -> "Bereit"
        else -> "Status wird geladen"
    }
    CardBlock(panel, text, "MOVA 1000", "Mähroboter · Steuerung und Status öffnen", Icons.Default.Park, action = onOpen) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MovaIllustration(104.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(status, color = if (mowerStatus?.toInt() in listOf(1,3,12)) Green else text, fontWeight = FontWeight.SemiBold)
                if (!connected) Text("Verbindung fehlt", color = Color(0xFFD62828), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Akku: ${battery?.let { "${it.toInt()} %" } ?: "—"}", color = text, fontSize = 13.sp)
                Text("Zur Robotersteuerung  ›", color = Blue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MovaDetailScreen(serverUrl: String, connected: Boolean, client: IoBrokerClient, panel: Color, text: Color, onBack: () -> Unit) {
    var battery by remember { mutableStateOf<Double?>(null) }
    var mowerStatus by remember { mutableStateOf<Double?>(null) }
    var bladeHealth by remember { mutableStateOf<Double?>(null) }
    var maintenanceHealth by remember { mutableStateOf<Double?>(null) }
    var commandMessage by remember { mutableStateOf<String?>(null) }
    var commandBusy by remember { mutableStateOf(false) }
    var showMaintenanceConfirm by remember { mutableStateOf(false) }
    var zones by remember { mutableStateOf(listOf<Pair<Int, Pair<String, Double>>>()) }
    var selectedZones by remember { mutableStateOf(setOf<Int>()) }
    LaunchedEffect(serverUrl, connected) {
        if (!connected) {
            battery = null; mowerStatus = null; bladeHealth = null; maintenanceHealth = null; zones = emptyList()
        } else {
            // Mähzonen-Namen und -Flächen einmal beim Öffnen der Ansicht laden.
            val loaded = java.util.Collections.synchronizedList(mutableListOf<Pair<Int, Pair<String, Double>>>())
            var remaining = 4
            for (zoneId in 1..4) {
                val prefix = "dreame.0.-112285430.map.slot0.zone${zoneId}"
                client.getTextState(serverUrl, "$prefix.name") { name ->
                    client.getNumericState(serverUrl, "$prefix.area") { area ->
                        if (area != null && area > 0.0) { val displayName = name?.takeIf { !it.matches(Regex("(?i)zone\\s*\\d+")) } ?: "Mähzone $zoneId"; loaded.add(zoneId to (displayName to area)) }
                        remaining--
                        if (remaining == 0) {
                            zones = loaded.sortedBy { it.first }
                            val valid = loaded.map { it.first }.toSet()
                            selectedZones = selectedZones.intersect(valid)
                        }
                    }
                }
            }
            while (true) {
                client.getNumericState(serverUrl, "dreame.0.-112285430.status.battery-level") { battery = it }
                client.getNumericState(serverUrl, "dreame.0.-112285430.status.status") { mowerStatus = it }
                client.getNumericState(serverUrl, "dreame.0.-112285430.status.blade-health") { bladeHealth = it }
                client.getNumericState(serverUrl, "dreame.0.-112285430.status.robot-maintenance-health") { maintenanceHealth = it }
                delay(30_000)
            }
        }
    }
    val statusCode = mowerStatus?.toInt()
    val statusLabel = when (statusCode) {
        1, 3, 12 -> "Mäht gerade"
        2 -> "Bereit"
        4 -> "Pausiert"
        5 -> "Fährt zur Ladestation"
        6 -> "Lädt"
        7 -> "Störung"
        8 -> "Regenpause"
        13 -> "Bereit"
        else -> "Status nicht verfügbar"
    }
    val mowing = statusCode in listOf(1, 3, 12)
    val canDock = connected && statusCode != null && statusCode !in listOf(5, 6, 13)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Zurück", tint = Blue) }
            Text("MOVA 1000", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text)
        }
        CardBlock(panel, text, "MOVA 1000", "Gerätestatus", Icons.Default.Park) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MovaIllustration(124.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(statusLabel, color = if (mowing) Green else text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    if (!connected) Text("Verbindung fehlt", color = Color(0xFFD62828), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Akku: ${battery?.let { "${it.toInt()} %" } ?: "—"}", color = text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            battery?.let { LinearProgressIndicator(progress = { (it / 100.0).toFloat().coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth()) }
        }
        CardBlock(panel, text, "Mähzonen", "Eine oder mehrere Zonen auswählen und gezielt starten", Icons.Default.Map) {
            if (!connected) Text("Bitte zuerst die ioBroker-Verbindung herstellen.", color = Muted)
            else if (zones.isEmpty()) Text("Zonen werden geladen. Bitte kurz warten.", color = Muted)
            zones.forEach { (id, info) ->
                Row(Modifier.fillMaxWidth().clickable {
                    selectedZones = if (id in selectedZones) selectedZones - id else selectedZones + id
                }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = id in selectedZones, onCheckedChange = { checked -> selectedZones = if (checked) selectedZones + id else selectedZones - id })
                    Column(Modifier.weight(1f)) {
                        Text(info.first, color = text, fontWeight = FontWeight.SemiBold)
                        Text("Zone $id · ${String.format(java.util.Locale.GERMANY, "%.1f", info.second)} m²", color = Muted, fontSize = 14.sp)
                    }
                }
            }
            Button(onClick = {
                commandBusy = true; commandMessage = null
                client.setTextState(serverUrl, "dreame.0.-112285430.remote.mow-zone", selectedZones.sorted().joinToString(",")) { ok, msg ->
                    commandBusy = false; commandMessage = if (ok) "Mähbefehl für Zone(n) ${selectedZones.sorted().joinToString(", ")} gesendet" else msg
                }
            }, enabled = connected && selectedZones.isNotEmpty() && !commandBusy && !mowing, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Ausgewählte Zone(n) starten")
            }
            commandMessage?.let { Text(it, color = Muted, fontSize = 14.sp) }
        }
        CardBlock(panel, text, "Steuerung", "Mähvorgang und Ladestation", Icons.Default.Gamepad) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { commandBusy = true; client.setState(serverUrl, "dreame.0.-112285430.remote.pause-mow", true) { ok, msg -> commandBusy = false; commandMessage = if(ok) "Pausebefehl gesendet" else msg } }, enabled = connected && mowing && !commandBusy, modifier = Modifier.weight(1f).height(52.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFDCEAF5), contentColor = Color(0xFF155B8D), disabledContainerColor = Color(0xFFDCEAF5), disabledContentColor = Color(0xFF536B7D)), border = BorderStroke(1.5.dp, Color(0xFF4B86AE))) { Icon(Icons.Default.Pause, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(4.dp)); Text("Pause", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                OutlinedButton(onClick = { commandBusy = true; client.setState(serverUrl, "dreame.0.-112285430.remote.stop-mow", true) { ok, msg -> commandBusy = false; commandMessage = if(ok) "Stoppbefehl gesendet" else msg } }, enabled = connected && (mowing || statusCode == 4) && !commandBusy, modifier = Modifier.weight(1f).height(52.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFDCEAF5), contentColor = Color(0xFF155B8D), disabledContainerColor = Color(0xFFDCEAF5), disabledContentColor = Color(0xFF536B7D)), border = BorderStroke(1.5.dp, Color(0xFF4B86AE))) { Icon(Icons.Default.Stop, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(4.dp)); Text("Stop", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
                OutlinedButton(onClick = { commandBusy = true; client.setState(serverUrl, "dreame.0.-112285430.remote.start-charge", true) { ok, msg -> commandBusy = false; commandMessage = if(ok) "Rückkehrbefehl gesendet" else msg } }, enabled = canDock && !commandBusy, modifier = Modifier.weight(1f).height(52.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFDCEAF5), contentColor = Color(0xFF155B8D), disabledContainerColor = Color(0xFFDCEAF5), disabledContentColor = Color(0xFF536B7D)), border = BorderStroke(1.5.dp, Color(0xFF4B86AE))) { Icon(Icons.Default.Home, null, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(4.dp)); Text("Laden", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
            }
        }
        CardBlock(panel, text, "Wartung", "Zustand der Verschleißteile und Wartungspunkt", Icons.Default.Build) {
            Text("Messerzustand: ${bladeHealth?.let { "${it.toInt()} %" } ?: "—"}", color = text)
            Text("Gerätewartung: ${maintenanceHealth?.let { "${it.toInt()} %" } ?: "—"}", color = text)
            Spacer(Modifier.height(6.dp))
            Text("Fährt den in der MOVA-App gespeicherten Wartungspunkt an.", color = Muted, fontSize = 12.sp)
            OutlinedButton(
                onClick = { showMaintenanceConfirm = true },
                enabled = connected && !commandBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFDCEAF5), contentColor = Color(0xFF155B8D)),
                border = BorderStroke(1.5.dp, Color(0xFF4B86AE))
            ) {
                Icon(Icons.Default.Build, null)
                Spacer(Modifier.width(8.dp))
                Text("Wartungspunkt anfahren", fontWeight = FontWeight.Bold)
            }
            commandMessage?.let { Text(it, color = Muted, fontSize = 13.sp) }
        }
    }
    if (showMaintenanceConfirm) {
        AlertDialog(
            onDismissRequest = { if (!commandBusy) showMaintenanceConfirm = false },
            title = { Text("Wartungspunkt anfahren?") },
            text = { Text("Der MOVA 1000 erhält den Befehl, zum in der MOVA-App gespeicherten Wartungspunkt zu fahren. Stelle sicher, dass der Weg frei ist.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMaintenanceConfirm = false
                        commandBusy = true
                        commandMessage = null
                        val command = """{"siid":2,"aiid":50,"in":[{"m":"a","p":0,"o":109,"d":{"point":[1]}}]}"""
                        client.setTextState(serverUrl, "dreame.0.-112285430.remote.customCommand", command) { ok, msg ->
                            commandBusy = false
                            commandMessage = if (ok) "Anfahrt zum Wartungspunkt beauftragt." else msg
                        }
                    },
                    enabled = !commandBusy
                ) { Text("Anfahren") }
            },
            dismissButton = {
                TextButton(onClick = { showMaintenanceConfirm = false }, enabled = !commandBusy) { Text("Abbrechen") }
            }
        )
    }
}

private fun roidmiStateLabel(value: Int): String = when (value) {
    1 -> "Schläft"; 2 -> "Bereit"; 3 -> "Pausiert"; 4 -> "Reinigt"; 5 -> "Fährt zur Ladestation"
    6 -> "Lädt"; 7 -> "Fehler"; 8 -> "Fernsteuerung"; 9 -> "Vollständig geladen"
    10 -> "Ausgeschaltet"; 11 -> "Rückfahrt pausiert"; else -> "Unbekannter Status ($value)"
}
private fun roidmiChargingLabel(value: Int, robotState: Int?): String = when {
    robotState == 9 -> "Vollständig geladen"
    robotState == 6 || value == 1 -> "Lädt"
    value == 2 -> "Lädt nicht"
    value == 3 || value == 4 -> "Nicht ladefähig (Quellen nennen unterschiedliche Codes)"
    else -> "Unbekannter Ladecode ($value)"
}
private fun roidmiSweepLabel(value: Int): String = when (value) {
    0 -> "Saugen"; 1 -> "Wischen"; 2 -> "Saugen und Wischen"; else -> "Unbekannt ($value)"
}
private fun roidmiFanLabel(value: Int): String = when (value) {
    0 -> "Aus (nur Wischen)"; 1 -> "Leise"; 2 -> "Standard"; 3 -> "Stark"; 4 -> "Maximum"; else -> "Unbekannt ($value)"
}
private fun roidmiWaterLabel(value: Int): String = when (value) {
    0 -> "Aus (nur Saugen)"; 1 -> "Gering"; 2 -> "Mittel"; 3 -> "Maximum"
    4 -> "Stufe 4 (nur im python-miio-Mapping)"; else -> "Nicht definiert ($value)"
}
private fun roidmiErrorLabel(value: Int): String = when (value) {
    0 -> "Kein Fehler"; 1 -> "Akku schwach – fährt zur Ladestation"; 2 -> "Akku schwach – schaltet ab"
    3 -> "Hauptrad blockiert"; 4 -> "Stoßfänger blockiert"; 5 -> "Roboter steht schief"
    6 -> "Lasersensor blockiert"; 7 -> "Vorderer Kollisionssensor"; 8 -> "Sensorfehler (Quellen uneinheitlich)"
    9 -> "Hauptbürste blockiert"; 10 -> "Seitenbürste blockiert"; 11 -> "Lüfterfehler"
    12 -> "Laserdeckel blockiert"; 13 -> "Staubbehälter/Filter prüfen"; 14 -> "Staubbehälter entfernt"
    15 -> "Staubbehälter und Filter reinigen"; 16 -> "Roboter festgefahren"; 17 -> "Roboter angehoben"
    18 -> "Wassertank entfernt"; 19 -> "Wassertank fast leer"; 20 -> "Zielbereich nicht erreichbar"
    21 -> "Start in Sperrzone"; 22 -> "Start nahe einer Kante"; 23 -> "Wasserpumpenfehler"
    24 -> "Ladestation nicht gefunden"; 25 -> "Laserdrehzahlfehler"; 27 -> "Laser blockiert"
    30 -> "Laser-Unterbrechung"; 31 -> "Akku-Fehler"; 32 -> "Hauptbürstenfehler"
    33 -> "Hauptradfehler"; 34 -> "Seitenbürstenfehler"; 35 -> "Lüfterfehler"
    36 -> "Wasserpumpenfehler"; 37 -> "Firmware-Update fehlgeschlagen"
    else -> "Unbekannter Fehlercode ($value)"
}
private const val RoidmiProductImage = "https://storage.googleapis.com/bucket-two-leobotics/product/1-2.jpg"
@Composable private fun RoidmiProductPhoto(size: androidx.compose.ui.unit.Dp) {
    AsyncImage(model = RoidmiProductImage, contentDescription = "Roidmi EVE Plus mit Absaugstation", modifier = Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(Color.White), contentScale = ContentScale.Fit)
}
@Composable private fun RoidmiHomeTile(serverUrl: String, connected: Boolean, client: IoBrokerClient, panel: Color, text: Color, onOpen: () -> Unit) {
    var adapter by remember { mutableStateOf(client.cachedText("roidmi.0.info.connection") == "true") }; var battery by remember { mutableStateOf(client.cachedNumber("roidmi.0.battery")) }; var state by remember { mutableStateOf(client.cachedNumber("roidmi.0.state")) }; var error by remember { mutableStateOf(client.cachedNumber("roidmi.0.errorCode")) }
    LaunchedEffect(serverUrl, connected) {
        if (connected) {
            client.cachedNumber("roidmi.0.battery")?.let { battery = it }
            client.cachedNumber("roidmi.0.state")?.let { state = it }
            while (true) {
                client.getState(serverUrl, "roidmi.0.info.connection") { v, _ -> if (v != null) adapter = v }
                client.getNumericState(serverUrl, "roidmi.0.battery") { battery = it ?: battery }
                client.getNumericState(serverUrl, "roidmi.0.state") { state = it ?: state }
                client.getNumericState(serverUrl, "roidmi.0.errorCode") { error = it ?: error }
                delay(30_000)
            }
        }
    }
    CardBlock(panel, text, "Roidmi EVE Plus", "Saugroboter · Status und Steuerung öffnen", Icons.Default.CleaningServices, action = onOpen) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { RoidmiProductPhoto(104.dp); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                when {
                    !connected -> "Offline · letzter Stand"
                    adapter && error == 0.0 -> "Bereit"
                    adapter && error != null && error != 0.0 -> roidmiErrorLabel(error!!.toInt())
                    adapter -> state?.let { roidmiStateLabel(it.toInt()) } ?: "Status wird geladen"
                    else -> "Nicht verbunden"
                },
                color = if (connected && adapter && error == 0.0) text else Muted,
                fontWeight = FontWeight.SemiBold
            )
            Text("Akku: " + (battery?.let { it.toInt().toString() + " %" } ?: "—"), color = text, fontSize = 13.sp)
            Text("Zur Robotersteuerung  ›", color = Blue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        } }
    }
}
@Composable private fun RoidmiDetailScreen(serverUrl: String, connected: Boolean, client: IoBrokerClient, panel: Color, text: Color, onBack: () -> Unit) {
    var adapter by remember { mutableStateOf(client.cachedText("roidmi.0.info.connection") == "true") }; var battery by remember { mutableStateOf(client.cachedNumber("roidmi.0.battery")) }; var charging by remember { mutableStateOf(client.cachedNumber("roidmi.0.charging")) }; var state by remember { mutableStateOf(client.cachedNumber("roidmi.0.state")) }; var error by remember { mutableStateOf(client.cachedNumber("roidmi.0.errorCode")) }; var fan by remember { mutableStateOf(client.cachedNumber("roidmi.0.fanSpeed")) }; var sweep by remember { mutableStateOf(client.cachedNumber("roidmi.0.sweepType")) }; var water by remember { mutableStateOf(client.cachedNumber("roidmi.0.waterLevel")) }; var roomsRaw by remember { mutableStateOf(client.cachedText("roidmi.0.map.roomsJson")) }; var mapId by remember { mutableStateOf(client.cachedNumber("roidmi.0.map.mapId")) }
    var selected by remember { mutableStateOf(setOf<Int>()) }; var busy by remember { mutableStateOf(false) }; var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(serverUrl, connected) {
        if (connected) {
            client.cachedText("roidmi.0.map.roomsJson")?.let { roomsRaw = it }
            client.cachedNumber("roidmi.0.map.mapId")?.let { mapId = it }
            while (true) {
                client.getState(serverUrl, "roidmi.0.info.connection") { v, _ -> if (v != null) adapter = v }
                client.getNumericState(serverUrl, "roidmi.0.battery") { battery = it ?: battery }
                client.getNumericState(serverUrl, "roidmi.0.charging") { charging = it ?: charging }
                client.getNumericState(serverUrl, "roidmi.0.state") { state = it ?: state }
                client.getNumericState(serverUrl, "roidmi.0.errorCode") { error = it ?: error }
                client.getNumericState(serverUrl, "roidmi.0.fanSpeed") { fan = it ?: fan }
                client.getNumericState(serverUrl, "roidmi.0.sweepType") { sweep = it ?: sweep }
                client.getNumericState(serverUrl, "roidmi.0.waterLevel") { water = it ?: water }
                client.getTextState(serverUrl, "roidmi.0.map.roomsJson") { roomsRaw = it ?: roomsRaw }
                client.getNumericState(serverUrl, "roidmi.0.map.mapId") { mapId = it ?: mapId }
                delay(30_000)
            }
        }
    }
    val rooms = remember(roomsRaw) { runCatching { val j = org.json.JSONArray(roomsRaw ?: "[]"); (0 until j.length()).mapNotNull { i -> val o = j.optJSONObject(i) ?: return@mapNotNull null; val id = o.optInt("id", -1); val name = o.optString("name").trim(); if (id > 0 && name.isNotBlank()) id to name else null } }.getOrDefault(emptyList()) }
    fun command(id: String, label: String) { if (!connected || !adapter || busy) return; busy = true; message = null; client.setState(serverUrl, "roidmi.0.control." + id, true) { ok, msg -> busy = false; message = if (ok) label + " an den Adapter übergeben." else msg } }
    fun setting(id: String, value: Int, label: String) { if (!connected || !adapter) return; client.setNumericState(serverUrl, "roidmi.0." + id, value.toDouble()) { ok, msg -> message = if (ok) label + " gespeichert." else msg } }
    val batteryText = battery?.let { it.toInt().toString() + " %" } ?: "—"
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Zurück", tint = Blue) }; Text("Roidmi EVE Plus", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text) }
        CardBlock(panel, text, "Gerätestatus", "Daten aus dem ioBroker-Adapter Roidmi", Icons.Default.CleaningServices) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoidmiProductPhoto(124.dp); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        when {
                            !connected -> "Offline · letzter Stand"
                            adapter && error == 0.0 -> "Bereit"
                            adapter && error != null && error != 0.0 -> roidmiErrorLabel(error!!.toInt())
                            adapter -> state?.let { roidmiStateLabel(it.toInt()) } ?: "Status wird geladen"
                            else -> "Nicht verbunden"
                        },
                        color = if (connected && adapter && error == 0.0) text else Muted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text("Akku: " + batteryText, color = text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            battery?.let { LinearProgressIndicator(progress = { (it / 100.0).toFloat().coerceIn(0f,1f) }, modifier = Modifier.fillMaxWidth()) }
            if (adapter && error != null && error != 0.0) Text("Fehlercode: ${error!!.toInt()}", color = Muted, fontSize = 12.sp)
        }
        CardBlock(panel, text, "Reinigungszonen", "Räume auswählen und gezielt reinigen", Icons.Default.Map) {
            if (rooms.isEmpty() || mapId == null || mapId!! <= 0) Text("Noch keine gültige Raumkarte importiert. Importierte Räume erscheinen hier automatisch.", color = Muted)
            else {
                rooms.chunked(3).forEach { roomRow ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        roomRow.forEach { (id, name) ->
                            Row(
                                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp))
                                    .clickable { selected = if (id in selected) selected - id else selected + id }
                                    .padding(horizontal = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = id in selected, onCheckedChange = { v -> selected = if (v) selected + id else selected - id }, modifier = Modifier.size(30.dp))
                                Text(name, color = text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                        repeat(3 - roomRow.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Button(onClick = { val payload = org.json.JSONObject().put("mapId", mapId!!.toInt()).put("roomIds", org.json.JSONArray(selected.sorted())).toString(); busy = true; client.setTextState(serverUrl, "roidmi.0.control.cleanRooms", payload) { ok, msg -> busy = false; message = if (ok) "Reinigungsauftrag an den Adapter übergeben." else msg } }, enabled = connected && adapter && selected.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Ausgewählte Räume reinigen") }
            }
        }
        CardBlock(panel, text, "Steuerung", "Reinigung und Ladestation", Icons.Default.Gamepad) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { OutlinedButton(onClick = { command("start", "Reinigungsstart") }, enabled = connected && adapter && !busy, modifier = Modifier.weight(1f).height(52.dp)) { Icon(Icons.Default.PlayArrow, null); Text("Start") }; OutlinedButton(onClick = { command("pause", "Pause") }, enabled = connected && adapter && !busy, modifier = Modifier.weight(1f).height(52.dp)) { Icon(Icons.Default.Pause, null); Text("Pause") } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { OutlinedButton(onClick = { command("resume", "Fortsetzen") }, enabled = adapter && !busy, modifier = Modifier.weight(1f).height(52.dp)) { Icon(Icons.Default.PlayArrow, null); Text("Weiter") }; OutlinedButton(onClick = { command("dock", "Rückkehrbefehl") }, enabled = adapter && !busy, modifier = Modifier.weight(1f).height(52.dp)) { Icon(Icons.Default.Home, null); Text("Laden") } }
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth()); message?.let { Text(it, color = Muted, fontSize = 13.sp) }
        }
        CardBlock(panel, text, "Einstellungen", "Reinigungsart, Saugstufe und Wasserstufe", Icons.Default.Settings) {
            Text("Reinigungsart · " + (sweep?.let { roidmiSweepLabel(it.toInt()) } ?: "—"), color = text, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedButton(onClick = { setting("sweepType", 0, "Saugen") }, enabled = connected && adapter, modifier = Modifier.weight(1f)) { Text("Saugen") }; OutlinedButton(onClick = { setting("sweepType", 1, "Wischen") }, enabled = connected && adapter, modifier = Modifier.weight(1f)) { Text("Wischen") }; OutlinedButton(onClick = { setting("sweepType", 2, "Saugen/Wischen") }, enabled = connected && adapter, modifier = Modifier.weight(1f)) { Text("Kombi") } }
            Text("Saugstufe · " + (fan?.let { roidmiFanLabel(it.toInt()) } ?: "—"), color = text, fontWeight = FontWeight.SemiBold); OutlinedButton(onClick = { setting("fanSpeed", ((fan?.toInt() ?: 0) + 1) % 5, "Saugstufe") }, enabled = connected && adapter, modifier = Modifier.fillMaxWidth()) { Text("Saugstufe weiterschalten") }
            Text("Wasserstufe · " + (water?.let { roidmiWaterLabel(it.toInt()) } ?: "—"), color = text, fontWeight = FontWeight.SemiBold); OutlinedButton(onClick = { setting("waterLevel", ((water?.toInt() ?: 0) + 1) % 5, "Wasserstufe") }, enabled = connected && adapter, modifier = Modifier.fillMaxWidth()) { Text("Wasserstufe weiterschalten") }
        }
    }
}
@Composable
private fun HomeScreen(
    rooms: List<String>, lights: List<DeviceItem>, devices: List<DeviceItem>, floodAlarm: Boolean, floodBattery: Int, floodKnown: Boolean, floodBatteryKnown: Boolean,
    panel: Color, text: Color, selectedRoom: String?, searchQuery: String, weatherCity: String, serverUrl: String, connected: Boolean, ioClient: IoBrokerClient, onOpenMova: () -> Unit, onOpenRoidmi: () -> Unit,
    onSearch: (String) -> Unit, onSelectRoom: (String) -> Unit, onOpenRoom: (String) -> Unit, onTab: (Tab) -> Unit, onToggle: (DeviceItem, Boolean) -> Unit, onSetNumeric: (DeviceItem, String, Double) -> Unit
) {
    if (selectedRoom != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { onSelectRoom("") }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Zurück zur Startseite", tint = Blue)
            }
            Text(selectedRoom, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text)
        }
        RoomMenu(rooms, selectedRoom, panel, text, { room -> onOpenRoom(room) },
            listOf("Ohne Raum" to { onOpenRoom("Nicht zugeordnet") }))
        val roomItems = (devices + lights).filter {
            if (selectedRoom == "Nicht zugeordnet") it.room.isBlank() || it.room == "Nicht zugeordnet" else it.room == selectedRoom
        }.distinctBy { it.stateId ?: it.name }
        if (roomItems.isEmpty()) Text("In diesem Raum sind noch keine Geräte zugeordnet.", color = Muted)
        roomItems.forEach { item ->
            var expanded by remember(item.stateId) { mutableStateOf(false) }
            var brightness by remember(item.stateId) { mutableFloatStateOf(item.currentBrightness?.toFloat() ?: 100f) }
            var colorTemp by remember(item.stateId) { mutableFloatStateOf(item.currentColorTemp?.toFloat() ?: 285f) }
            var hue by remember(item.stateId) { mutableFloatStateOf(item.currentHue?.toFloat() ?: 0f) }
            var saturation by remember(item.stateId) { mutableFloatStateOf(item.currentSaturation?.toFloat() ?: 254f) }
            CardBlock(panel, text, item.name, item.room + " · " + if (item.initialOn) "Eingeschaltet" else "Ausgeschaltet",
                if (item.kind == "light") Icons.Default.Lightbulb else Icons.Default.Power) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (item.initialOn) "Eingeschaltet" else "Ausgeschaltet", color = if (item.initialOn) Green else Muted, modifier = Modifier.weight(1f))
                    Switch(checked = item.initialOn, enabled = item.writable && item.stateId != null, onCheckedChange = { onToggle(item, it) })
                }
                if (item.powerWatts != null) Text("Leistung: " + String.format(java.util.Locale.GERMANY, "%.1f W", item.powerWatts), color = Muted, fontSize = 12.sp)
                if (item.kind == "light" && (item.brightnessId != null || item.colorTempId != null || item.hueId != null)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Einklappen" else "Erweitern")
                            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        }
                    }
                    if (expanded) {
                        item.brightnessId?.let { id ->
                            Text("Helligkeit: ${brightness.toInt()}%", color = text, fontWeight = FontWeight.SemiBold)
                            Slider(value = brightness.coerceIn(1f, 100f), onValueChange = { brightness = it },
                                valueRange = 1f..100f, enabled = item.writable,
                                onValueChangeFinished = { onSetNumeric(item, id, brightness.toDouble() * 2.54) })
                        }
                        item.colorTempId?.let { id ->
                            Text("Farbtemperatur: ${colorTemp.toInt()} mired", color = text, fontWeight = FontWeight.SemiBold)
                            Slider(value = colorTemp.coerceIn(153f, 500f), onValueChange = { colorTemp = it },
                                valueRange = 153f..500f, enabled = item.writable,
                                onValueChangeFinished = { onSetNumeric(item, id, colorTemp.toDouble()) })
                        }
                        if (item.hueId != null && item.saturationId != null) {
                            Text("Farbton: ${hue.toInt()}", color = text, fontWeight = FontWeight.SemiBold)
                            Slider(value = hue.coerceIn(0f, 65535f), onValueChange = { hue = it },
                                valueRange = 0f..65535f, enabled = item.writable,
                                onValueChangeFinished = { onSetNumeric(item, item.hueId, hue.toDouble()) })
                            Text("Sättigung: ${saturation.toInt()}", color = text, fontWeight = FontWeight.SemiBold)
                            Slider(value = saturation.coerceIn(0f, 254f), onValueChange = { saturation = it },
                                valueRange = 0f..254f, enabled = item.writable,
                                onValueChangeFinished = { onSetNumeric(item, item.saturationId, saturation.toDouble()) })
                        }
                    }
                }
            }
        }
        return
    }
    OutlinedTextField(value = searchQuery, onValueChange = onSearch, modifier = Modifier.fillMaxWidth(), singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearch("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Suche löschen")
                }
            }
            InputConfirmIcon()
            }
        },
        placeholder = { Text("Gerätenamen suchen") })
    val allDevices = devices + lights
    if (searchQuery.isNotBlank()) {
        allDevices.filter { item -> item.name.split(Regex("[^\\p{L}\\p{N}]+")).any { word -> word.startsWith(searchQuery.trim(), ignoreCase = true) } }.forEach { item ->
            CardBlock(panel, text, item.name, item.room + " · " + item.detail, Icons.Default.Devices, action = { onOpenRoom(item.room) }) {}
        }
    }
    RoomMenu(rooms, selectedRoom, panel, text, { room -> onOpenRoom(room) }, listOf("Ohne Raum" to { onOpenRoom("Nicht zugeordnet") }))
    WeatherForecastCard(city = weatherCity, panel = panel, text = text)
    MovaHomeTile(serverUrl = serverUrl, connected = connected, client = ioClient, panel = panel, text = text, onOpen = onOpenMova)
    RoidmiHomeTile(serverUrl = serverUrl, connected = connected, client = ioClient, panel = panel, text = text, onOpen = onOpenRoidmi)
    CardBlock(panel, text, "Sicherheit", "Wassermelder Küche", Icons.Default.WaterDrop, action = { onTab(Tab.SAFETY) }) {
        if (floodAlarm) Text("WASSERALARM – Wasser erkannt!", color = Color(0xFFD62828), fontWeight = FontWeight.Bold)
        else if (floodKnown) Text("Letzter gemeldeter Zustand: kein Wasser erkannt.", color = Green, fontSize = 12.sp)
        else Text("Status unbekannt – noch keine gültige Sensormeldung.", color = Muted, fontSize = 12.sp)
        Text(if (floodBatteryKnown) "Batterie: ${floodBattery}%" else "Batteriestand unbekannt", color = Muted, fontSize = 11.sp)
    }
    
}

@Composable
private fun FavoritesScreen(items: List<DeviceItem>, favoriteIds: Set<String>, favoriteOrder: List<String>, panel: Color, text: Color,
    onToggle: (DeviceItem) -> Unit, onMove: (String, Int) -> Unit, onSwitch: (DeviceItem, Boolean) -> Unit, onEdit: (DeviceItem) -> Unit) {
    Text("Favoriten", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text)
    val byId = items.filter { it.stateId != null && it.stateId in favoriteIds }.associateBy { it.stateId!! }
    val ids = favoriteOrder.filter { it in byId.keys } + (byId.keys - favoriteOrder.toSet()).sorted()
    if (ids.isEmpty()) Text("Noch keine Favoriten. Öffne die Gerätebearbeitung und tippe auf den Stern.", color = Muted)
    ids.forEachIndexed { index, id ->
        val item = byId[id] ?: return@forEachIndexed
        CardBlock(panel, text, item.name, item.room + " · " + item.detail, Icons.Default.Star, action = { onEdit(item) }, actionIcon = Icons.Default.Edit) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (item.initialOn) "Eingeschaltet" else "Ausgeschaltet", color = if (item.initialOn) Green else Muted, modifier = Modifier.weight(1f), fontSize = 12.sp)
                if (item.writable) Switch(checked = item.initialOn, onCheckedChange = { onSwitch(item, it) })
                IconButton(onClick = { onMove(id, -1) }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, "Nach oben") }
                IconButton(onClick = { onMove(id, 1) }, enabled = index < ids.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Nach unten") }
                IconButton(onClick = { onToggle(item) }) { Icon(Icons.Default.Star, "Favorit entfernen", tint = Blue) }
            }
        }
    }
}

@Composable
private fun EnergyScreen(items: List<DeviceItem>, price: String, onPrice: (String) -> Unit, panel: Color, text: Color) {
    Text("Energie", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text)
    val measured = items.filter { it.powerId != null && it.powerWatts != null }
    val watts = measured.sumOf { it.powerWatts ?: 0.0 }
    CardBlock(panel, text, "Aktuelle Gesamtleistung", "Verfügbare Messwerte", Icons.Default.Bolt) {
        Text(String.format(java.util.Locale.GERMANY, "%.1f W", watts), fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Blue)
        Text("${measured.size} Geräte mit aktuellem Leistungswert", color = Muted, fontSize = 12.sp)
    }
    if (measured.isEmpty()) Text("Aktuell sind keine echten Leistungswerte verfügbar.", color = Muted)
    items.filter { it.powerId != null }.forEach { item ->
        CardBlock(panel, text, item.name, item.room, Icons.Default.Power) {
            Text(item.powerWatts?.let { String.format(java.util.Locale.GERMANY, "%.1f W", it) } ?: "Leistungswert nicht verfügbar", color = text, fontSize = 15.sp)
            val energy = item.energyValue
            if (energy != null) {
                val unit = item.energyUnit?.ifBlank { "Wh" } ?: "Wh"
                val kwh = when (unit.lowercase()) { "kwh" -> energy; "wh" -> energy / 1000.0; else -> null }
                val energyText = if (unit.equals("kWh", true)) String.format(java.util.Locale.GERMANY, "%.2f kWh", energy) else String.format(java.util.Locale.GERMANY, "%.0f %s", energy, unit)
                Text("Gesamtverbrauch: " + energyText, color = Muted, fontSize = 12.sp)
            } else Text("Gesamtverbrauch: kein Energiedatenpunkt verfügbar", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SummaryCard(modifier: Modifier, title: String, value: String, icon: ImageVector, panel: Color, text: Color, accent: Color) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(accent.copy(alpha = if (panel == Color.White) 0.22f else 0.30f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text(title, fontSize = 12.sp, color = Muted)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = text)
    }
}

@Composable
private fun CardBlock(panel: Color, text: Color, title: String, subtitle: String, icon: ImageVector, action: (() -> Unit)? = null, actionIcon: ImageVector = Icons.Default.ChevronRight, content: @Composable ColumnScope.() -> Unit) {
    val accent = when (icon) {
        Icons.Default.WaterDrop -> Color(0xFF159A9C)
        Icons.Default.Lightbulb -> Color(0xFFE6A323)
        Icons.Default.Power, Icons.Default.Devices -> Color(0xFF3984D6)
        Icons.Default.HomeWork, Icons.Default.Weekend, Icons.Default.Kitchen -> Color(0xFF7B61C9)
        Icons.Default.Cloud -> Color(0xFF398FBA)
        Icons.Default.Palette -> Color(0xFF398FBA)
        Icons.Default.Shield -> Color(0xFF2E9B70)
        Icons.Default.VisibilityOff -> Color(0xFF7A8797)
        Icons.Default.RoomPreferences -> Color(0xFFE29A25)
        else -> Blue
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(accent.copy(alpha = if (panel == Color.White) 0.20f else 0.30f)).padding(horizontal = 9.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(31.dp).clip(RoundedCornerShape(10.dp)).background(accent), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = Muted, fontSize = 10.sp)
            }
            if (action != null) IconButton(onClick = action, modifier = Modifier.size(32.dp)) { Icon(actionIcon, null, tint = Blue, modifier = Modifier.size(18.dp)) }
        }
        content()
    }
}

@Composable
private fun DeviceRow(item: DeviceItem, panel: Color, text: Color, compact: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
            .background(if (item.initialOn) Color(0xFF1769AA).copy(alpha = 0.07f) else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = if (compact) 7.dp else 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(if (item.initialOn) Color(0xFFDCEBFA) else if (panel == Color.White) Color(0xFFF0F3F7) else Color(0xFF293747)), contentAlignment = Alignment.Center) {
            Icon(when (item.kind) { "light" -> Icons.Default.Lightbulb; "plug" -> Icons.Default.Power; "relay" -> Icons.Default.ToggleOn; else -> Icons.Default.Devices }, null, tint = Blue)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, color = text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(item.subtitle, color = Muted, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (item.initialOn) "Ein" else "Aus", color = if (item.initialOn) Green else Muted, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            if (item.detail.isNotBlank()) Text(item.detail, color = Muted, fontSize = 10.sp)
        }
        Switch(checked = item.initialOn, onCheckedChange = { onClick() })
    }
}

@Composable
private fun LightsScreen(
    lights: List<DeviceItem>, rooms: List<String>, panel: Color, text: Color, connected: Boolean,
    selectedRoom: String?, onSelectRoom: (String) -> Unit,
    onToggle: (DeviceItem, Boolean) -> Unit,
    onEditDevice: (DeviceItem) -> Unit,
    onSetNumeric: (DeviceItem, String, Double) -> Unit,
    onSetDefaultBrightness: (DeviceItem, Int) -> Unit,
    defaultBrightness: (DeviceItem) -> Int
) {
    Text("Licht", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text)
    RoomMenu(rooms, selectedRoom, panel, text, onSelectRoom)
    if (!connected) Text("Bitte zuerst unter Einstellungen verbinden.", color = Muted)
    else if (lights.isEmpty()) Text("Keine schaltbaren Hue-Zustände gefunden. Prüfe die Hue-Objekte unter hue.0.", color = Muted)
    val visibleLights = lights.filter { selectedRoom == null || it.room == selectedRoom }
    val grouped = visibleLights.groupBy { it.room.ifBlank { "Nicht zugeordnet" } }
    val roomOrder = (rooms.filter { it != "Nicht zugeordnet" } + grouped.keys.filter { it !in rooms && it != "Nicht zugeordnet" }).distinct()
    roomOrder.filter { grouped[it].orEmpty().isNotEmpty() }.forEachIndexed { roomIndex, room ->
        val roomItems = grouped[room].orEmpty().sortedBy { it.name.lowercase() }
        val accent = when (room.lowercase()) {
            "wohnzimmer" -> Color(0xFF6C63C7)
            "küche" -> Color(0xFFE09A22)
            "schlafzimmer" -> Color(0xFF4787C7)
            "bad", "badezimmer" -> Color(0xFF159A9C)
            "hauswirtschaft" -> Color(0xFF2E9B70)
            "nicht zugeordnet" -> Color(0xFFD97706)
            else -> listOf(Color(0xFF6C63C7), Color(0xFF159A9C), Color(0xFFE09A22), Color(0xFF4787C7))[roomIndex % 4]
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(accent.copy(alpha = if (panel == Color.White) 0.10f else 0.20f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(accent), contentAlignment = Alignment.Center) {
                    Icon(if (room == "Nicht zugeordnet") Icons.Default.WarningAmber else Icons.Default.HomeWork, null, tint = Color.White)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(room, color = text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("${roomItems.size} ${if (roomItems.size == 1) "Lampe" else "Lampen"}", color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            roomItems.forEach { item ->
                var expanded by remember(item.stateId) { mutableStateOf(false) }
                var brightnessPercent by remember(item.stateId) { mutableFloatStateOf(item.currentBrightness?.toFloat() ?: 100f) }
                var turnOnPercent by remember(item.stateId) { mutableIntStateOf(defaultBrightness(item)) }
                var temp by remember(item.stateId) { mutableFloatStateOf(item.currentColorTemp?.let { ((it - 153.0) / 347.0 * 100.0).toFloat().coerceIn(1f, 100f) } ?: 50f) }
                var hue by remember(item.stateId) { mutableFloatStateOf(item.currentHue?.let { (it / 65535.0 * 360.0).toFloat().coerceIn(0f, 360f) } ?: 180f) }
                var saturation by remember(item.stateId) { mutableFloatStateOf(item.currentSaturation?.let { (it / 254.0 * 100.0).toFloat().coerceIn(0f, 100f) } ?: 100f) }
                LaunchedEffect(item.currentBrightness) { item.currentBrightness?.let { brightnessPercent = it.toFloat().coerceIn(1f, 100f) } }
                LaunchedEffect(item.currentColorTemp) { item.currentColorTemp?.let { temp = ((it - 153.0) / 347.0 * 100.0).toFloat().coerceIn(1f, 100f) } }
                LaunchedEffect(item.currentHue) { item.currentHue?.let { hue = (it / 65535.0 * 360.0).toFloat().coerceIn(0f, 360f) } }
                LaunchedEffect(item.currentSaturation) { item.currentSaturation?.let { saturation = (it / 254.0 * 100.0).toFloat().coerceIn(0f, 100f) } }
                CardBlock(panel, text, item.name, "", Icons.Default.Lightbulb, action = { onEditDevice(item) }, actionIcon = Icons.Default.Edit) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(if (item.initialOn) "Eingeschaltet" else "Ausgeschaltet", color = if (item.initialOn) Green else text); Text(item.room, color = Muted, fontSize = 11.sp) }
                        Switch(checked = item.initialOn, enabled = connected && item.writable && item.stateId != null, onCheckedChange = { onToggle(item, it) })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Einklappen" else "Erweitern")
                            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        }
                    }
                    if (expanded) {
                    if (item.brightnessId != null) {
                        if (item.currentBrightness == null) {
                            Text("Aktuelle Helligkeit wird geladen …", color = Muted, fontSize = 12.sp)
                        } else {
                            Text("Aktuelle Helligkeit: ${brightnessPercent.toInt()}%", color = text, fontWeight = FontWeight.SemiBold)
                            Slider(value = brightnessPercent, onValueChange = { brightnessPercent = it }, valueRange = 1f..100f, enabled = connected,
                                onValueChangeFinished = { onSetNumeric(item, item.brightnessId, brightnessPercent.toDouble() * 2.54) })
                        }
                        Text("Helligkeit beim Einschalten: ${turnOnPercent}%", color = text, fontWeight = FontWeight.SemiBold)
                        Slider(value = turnOnPercent.toFloat(), onValueChange = { turnOnPercent = it.toInt() }, valueRange = 1f..100f, enabled = connected,
                            onValueChangeFinished = { onSetDefaultBrightness(item, turnOnPercent); if (!item.initialOn) onSetNumeric(item, item.brightnessId, turnOnPercent.toDouble() * 2.54) })
                    }
                    if (item.colorTempId != null || (item.hueId != null && item.saturationId != null)) {
                        var colorMenuExpanded by remember(item.stateId) { mutableStateOf(false) }
                        val colorPresets = listOf<Triple<String, Double?, Double?>>(
                            Triple("Warmweiß", null, 450.0),
                            Triple("Neutralweiß", null, 285.0),
                            Triple("Kaltweiß", null, 170.0),
                            Triple("Rot", 0.0, null),
                            Triple("Orange", 6000.0, null),
                            Triple("Gelb", 11000.0, null),
                            Triple("Grün", 26000.0, null),
                            Triple("Türkis", 34000.0, null),
                            Triple("Blau", 44000.0, null),
                            Triple("Violett", 50000.0, null),
                            Triple("Pink", 60000.0, null)
                        ).filter { preset ->
                            (preset.second == null || (item.hueId != null && item.saturationId != null)) &&
                            (preset.third == null || item.colorTempId != null)
                        }
                        Box {
                            OutlinedButton(onClick = { colorMenuExpanded = true }, enabled = connected && colorPresets.isNotEmpty()) {
                                Icon(Icons.Default.Palette, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Lichtfarbe auswählen")
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = colorMenuExpanded, onDismissRequest = { colorMenuExpanded = false }) {
                                colorPresets.forEach { preset ->
                                    DropdownMenuItem(
                                        text = { Text(preset.first) },
                                        onClick = {
                                            colorMenuExpanded = false
                                            val temperatureValue = preset.third
                                            val hueValue = preset.second
                                            if (temperatureValue != null && item.colorTempId != null) {
                                                onSetNumeric(item, item.colorTempId, temperatureValue)
                                            }
                                            if (hueValue != null && item.hueId != null && item.saturationId != null) {
                                                onSetNumeric(item, item.hueId, hueValue)
                                                onSetNumeric(item, item.saturationId, 254.0)
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (preset.third != null) Icons.Default.LightMode else Icons.Default.Circle,
                                                contentDescription = null,
                                                tint = when (preset.first) {
                                                    "Warmweiß" -> Color(0xFFFFB74D)
                                                    "Neutralweiß" -> Color(0xFFFFF3D0)
                                                    "Kaltweiß" -> Color(0xFFB3D9FF)
                                                    "Rot" -> Color(0xFFE53935)
                                                    "Orange" -> Color(0xFFFF9800)
                                                    "Gelb" -> Color(0xFFFFD600)
                                                    "Grün" -> Color(0xFF43A047)
                                                    "Türkis" -> Color(0xFF00ACC1)
                                                    "Blau" -> Color(0xFF1E88E5)
                                                    "Violett" -> Color(0xFF8E24AA)
                                                    else -> Color(0xFFEC407A)
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (item.colorTempId != null) {
                        val currentMired = item.currentColorTemp
                        Text(
                            if (currentMired != null && currentMired > 0.0) "Aktuelle Farbtemperatur: ${(1_000_000.0 / currentMired).toInt()} K"
                            else "Aktuelle Farbtemperatur wird geladen …",
                            color = if (currentMired != null && currentMired > 0.0) text else Muted,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("Farbtemperatur einstellen", color = text, fontWeight = FontWeight.SemiBold)
                        if (currentMired != null) {
                            Slider(value = temp, onValueChange = { temp = it }, valueRange = 1f..100f, enabled = connected,
                                onValueChangeFinished = { onSetNumeric(item, item.colorTempId, 153.0 + temp.toDouble() / 100.0 * 347.0) })
                        }
                    }
                    if (item.hueId != null && item.saturationId != null) {
                        val hueAvailable = item.currentHue != null && item.currentSaturation != null
                        if (hueAvailable) {
                            val previewColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(
                                hue.coerceIn(0f, 360f),
                                (saturation / 100f).coerceIn(0f, 1f),
                                (brightnessPercent / 100f).coerceIn(0.05f, 1f)
                            )))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(Modifier.size(28.dp).clip(CircleShape).background(previewColor))
                                Column {
                                    Text("Aktuelle Farbe", color = text, fontWeight = FontWeight.SemiBold)
                                    Text("Farbton ${hue.toInt()}° · Sättigung ${saturation.toInt()}%", color = Muted, fontSize = 12.sp)
                                }
                            }
                        } else {
                            Text("Aktuelle Farbe wird geladen …", color = Muted, fontSize = 12.sp)
                        }
                        Text("Farbton einstellen", color = text, fontWeight = FontWeight.SemiBold)
                        if (hueAvailable) {
                            Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f, enabled = connected,
                                onValueChangeFinished = { onSetNumeric(item, item.hueId, hue.toDouble() / 360.0 * 65535.0) })
                            Text("Farbsättigung einstellen", color = text, fontWeight = FontWeight.SemiBold)
                            Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..100f, enabled = connected,
                                onValueChangeFinished = { onSetNumeric(item, item.saturationId, saturation.toDouble() * 2.54) })
                        }
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevicesScreen(devices: List<DeviceItem>, rooms: List<String>, panel: Color, text: Color, connected: Boolean, selectedRoom: String?, onSelectRoom: (String) -> Unit, onToggle: (DeviceItem, Boolean) -> Unit, onEditDevice: (DeviceItem) -> Unit) {
    val visibleDevices = devices.filter { selectedRoom == null || it.room == selectedRoom }
    val unassigned = devices.filter { it.room.isBlank() || it.room == "Nicht zugeordnet" }
    Text("Geräte", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text)
    RoomMenu(rooms, selectedRoom, panel, text, onSelectRoom)
    if (selectedRoom == "Nicht zugeordnet") {
        CardBlock(panel, text, "Noch keinem Raum zugeordnet", "${unassigned.size} ${if (unassigned.size == 1) "Gerät" else "Geräte"}", Icons.Default.RoomPreferences) {
            if (unassigned.isEmpty()) {
                Text("Alle Geräte haben bereits einen Raum.", color = Green, fontSize = 13.sp)
            } else {
                Text("Diese Geräte brauchen noch einen Raum:", color = Muted, fontSize = 12.sp)
                unassigned.forEach { item ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF1D6)).clickable { onEditDevice(item) }.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WarningAmber, null, tint = Color(0xFFB66A00))
                        Spacer(Modifier.width(8.dp))
                        Text(item.name, color = Color(0xFF754500), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.Edit, null, tint = Color(0xFFB66A00))
                    }
                }
                Text("Tippe auf ein Gerät, um es zuzuweisen.", color = Muted, fontSize = 11.sp)
            }
        }
    }
    if (!connected) Text("Bitte zuerst unter Einstellungen verbinden.", color = Muted)
    else if (devices.isEmpty()) Text("Noch keine Shelly-Geräte gefunden.", color = Muted)
    val grouped = visibleDevices.groupBy { it.room.ifBlank { "Nicht zugeordnet" } }
    val roomOrder = (rooms.filter { it != "Nicht zugeordnet" } + grouped.keys.filter { it !in rooms && it != "Nicht zugeordnet" }).distinct()
    roomOrder.filter { grouped[it].orEmpty().isNotEmpty() }.forEachIndexed { roomIndex, room ->
        val roomItems = grouped[room].orEmpty().sortedBy { it.name.lowercase() }
        val roomAccent = when (room.lowercase()) {
            "wohnzimmer" -> Color(0xFF6C63C7)
            "küche" -> Color(0xFFE09A22)
            "schlafzimmer" -> Color(0xFF4787C7)
            "bad", "badezimmer" -> Color(0xFF159A9C)
            "hauswirtschaft" -> Color(0xFF2E9B70)
            "nicht zugeordnet" -> Color(0xFFD97706)
            else -> listOf(Color(0xFF6C63C7), Color(0xFF159A9C), Color(0xFFE09A22), Color(0xFF4787C7))[roomIndex % 4]
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(roomAccent.copy(alpha = if (panel == Color.White) 0.10f else 0.20f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(roomAccent), contentAlignment = Alignment.Center) {
                    Icon(if (room == "Nicht zugeordnet") Icons.Default.WarningAmber else Icons.Default.HomeWork, null, tint = Color.White)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(room, color = text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("${roomItems.size} ${if (roomItems.size == 1) "Gerät" else "Geräte"}", color = roomAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            roomItems.forEach { item ->
                CardBlock(panel, text, item.name, item.subtitle, when { item.name == "Wasser" -> Icons.Default.WaterDrop; item.kind == "light" -> Icons.Default.Lightbulb; item.kind == "device" -> Icons.Default.Devices; else -> Icons.Default.Power }, action = { onEditDevice(item) }, actionIcon = Icons.Default.Edit) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(if (item.initialOn) "Eingeschaltet" else "Ausgeschaltet", color = if (item.initialOn) Green else text, fontWeight = FontWeight.SemiBold)
                            if (item.powerWatts != null) Text("Leistung: ${"%.1f".format(item.powerWatts)} W", color = Muted, fontSize = 12.sp)
                            Text(item.stateId ?: "", color = Muted, fontSize = 9.sp)
                            if (!item.writable) Text("Schalten aus Sicherheitsgründen gesperrt", color = Muted, fontSize = 10.sp)
                        }
                        Switch(checked = item.initialOn, enabled = connected && item.writable && item.stateId != null, onCheckedChange = { onToggle(item, it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomsScreen(rooms: List<String>, devices: List<DeviceItem>, lights: List<DeviceItem>, panel: Color, text: Color, connected: Boolean,
    onToggle: (DeviceItem, Boolean) -> Unit, onSetNumeric: (DeviceItem, String, Double) -> Unit,
    onSetDefaultBrightness: (DeviceItem, Int) -> Unit, defaultBrightness: (DeviceItem) -> Int,
    onSelectRoom: (String?) -> Unit, onAddRoom: () -> Unit, onRenameRoom: (String, String) -> Unit,
    selectedRoom: String?, onEditDevice: (DeviceItem) -> Unit,
    hiddenItems: List<DeviceItem>, onShowHidden: () -> Unit,
    onRequestDeleteRoom: (String) -> Unit) {
    var renameRoom by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    val activeRoom = selectedRoom ?: rooms.firstOrNull()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Räume", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text)
        IconButton(onClick = onAddRoom, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.AddCircle, contentDescription = "Raum hinzufügen", tint = Blue, modifier = Modifier.size(23.dp))
        }
    }
    RoomMenu(rooms, activeRoom, panel, text, { onSelectRoom(it) }, listOf(
        "Ohne Raum" to { onSelectRoom("Nicht zugeordnet") }
    ))
    if (activeRoom != null) {
        Text(activeRoom, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { renameRoom = activeRoom; renameValue = activeRoom }) { Text("Umbenennen") }
            if (activeRoom != "Nicht zugeordnet") TextButton(onClick = { onRequestDeleteRoom(activeRoom) }) { Text("Löschen") }
        }
        val roomItems = if (activeRoom == "Nicht zugeordnet") (devices + lights).filter { it.room.isBlank() || it.room == "Nicht zugeordnet" }.distinctBy { it.stateId ?: it.name } else (devices + lights).filter { it.room == activeRoom }.distinctBy { it.stateId ?: it.name }
        val lamps = roomItems.filter { it.kind == "light" }
        val switches = roomItems.filter { it.kind in listOf("plug", "relay", "switch") }
        val otherItems = roomItems.filter { it.kind !in listOf("light", "plug", "relay", "switch") }
        if (roomItems.isEmpty()) Text("Noch keine Geräte zugeordnet.", color = Muted)
        if (lamps.isNotEmpty()) {
            Text("Lampen", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = text)
            lamps.forEach { item ->
                var expanded by remember(item.stateId) { mutableStateOf(false) }
                var brightness by remember(item.stateId) { mutableFloatStateOf(item.currentBrightness?.toFloat() ?: 100f) }
                var turnOnPercent by remember(item.stateId) { mutableIntStateOf(defaultBrightness(item)) }
                var temp by remember(item.stateId) { mutableFloatStateOf(item.currentColorTemp?.let { ((it - 153.0) / 347.0 * 100.0).toFloat().coerceIn(1f, 100f) } ?: 50f) }
                var hue by remember(item.stateId) { mutableFloatStateOf(item.currentHue?.let { (it / 65535.0 * 360.0).toFloat().coerceIn(0f, 360f) } ?: 180f) }
                var saturation by remember(item.stateId) { mutableFloatStateOf(item.currentSaturation?.let { (it / 254.0 * 100.0).toFloat().coerceIn(0f, 100f) } ?: 100f) }
                LaunchedEffect(item.currentBrightness) { item.currentBrightness?.let { brightness = it.toFloat().coerceIn(1f, 100f) } }
                LaunchedEffect(item.currentColorTemp) { item.currentColorTemp?.let { temp = ((it - 153.0) / 347.0 * 100.0).toFloat().coerceIn(1f, 100f) } }
                LaunchedEffect(item.currentHue) { item.currentHue?.let { hue = (it / 65535.0 * 360.0).toFloat().coerceIn(0f, 360f) } }
                LaunchedEffect(item.currentSaturation) { item.currentSaturation?.let { saturation = (it / 254.0 * 100.0).toFloat().coerceIn(0f, 100f) } }
                CardBlock(panel, text, item.name, "", Icons.Default.Lightbulb, action = { onEditDevice(item) }, actionIcon = Icons.Default.Edit) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (item.initialOn) "Eingeschaltet" else "Ausgeschaltet", color = if (item.initialOn) Green else Muted, modifier = Modifier.weight(1f))
                        Switch(checked = item.initialOn, enabled = connected && item.writable && item.stateId != null, onCheckedChange = { onToggle(item, it) })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { expanded = !expanded }) {
                            Text(if (expanded) "Einklappen" else "Erweitern")
                            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        }
                    }
                    if (expanded) {
                        if (item.brightnessId != null) {
                            Text(if (item.currentBrightness != null) "Aktuelle Helligkeit: ${brightness.toInt()}%" else "Aktuelle Helligkeit wird geladen …", color = if (item.currentBrightness != null) text else Muted, fontWeight = FontWeight.SemiBold)
                            if (item.currentBrightness != null) Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 1f..100f, enabled = connected,
                                onValueChangeFinished = { onSetNumeric(item, item.brightnessId, brightness.toDouble() * 2.54) })
                            Text("Helligkeit beim Einschalten: ${turnOnPercent}%", color = text, fontWeight = FontWeight.SemiBold)
                            Slider(value = turnOnPercent.toFloat(), onValueChange = { turnOnPercent = it.toInt() }, valueRange = 1f..100f, enabled = connected,
                                onValueChangeFinished = { onSetDefaultBrightness(item, turnOnPercent); if (!item.initialOn) onSetNumeric(item, item.brightnessId, turnOnPercent.toDouble() * 2.54) })
                        }
                        if (item.colorTempId != null) {
                            Text(if (item.currentColorTemp != null && item.currentColorTemp > 0.0) "Aktuelle Farbtemperatur: ${(1_000_000.0 / item.currentColorTemp).toInt()} K" else "Farbtemperatur wird geladen …", color = text, fontWeight = FontWeight.SemiBold)
                            Text("Farbtemperatur einstellen", color = text, fontWeight = FontWeight.SemiBold)
                            if (item.currentColorTemp != null) Slider(value = temp, onValueChange = { temp = it }, valueRange = 1f..100f, enabled = connected,
                                onValueChangeFinished = { onSetNumeric(item, item.colorTempId, 153.0 + temp.toDouble() / 100.0 * 347.0) })
                        }
                        if (item.hueId != null && item.saturationId != null) {
                            Text("Farbton einstellen: ${hue.toInt()}°", color = text, fontWeight = FontWeight.SemiBold)
                            Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f, enabled = connected,
                                onValueChangeFinished = { onSetNumeric(item, item.hueId, hue.toDouble() / 360.0 * 65535.0) })
                            Text("Farbsättigung einstellen: ${saturation.toInt()}%", color = text, fontWeight = FontWeight.SemiBold)
                            Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..100f, enabled = connected,
                                onValueChangeFinished = { onSetNumeric(item, item.saturationId, saturation.toDouble() * 2.54) })
                        }
                    }
                }
            }
        }
        if (switches.isNotEmpty()) {
            Text("Schalter & Steckdosen", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = text)
            switches.forEach { item ->
                CardBlock(panel, text, item.name, "", Icons.Default.Power, action = { onEditDevice(item) }, actionIcon = Icons.Default.Edit) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (item.initialOn) "Eingeschaltet" else "Ausgeschaltet", color = if (item.initialOn) Green else Muted, modifier = Modifier.weight(1f))
                        Switch(
                            checked = item.initialOn,
                            enabled = connected && item.writable && item.stateId != null,
                            onCheckedChange = { onToggle(item, it) }
                        )
                    }
                    if (item.powerWatts != null) {
                        Text(
                            "Aktuelle Leistungsaufnahme: " + "%.1f".format(java.util.Locale.GERMANY, item.powerWatts) + " W",
                            color = Muted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
        if (otherItems.isNotEmpty()) {
            Text("Sonstiges", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = text)
            otherItems.forEach { item ->
                CardBlock(panel, text, item.name, "", Icons.Default.Devices, action = { onEditDevice(item) }, actionIcon = Icons.Default.Edit) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.detail.ifBlank { item.kind }, color = Muted, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
    if (renameRoom != null) AlertDialog(onDismissRequest = { renameRoom = null }, title = { Text("Raum umbenennen") }, text = { OutlinedTextField(value = renameValue, onValueChange = { renameValue = it }, singleLine = true, label = { Text("Name") }, trailingIcon = { InputConfirmIcon() }) }, confirmButton = { TextButton(onClick = { onRenameRoom(renameRoom!!, renameValue); renameRoom = null }) { Text("Speichern") } }, dismissButton = { TextButton(onClick = { renameRoom = null }) { Text("Abbrechen") } })
}


private fun writeHiddenItems(items: List<DeviceItem>): String {
    val array = JSONArray()
    items.forEach { item ->
        val obj = JSONObject()
        obj.put("name", item.name); obj.put("subtitle", item.subtitle); obj.put("kind", item.kind)
        obj.put("initialOn", item.initialOn); obj.put("detail", item.detail); obj.put("stateId", item.stateId)
        obj.put("powerId", item.powerId); obj.put("writable", item.writable); obj.put("powerWatts", item.powerWatts); obj.put("room", item.room)
        obj.put("energyId", item.energyId); obj.put("energyUnit", item.energyUnit); obj.put("energyValue", item.energyValue)
        obj.put("brightnessId", item.brightnessId); obj.put("colorTempId", item.colorTempId); obj.put("hueId", item.hueId); obj.put("saturationId", item.saturationId)
        array.put(obj)
    }
    return array.toString()
}

private fun readHiddenItems(raw: String?): List<DeviceItem> = try {
    val array = JSONArray(raw ?: "[]")
    (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        DeviceItem(o.optString("name"), o.optString("subtitle"), o.optString("kind", "plug"), o.optBoolean("initialOn"), o.optString("detail"), o.optString("stateId").takeIf { it.isNotBlank() && it != "null" }, o.optString("powerId").takeIf { it.isNotBlank() && it != "null" }, o.optBoolean("writable", true), if (o.has("powerWatts") && !o.isNull("powerWatts")) o.optDouble("powerWatts") else null, o.optString("room", "Nicht zugeordnet"), o.optString("brightnessId").takeIf { it.isNotBlank() && it != "null" }, o.optString("colorTempId").takeIf { it.isNotBlank() && it != "null" }, o.optString("hueId").takeIf { it.isNotBlank() && it != "null" }, o.optString("saturationId").takeIf { it.isNotBlank() && it != "null" },
            energyId = o.optString("energyId").takeIf { it.isNotBlank() && it != "null" },
            energyUnit = o.optString("energyUnit").takeIf { it.isNotBlank() && it != "null" },
            energyValue = if (o.has("energyValue") && !o.isNull("energyValue")) o.optDouble("energyValue") else null)
    }
} catch (_: Exception) { emptyList() }

private fun readStringMap(raw: String?): Map<String, String> = try {
    val obj = JSONObject(raw ?: "{}"); val result = mutableMapOf<String, String>(); val keys = obj.keys(); while (keys.hasNext()) { val key = keys.next().toString(); result[key] = obj.optString(key) }; result
} catch (_: Exception) { emptyMap() }

private fun updateDeviceState(devices: MutableList<DeviceItem>, lights: MutableList<DeviceItem>, id: String, state: Boolean) {
    val d = devices.indexOfFirst { it.stateId == id }
    if (d >= 0) devices[d] = devices[d].copy(initialOn = state, detail = if (state) "Ein" else "Aus")
    val l = lights.indexOfFirst { it.stateId == id }
    if (l >= 0) lights[l] = lights[l].copy(initialOn = state, detail = if (state) "Ein" else "Aus")
}

private fun updateNumericDeviceValue(devices: MutableList<DeviceItem>, lights: MutableList<DeviceItem>, id: String, field: String, value: Double) {
    fun update(item: DeviceItem): DeviceItem = when (field) {
        "brightness" -> item.copy(currentBrightness = value)
        "colorTemp" -> item.copy(currentColorTemp = value)
        "hue" -> item.copy(currentHue = value)
        "saturation" -> item.copy(currentSaturation = value)
        else -> item
    }
    val d = devices.indexOfFirst { it.stateId == id }
    if (d >= 0) devices[d] = update(devices[d])
    val l = lights.indexOfFirst { it.stateId == id }
    if (l >= 0) lights[l] = update(lights[l])
}

private fun updateDeviceMeasurement(devices: MutableList<DeviceItem>, lights: MutableList<DeviceItem>, id: String, update: (DeviceItem) -> DeviceItem) {
    val d = devices.indexOfFirst { it.stateId == id }
    if (d >= 0) devices[d] = update(devices[d])
    val l = lights.indexOfFirst { it.stateId == id }
    if (l >= 0) lights[l] = update(lights[l])
}

private fun refreshStates(client: IoBrokerClient, url: String, devices: MutableList<DeviceItem>, lights: MutableList<DeviceItem>) {
    (devices + lights).forEach { item ->
        val id = item.stateId ?: return@forEach
        client.getState(url, id) { state, _ -> if (state != null) updateDeviceState(devices, lights, id, state) }
        val brightnessId = item.brightnessId
        if (brightnessId != null) client.getNumericState(url, brightnessId) { value ->
            if (value != null) updateNumericDeviceValue(devices, lights, id, "brightness", value / 2.54)
        }
        val colorTempId = item.colorTempId
        if (colorTempId != null) client.getNumericState(url, colorTempId) { value ->
            if (value != null) updateNumericDeviceValue(devices, lights, id, "colorTemp", value)
        }
        val hueId = item.hueId
        if (hueId != null) client.getNumericState(url, hueId) { value ->
            if (value != null) updateNumericDeviceValue(devices, lights, id, "hue", value)
        }
        val saturationId = item.saturationId
        if (saturationId != null) client.getNumericState(url, saturationId) { value ->
            if (value != null) updateNumericDeviceValue(devices, lights, id, "saturation", value)
        }
        val powerId = item.powerId
        if (powerId != null) client.getNumericState(url, powerId) { watts ->
            if (watts != null) updateDeviceMeasurement(devices, lights, id) { it.copy(powerWatts = watts) }
        }
        val energyId = item.energyId
        if (energyId != null) client.getNumericState(url, energyId) { value ->
            if (value != null) updateDeviceMeasurement(devices, lights, id) { it.copy(energyValue = value) }
        }
    }
}

@Composable
private fun DeviceHealthScreen(items: List<DeviceItem>, connected: Boolean, connectionStatus: String, floodBattery: Int, floodBatteryKnown: Boolean, panel: Color, text: Color) {
    Text("Geräteprobleme", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text)
    val missingPower = items.filter { it.powerId != null && it.powerWatts == null }
    val hasAlerts = !connected || missingPower.isNotEmpty() || (floodBatteryKnown && floodBattery < 20)
    CardBlock(panel, text, if (hasAlerts) "Prüfung erforderlich" else "Keine bekannten Probleme", if (hasAlerts) "Es gibt Hinweise, die geprüft werden sollten." else "Verbindung und verfügbare Messwerte sehen unauffällig aus.", if (hasAlerts) Icons.Default.Warning else Icons.Default.CheckCircle) {
        Text(if (connected) "ioBroker-Verbindung: verbunden" else "ioBroker-Verbindung: fehlgeschlagen", color = if (connected) Green else Color(0xFFD62828), fontSize = 13.sp)
        if (!connected && connectionStatus.isNotBlank()) Text(connectionStatus, color = Muted, fontSize = 12.sp)
    }
    if (!connected) CardBlock(panel, text, "Verbindung prüfen", "HomePilot kann derzeit keine Daten abrufen", Icons.Default.WifiOff) {
        Text("Prüfe die ioBroker-Adresse und die Netzwerkverbindung. Bei Fernzugriff WireGuard manuell aktivieren.", color = text, fontSize = 13.sp)
    }
    if (missingPower.isNotEmpty()) CardBlock(panel, text, "Leistungsmessung fehlt", "Für diese Geräte ist ein Messdatenpunkt hinterlegt, liefert aber derzeit keinen Zahlenwert.", Icons.Default.Bolt) {
        missingPower.forEach { item -> Text("• ${item.name} · ${item.room}", color = text, fontSize = 13.sp) }
    }
    if (floodBatteryKnown && floodBattery < 20) CardBlock(panel, text, "Wassermelder-Batterie niedrig", "Batteriestand ${floodBattery}%", Icons.Default.BatteryAlert) {
        Text("Batterie des Shelly Flood zeitnah prüfen.", color = text, fontSize = 13.sp)
    }
    if (missingPower.isEmpty() && (connected || !connected) && !(floodBatteryKnown && floodBattery < 20)) CardBlock(panel, text, "Messwerte", "Keine fehlenden Leistungswerte erkannt", Icons.Default.CheckCircle) {
        Text("Es werden nur Hinweise angezeigt, die aus den tatsächlich vorhandenen Datenpunkten abgeleitet werden können.", color = Muted, fontSize = 12.sp)
    }
    Text("Ein fehlender Messwert bedeutet nicht automatisch, dass ein Gerät offline oder defekt ist. Geräte ohne zuverlässigen Online-Datenpunkt werden nicht pauschal als offline markiert.", color = Muted, fontSize = 12.sp)
}

@Composable
private fun SafetyScreen(alarm: Boolean, battery: Int, batteryKnown: Boolean, panel: Color, text: Color, known: Boolean, temperature: Double?) {
    Text("Sicherheit", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text)
    CardBlock(panel, text, "Wassermelder Küche", "Shelly Flood Gen1 · Daten aus ioBroker", Icons.Default.WaterDrop) {
        if (alarm) Text("WASSERALARM – Wasser erkannt!", color = Color(0xFFD62828), fontWeight = FontWeight.Bold)
        else if (known) Text("Letzter gemeldeter Zustand: kein Wasser erkannt.", color = Green)
        else Text("Status unbekannt – noch keine gültige Sensormeldung erhalten.", color = text)
        Text(if (batteryKnown) "Batterie: $battery%" else "Batteriestand noch nicht verfügbar", color = text)
        temperature?.let { Text("Temperatur: " + String.format(java.util.Locale.GERMANY, "%.1f °C", it), color = text) }
        Text("Der Sensor meldet sich nur gelegentlich. Ein fehlendes Online-Signal wird daher nicht als Defekt gewertet.", color = Muted, fontSize = 12.sp)
        if (alarm) Text("Die Android-Benachrichtigung wird beim Empfang des Alarms ausgelöst, solange HomePilot die Sensordaten abfragt.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun HomePilotAccountLoginScreen(
    registry: HomePilotAccountRegistry?, username: String, onUsername: (String) -> Unit,
    password: String, onPassword: (String) -> Unit, passwordConfirmation: String, onPasswordConfirmation: (String) -> Unit,
    displayName: String, onDisplayName: (String) -> Unit,
    loading: Boolean, status: String, onReload: () -> Unit, onLogin: () -> Unit
) {
    val setup = registry?.accounts?.isEmpty() == true
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Home, contentDescription = null, tint = Blue, modifier = Modifier.size(58.dp))
        Spacer(Modifier.height(12.dp))
        Text("HomePilot", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(if (registry == null) "HomePilot-Konten werden geladen …" else if (setup) "Ersteinrichtung · Administrator erstellen" else "Mit deinem HomePilot-Konto anmelden", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        if (registry == null) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Button(onClick = onReload, enabled = !loading) { Text("Konten erneut laden") }
        } else {
            OutlinedTextField(value = username, onValueChange = onUsername, label = { Text("HomePilot-Benutzername") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (setup) { Spacer(Modifier.height(8.dp)); OutlinedTextField(value = displayName, onValueChange = onDisplayName, label = { Text("Anzeigename (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = password, onValueChange = onPassword, label = { Text(if (setup) "Passwort (mind. 8 Zeichen)" else "HomePilot-Passwort") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            if (setup) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = passwordConfirmation, onValueChange = onPasswordConfirmation, label = { Text("Passwort wiederholen") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                if (passwordConfirmation.isNotEmpty() && password != passwordConfirmation) {
                    Text("Die Passwörter stimmen nicht überein.", color = Color(0xFFD32F2F), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(onClick = onLogin, enabled = !loading && username.isNotBlank() && password.isNotBlank() && (!setup || (passwordConfirmation.isNotBlank() && password == passwordConfirmation)), modifier = Modifier.fillMaxWidth()) { Text(if (setup) "Administrator erstellen" else "Anmelden") }
        }
        if (status.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(8.dp))
        Text("ioBroker-Verbindung und HomePilot-Konto sind getrennte Anmeldungen.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun HomePilotLoginScreen(
    status: String, loading: Boolean, onLogin: () -> Unit
) {
    val statusError = status.contains("fehlgeschlagen", ignoreCase = true) ||
        status.contains("nicht authentifiziert", ignoreCase = true) || status.contains("Fehler", ignoreCase = true)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Home, contentDescription = null, tint = Blue, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(14.dp))
        Text("HomePilot", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text("Verbindung zu ioBroker wird ohne Benutzerkonto hergestellt.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        Text("WebSocket: ws://192.168.178.54:8084", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        if (status != "Nicht verbunden" && status.isNotBlank()) {
            Text(status, color = if (statusError) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = onLogin, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Login, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (loading) "Verbindung läuft …" else "Mit ioBroker verbinden")
        }
        Spacer(Modifier.height(10.dp))
        Text("Danach meldest du dich mit deinem HomePilot-Konto an.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SettingsScreen(dark: Boolean, onDark: (Boolean) -> Unit, panel: Color, text: Color, onBack: () -> Unit,
    serverUrl: String, onServerUrl: (String) -> Unit, weatherCity: String, onWeatherCity: (String) -> Unit,
    status: String, connected: Boolean, onConnect: () -> Unit,
    onSearchDevices: () -> Unit, onOpenLights: () -> Unit, onOpenDevices: () -> Unit, onOpenRooms: () -> Unit, onOpenHealth: () -> Unit, onShowHidden: () -> Unit,
    inactivityMinutes: Int, onInactivityMinutes: (Int) -> Unit, onBatterySettings: () -> Unit,
    ioBrokerUsername: String, onIoBrokerUsername: (String) -> Unit, ioBrokerPassword: String, onIoBrokerPassword: (String) -> Unit, onSaveCredentials: () -> Unit, onSwitchUser: () -> Unit, homePilotAccount: HomePilotAccount?, homePilotAccounts: List<HomePilotAccount>, onHomePilotSwitch: () -> Unit, onAddHomePilotAccount: (String, String, String, String) -> Unit, onResetHomePilotPassword: (String, String) -> Unit, onDeleteHomePilotAccount: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Zurück", tint = Blue)
        }
        Text("Einstellungen", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text)
    }
    CardBlock(panel, text, "Darstellung", "Helles Design standardmäßig", Icons.Default.Palette) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Dunkles Design", color = text, fontWeight = FontWeight.SemiBold); Text("Kann jederzeit umgeschaltet werden", color = Muted, fontSize = 12.sp) }
            Switch(checked = dark, onCheckedChange = onDark)
        }
    }
    CardBlock(panel, text, "Geräte-Einstellungen", "Geräte suchen und Bedienseiten öffnen", Icons.Default.Tune) {
        Button(onClick = onSearchDevices, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Geräte suchen") }
        Button(onClick = onOpenLights, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Lightbulb, null); Spacer(Modifier.width(8.dp)); Text("Lichtseite") }
        Button(onClick = onOpenDevices, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Devices, null); Spacer(Modifier.width(8.dp)); Text("Geräteseite") }
        Button(onClick = onOpenRooms, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.HomeWork, null); Spacer(Modifier.width(8.dp)); Text("Raumseite") }
    }
    CardBlock(panel, text, "Ausgeblendete Geräte", "Ausgeblendete Geräte wieder einblenden", Icons.Default.VisibilityOff, action = onShowHidden) {
        Button(onClick = onShowHidden, modifier = Modifier.fillMaxWidth()) { Text("Ausgeblendete Geräte verwalten") }
    }
    CardBlock(panel, text, "Geräteprobleme", "Fehlende Messwerte und bekannte Warnungen prüfen", Icons.Default.HealthAndSafety, action = onOpenHealth) {
        Button(onClick = onOpenHealth, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.HealthAndSafety, null); Spacer(Modifier.width(8.dp)); Text("Problemübersicht öffnen") }
    }
    var showOwnPasswordDialog by remember { mutableStateOf(false) }
    var ownHomePilotPassword by remember { mutableStateOf("") }
    var showNewHomePilotUser by remember { mutableStateOf(false) }
    var newHomePilotUsername by remember { mutableStateOf("") }
    var newHomePilotDisplayName by remember { mutableStateOf("") }
    var newHomePilotPassword by remember { mutableStateOf("") }
    var newHomePilotRole by remember { mutableStateOf(HomePilotAccount.USER) }
    var resetHomePilotUser by remember { mutableStateOf<HomePilotAccount?>(null) }
    var deleteHomePilotUser by remember { mutableStateOf<HomePilotAccount?>(null) }
    var resetHomePilotPasswordValue by remember { mutableStateOf("") }
    CardBlock(panel, text, "HomePilot-Konto", homePilotAccount?.let { "${it.displayName} · ${if (it.role == HomePilotAccount.ADMIN) "Administrator" else "Benutzer"}" } ?: "Nicht angemeldet", Icons.Default.AccountCircle) {
        Text("HomePilot-Anmeldung ist unabhängig von ioBroker.", color = Muted, fontSize = 12.sp)
        TextButton(onClick = { ownHomePilotPassword = ""; showOwnPasswordDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Eigenes Passwort ändern") }
        TextButton(onClick = onHomePilotSwitch, modifier = Modifier.fillMaxWidth()) { Text("HomePilot-Benutzer wechseln") }
    }
    if (homePilotAccount?.role == HomePilotAccount.ADMIN) CardBlock(panel, text, "HomePilot-Benutzerverwaltung", "${homePilotAccounts.size} Konten", Icons.Default.ManageAccounts) {
        homePilotAccounts.forEach { account ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(account.displayName, color = text, fontWeight = FontWeight.SemiBold); Text("${account.username} · ${if (account.role == HomePilotAccount.ADMIN) "Admin" else "Benutzer"}", color = Muted, fontSize = 11.sp) }
                TextButton(onClick = { resetHomePilotUser = account; resetHomePilotPasswordValue = "" }) { Text("Passwort") }
                if (account.username != homePilotAccount.username) TextButton(onClick = { deleteHomePilotUser = account }) { Text("Löschen") }
            }
        }
        Button(onClick = { showNewHomePilotUser = true; newHomePilotUsername = ""; newHomePilotDisplayName = ""; newHomePilotPassword = ""; newHomePilotRole = HomePilotAccount.USER }, modifier = Modifier.fillMaxWidth()) { Text("Benutzer hinzufügen") }
    }
    if (showOwnPasswordDialog && homePilotAccount != null) AlertDialog(onDismissRequest = { showOwnPasswordDialog = false }, title = { Text("Eigenes HomePilot-Passwort ändern") }, text = { Column { Text("Neues Passwort (mindestens 8 Zeichen)"); OutlinedTextField(value = ownHomePilotPassword, onValueChange = { ownHomePilotPassword = it }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()) } }, confirmButton = { TextButton(onClick = { onResetHomePilotPassword(homePilotAccount.username, ownHomePilotPassword); showOwnPasswordDialog = false }) { Text("Speichern") } }, dismissButton = { TextButton(onClick = { showOwnPasswordDialog = false }) { Text("Abbrechen") } })
    if (showNewHomePilotUser) AlertDialog(onDismissRequest = { showNewHomePilotUser = false }, title = { Text("HomePilot-Benutzer anlegen") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = newHomePilotUsername, onValueChange = { newHomePilotUsername = it }, label = { Text("Benutzername") }, singleLine = true)
        OutlinedTextField(value = newHomePilotDisplayName, onValueChange = { newHomePilotDisplayName = it }, label = { Text("Anzeigename") }, singleLine = true)
        OutlinedTextField(value = newHomePilotPassword, onValueChange = { newHomePilotPassword = it }, label = { Text("Passwort (min. 8 Zeichen)") }, singleLine = true, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
        Text("Rolle")
        Row { TextButton(onClick = { newHomePilotRole = HomePilotAccount.USER }) { Text(if (newHomePilotRole == HomePilotAccount.USER) "✓ Benutzer" else "Benutzer") }; TextButton(onClick = { newHomePilotRole = HomePilotAccount.ADMIN }) { Text(if (newHomePilotRole == HomePilotAccount.ADMIN) "✓ Admin" else "Admin") } }
    } }, confirmButton = { TextButton(onClick = { onAddHomePilotAccount(newHomePilotUsername, newHomePilotDisplayName, newHomePilotPassword, newHomePilotRole); showNewHomePilotUser = false }) { Text("Anlegen") } }, dismissButton = { TextButton(onClick = { showNewHomePilotUser = false }) { Text("Abbrechen") } })
    deleteHomePilotUser?.let { account -> AlertDialog(onDismissRequest = { deleteHomePilotUser = null }, title = { Text("Benutzer wirklich löschen?") }, text = { Text("Das Konto „${account.username}“ wird dauerhaft aus HomePilot entfernt. Der ioBroker-Zugang bleibt unverändert.") }, confirmButton = { TextButton(onClick = { onDeleteHomePilotAccount(account.username); deleteHomePilotUser = null }) { Text("Löschen") } }, dismissButton = { TextButton(onClick = { deleteHomePilotUser = null }) { Text("Abbrechen") } }) }
    resetHomePilotUser?.let { account -> AlertDialog(onDismissRequest = { resetHomePilotUser = null }, title = { Text("Passwort zurücksetzen") }, text = { Column { Text("Neues Passwort für ${account.username}"); OutlinedTextField(value = resetHomePilotPasswordValue, onValueChange = { resetHomePilotPasswordValue = it }, label = { Text("Neues Passwort (min. 8 Zeichen)") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()) } }, confirmButton = { TextButton(onClick = { onResetHomePilotPassword(account.username, resetHomePilotPasswordValue); resetHomePilotUser = null }) { Text("Speichern") } }, dismissButton = { TextButton(onClick = { resetHomePilotUser = null }) { Text("Abbrechen") } }) }
    CardBlock(panel, text, "ioBroker-Verbindung", if (connected) "Verbunden" else "Nicht verbunden", Icons.Default.Cloud) {
        OutlinedTextField(value = serverUrl, onValueChange = onServerUrl, label = { Text("Serveradresse") }, singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = { InputConfirmIcon() })
        val statusHasError = listOf("fehlgeschlagen", "timeout", "nicht geantwortet", "send failed", "Fehler", "disconnected").any { status.contains(it, ignoreCase = true) }
        val statusColor = when {
            statusHasError -> Color(0xFFD32F2F)
            connected && !status.contains("prüfe", ignoreCase = true) -> Green
            else -> Muted
        }
        Text(status, color = statusColor, fontSize = 12.sp)
        if (!connected) {
            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text("Verbindung herstellen") }
        }
        Text("Dauerhafte WebSocket-Verbindung über ioBroker ws.0 (Port 8084).", color = Muted, fontSize = 11.sp)
    }
    CardBlock(panel, text, "ioBroker-Anmeldung", "Anmeldung mit einem vorhandenen ioBroker-Konto", Icons.Default.Lock) {
        OutlinedTextField(value = ioBrokerUsername, onValueChange = onIoBrokerUsername, label = { Text("ioBroker-Benutzer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = ioBrokerPassword, onValueChange = onIoBrokerPassword, label = { Text("ioBroker-Passwort") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
        Text("Die Zugangsdaten werden verschlüsselt auf diesem Android-Gerät gespeichert. Für Netzwerkverbindungen mit Passwort wird WSS/TLS empfohlen; ws:// überträgt Zugangsdaten unverschlüsselt.", color = Muted, fontSize = 11.sp)
        Button(onClick = onSaveCredentials, modifier = Modifier.fillMaxWidth()) { Text("Login speichern & neu verbinden") }
        TextButton(onClick = onSwitchUser, modifier = Modifier.fillMaxWidth()) { Text("Benutzer wechseln") }
    }
    CardBlock(panel, text, "Verbindung im Hintergrund", "Automatische Trennung bei Nichtbenutzung", Icons.Default.Timer) {
        Text("Beim Zurückkehren verbindet sich HomePilot automatisch erneut.", color = Muted, fontSize = 10.sp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(listOf(15, 30), listOf(60, 0)).forEach { rowOptions ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    rowOptions.forEach { minutes ->
                        Row(
                            Modifier.weight(1f).height(30.dp).clickable { onInactivityMinutes(minutes) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (inactivityMinutes == minutes) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (inactivityMinutes == minutes) Blue else Muted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (minutes == 0) "Nie trennen" else "$minutes Min.", color = text, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
    CardBlock(panel, text, "Energiesparen für HomePilot ausschalten", "Für stabile ioBroker-Verbindung", Icons.Default.BatteryFull) {
        Text("Android darf HomePilot nicht pausieren, wenn du kurz zu einer anderen App wechselst.", color = Muted, fontSize = 11.sp)
        Button(onClick = onBatterySettings, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) { Text("Android-App-Einstellungen öffnen") }
    }
    CardBlock(panel, text, "Wetter", "5-Tage-Prognose auf der Startseite", Icons.Default.WbSunny) {
        OutlinedTextField(value = weatherCity, onValueChange = onWeatherCity, label = { Text("Ort oder Postleitzahl") }, singleLine = true, modifier = Modifier.fillMaxWidth(), trailingIcon = { InputConfirmIcon() })
        Text("Wetterdaten von Open-Meteo; benötigt eine Internetverbindung.", color = Muted, fontSize = 11.sp)
    }
    GithubUpdateCard(appVersion = BuildConfig.VERSION_NAME)
}


private fun openBatterySettings(context: Context) {
    val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val batteryList = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(appInfo) } catch (_: Exception) {
        try { context.startActivity(batteryList) } catch (_: Exception) { }
    }
}
