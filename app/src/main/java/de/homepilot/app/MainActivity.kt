package de.homepilot.app

import android.os.Bundle
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.net.Uri
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Blue = Color(0xFF1769AA)
private val Green = Color(0xFF16845B)
private val Muted = Color(0xFF63748A)
private val LightBg = Color(0xFFF3F6FA)
private val DarkBg = Color(0xFF101923)
private val LightText = Color(0xFF172334)
private val DarkText = Color(0xFFEAF1F8)

private class IoBrokerClient {
    private val mainHandler = Handler(Looper.getMainLooper())
    fun disconnect() { /* HTTP requests are short-lived; nothing persistent to close. */ }

    private fun request(baseUrl: String, path: String, callback: (Boolean, String?, String?) -> Unit) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val base = baseUrl.trim().trimEnd('/')
                require(base.startsWith("http://") || base.startsWith("https://")) { "Serveradresse muss mit http:// oder https:// beginnen" }
                connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 7000
                    readTimeout = 7000
                    setRequestProperty("Accept", "application/json")
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IllegalStateException("HTTP $code${if (body.isNotBlank()) ": $body" else ""}")
                mainHandler.post { callback(true, body, null) }
            } catch (e: Exception) {
                val message = e.message ?: e.javaClass.simpleName
                mainHandler.post { callback(false, null, message) }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }

    fun connect(url: String, onResult: (Boolean, String) -> Unit) {
        getState(url, "shelly.0.SHPLG-S#376CC3#1.Relay0.Switch") { state, error ->
            if (state != null) onResult(true, "Verbindung erfolgreich hergestellt")
            else onResult(false, "Verbindung fehlgeschlagen: ${error ?: "Keine gültige Statusantwort"}")
        }
    }

    fun getState(baseUrl: String, id: String, callback: (Boolean?, String?) -> Unit) {
        val encodedId = Uri.encode(id)
        request(baseUrl, "/get/$encodedId") { ok, body, error ->
            if (!ok || body == null) {
                callback(null, error ?: "Keine Antwort für $id")
            } else {
                try {
                    val json = JSONObject(body)
                    val value = when {
                        json.has("val") -> json.opt("val")
                        json.optJSONObject("state")?.has("val") == true -> json.optJSONObject("state")?.opt("val")
                        else -> null
                    }
                    when (value) {
                        is Boolean -> callback(value, null)
                        is String -> callback(value.toBooleanStrictOrNull(), null)
                        else -> callback(null, "Unerwartete API-Antwort: $body")
                    }
                } catch (e: Exception) {
                    callback(null, "Antwort konnte nicht gelesen werden: ${e.message ?: body}")
                }
            }
        }
    }

    fun discoverSwitches(baseUrl: String, adapter: String, callback: (List<DeviceItem>, String?) -> Unit) {
        request(baseUrl, "/objects?pattern=${Uri.encode(adapter + ".*")}") { ok, body, error ->
            if (!ok || body == null) { callback(emptyList(), error ?: "Geräteliste nicht verfügbar"); return@request }
            try {
                val objects = JSONObject(body)
                val candidates = mutableListOf<DeviceItem>()
                val allIds = mutableListOf<String>()
                val keyIterator = objects.keys()
                while (keyIterator.hasNext()) allIds.add(keyIterator.next())
                for (id in allIds) {
                    val obj = objects.optJSONObject(id) ?: continue
                    val common = obj.optJSONObject("common") ?: continue
                    val role = common.optString("role", "").lowercase()
                    val type = common.optString("type", "")
                    val rawName = common.opt("name")
                    // ioBroker names can be multilingual JSON objects. Never expose those raw strings.
                    val name = when (rawName) {
                        is JSONObject -> rawName.optString("de").ifBlank { rawName.optString("en") }
                        else -> rawName?.toString().orEmpty()
                    }
                    val isBooleanSwitch = type == "boolean" && role.contains("switch")
                    val isRealDeviceOutput = when (adapter) {
                        "shelly.0" -> id.matches(Regex("shelly\\.0\\..*(?:Relay\\d*\\.Switch|Relay\\d*\\.Output|\\.Switch)$", RegexOption.IGNORE_CASE))
                        "hue.0" -> {
                            val leaf = id.substringAfterLast('.').lowercase()
                            val outputState = leaf in setOf("on", "all_on", "state", "switch")
                            // Hue adapter object paths vary by adapter version and configuration.
                            // Find plausible boolean on/off states anywhere under hue.0; the user
                            // explicitly chooses which candidates to import, so discovery is broad
                            // while import remains opt-in.
                            type == "boolean" && (outputState || role.contains("switch") || role.contains("light"))
                        }
                        else -> false
                    }
                    if (!isRealDeviceOutput) continue
                    if (id.startsWith("$adapter.")) {
                        val parent = id.substringBeforeLast('.')
                        val siblingPower = allIds.firstOrNull { candidateId ->
                            candidateId.substringBeforeLast('.') == parent &&
                                (candidateId.substringAfterLast('.').contains("power", ignoreCase = true) ||
                                 (objects.optJSONObject(candidateId)?.optJSONObject("common")?.optString("unit", "") == "W"))
                        }
                        // Prefer the human-readable device/channel name from ioBroker's
                        // object hierarchy over generic state labels such as "Switch".
                        fun objectName(objectId: String): String {
                            val objectCommon = objects.optJSONObject(objectId)?.optJSONObject("common") ?: return ""
                            val value = objectCommon.opt("name")
                            val resolved = when (value) {
                                is JSONObject -> value.optString("de").ifBlank { value.optString("en") }
                                else -> value?.toString().orEmpty()
                            }.trim()
                            val generic = resolved.isBlank() || resolved.startsWith("{") ||
                                resolved.equals("Switch", true) || resolved.equals("on", true) ||
                                resolved.equals("Output", true) || resolved.equals("State", true) ||
                                resolved.matches(Regex("(?i)(relay|channel|device)\\s*#?\\d*"))
                            return if (generic) "" else resolved
                        }
                        var ancestor = parent
                        var friendlyName = ""
                        while (ancestor.startsWith("$adapter.") && ancestor.isNotBlank()) {
                            friendlyName = objectName(ancestor)
                            if (friendlyName.isNotBlank()) break
                            val next = ancestor.substringBeforeLast('.', "")
                            if (next.isBlank() || next == ancestor || next == adapter) break
                            ancestor = next
                        }
                        fun isGenericDeviceName(value: String): Boolean {
                            val normalized = value.trim()
                            return normalized.isBlank() || normalized.startsWith("{") ||
                                normalized.equals("Switch", true) || normalized.equals("on", true) ||
                                normalized.equals("Output", true) || normalized.equals("State", true) ||
                                normalized.matches(Regex("(?i)(relay|channel|device)\\s*#?\\d*")) ||
                                normalized.matches(Regex("(?i)(relay|channel)\\d+"))
                        }
                        val fallbackName = when {
                            friendlyName.isNotBlank() -> friendlyName
                            !isGenericDeviceName(name) -> name.trim()
                            else -> if (adapter == "hue.0") "Hue-Licht" else "Shelly-Gerät"
                        }
                        fun relatedState(vararg endings: String): String? = allIds.firstOrNull { candidateId ->
                        candidateId.startsWith("$parent.", ignoreCase = true) &&
                            candidateId.substringAfterLast('.').lowercase() in endings.map { it.lowercase() } &&
                            objects.optJSONObject(candidateId)?.optJSONObject("common")?.optBoolean("write", false) == true
                    }
                    val brightnessId = if (adapter == "hue.0") relatedState("bri", "brightness", "level", "dim") else null
                    val colorTempId = if (adapter == "hue.0") relatedState("ct", "colortemperature", "color_temperature", "temperature") else null
                    val hueId = if (adapter == "hue.0") relatedState("hue", "colorhue") else null
                    val saturationId = if (adapter == "hue.0") relatedState("sat", "saturation") else null
                    candidates.add(DeviceItem(fallbackName, if (adapter == "hue.0") "Hue · ioBroker" else "Shelly · ioBroker", if (adapter == "hue.0") "light" else "plug", false, "", id, siblingPower, true, room = "Nicht zugeordnet", brightnessId = brightnessId, colorTempId = colorTempId, hueId = hueId, saturationId = saturationId))
                    }
                }
                if (adapter != "shelly.0" || candidates.isEmpty()) {
                    callback(candidates.sortedBy { it.name.lowercase() }, null)
                } else {
                    // Use the live value of each device's ioBroker ".name" datapoint,
                    // e.g. shelly.0.SHPLG-1#25E141#1.name -> "Warmes Wasser".
                    val resolved = java.util.Collections.synchronizedList(candidates.toMutableList())
                    var remaining = candidates.size
                    candidates.forEachIndexed { index, item ->
                        val stateId = item.stateId.orEmpty()
                        val deviceRoot = stateId.replace(Regex("\\.Relay\\d*\\.(Switch|Output)$", RegexOption.IGNORE_CASE), "")
                        val nameId = "$deviceRoot.name"
                        getTextState(baseUrl, nameId) { liveName ->
                            if (!liveName.isNullOrBlank()) {
                                val safeName = liveName.trim()
                                resolved[index] = item.copy(name = safeName)
                            }
                            remaining -= 1
                            if (remaining == 0) {
                                callback(resolved.sortedBy { it.name.lowercase() }, null)
                            }
                        }
                    }
                }
            } catch (e: Exception) { callback(emptyList(), "Geräteliste konnte nicht gelesen werden: ${e.message}") }
        }
    }

    fun getTextState(baseUrl: String, id: String, callback: (String?) -> Unit) {
        request(baseUrl, "/get/${Uri.encode(id)}") { ok, body, _ ->
            val value = if (ok && body != null) runCatching {
                val json = JSONObject(body)
                when (val raw = json.opt("val")) {
                    is String -> raw.trim()
                    is Number, is Boolean -> raw.toString()
                    else -> ""
                }
            }.getOrNull() else null
            callback(value?.takeIf { it.isNotBlank() })
        }
    }

    fun getNumericState(baseUrl: String, id: String, callback: (Double?) -> Unit) {
        request(baseUrl, "/get/${Uri.encode(id)}") { ok, body, _ ->
            val value = if (ok && body != null) runCatching { JSONObject(body).optDouble("val", Double.NaN) }.getOrNull() else null
            callback(value?.takeIf { it.isFinite() })
        }
    }

    fun setState(baseUrl: String, id: String, value: Boolean, callback: (Boolean, String) -> Unit) {
        val encodedId = Uri.encode(id)
        val encodedValue = if (value) "true" else "false"
        request(baseUrl, "/set/$encodedId?value=$encodedValue") { ok, body, error ->
            if (!ok) callback(false, "Schalten fehlgeschlagen: ${error ?: "unbekannter Fehler"}")
            else callback(true, "Schaltbefehl gesendet")
        }
    }

    fun setNumericState(baseUrl: String, id: String, value: Double, callback: (Boolean, String) -> Unit) {
        val encodedId = Uri.encode(id)
        val encodedValue = Uri.encode(value.toString())
        request(baseUrl, "/set/$encodedId?value=$encodedValue") { ok, _, error ->
            if (!ok) callback(false, "Lichtwert konnte nicht gesetzt werden: ${error ?: "unbekannter Fehler"}")
            else callback(true, "Lichteinstellung gespeichert")
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HomePilotApp() }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Übersicht", Icons.Default.Home),
    LIGHTS("Licht", Icons.Default.Lightbulb),
    DEVICES("Geräte", Icons.Default.Devices),
    SAFETY("Sicherheit", Icons.Default.Shield),
    ROOMS("Räume", Icons.Default.HomeWork),
    SETTINGS("Einstellungen", Icons.Default.Settings)
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
    val currentSaturation: Double? = null
)

@Composable
private fun HomePilotApp() {
    var dark by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(Tab.HOME) }
    var showDemoNotice by remember { mutableStateOf(false) }
    val lights = remember { mutableStateListOf<DeviceItem>() }
    val devices = remember { mutableStateListOf<DeviceItem>() }
    val discoveredLights = remember { mutableStateListOf<DeviceItem>() }
    var showLightImportDialog by remember { mutableStateOf(false) }
    var scanResultMessage by remember { mutableStateOf<String?>(null) }
    var selectedLightIds by remember { mutableStateOf(setOf<String>()) }
    var floodAlarm by remember { mutableStateOf(false) }
    var floodBattery by remember { mutableIntStateOf(100) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("homepilot", Context.MODE_PRIVATE) }
    var rooms by remember { mutableStateOf(prefs.getString("rooms", "Wohnzimmer|Küche|Schlafzimmer|Bad|Hauswirtschaft|Nicht zugeordnet")!!.split("|").toMutableList()) }
    var customNames by remember { mutableStateOf(readStringMap(prefs.getString("deviceNames", "{}"))) }
    var deviceRooms by remember { mutableStateOf(readStringMap(prefs.getString("deviceRooms", "{}"))) }
    var hiddenIds by remember { mutableStateOf(prefs.getStringSet("hiddenDevices", emptySet())?.toSet() ?: emptySet()) }
    var importedLightIds by remember { mutableStateOf(prefs.getStringSet("importedLights", emptySet())?.toSet() ?: emptySet()) }
    var hiddenItems by remember { mutableStateOf(readHiddenItems(prefs.getString("hiddenDeviceItems", "[]"))) }
    var confirmRemoveDevice by remember { mutableStateOf<DeviceItem?>(null) }
    var confirmDeleteRoom by remember { mutableStateOf<String?>(null) }
    var showHiddenDevices by remember { mutableStateOf(false) }
    var selectedRoom by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = selectedTab != Tab.HOME) {
        selectedTab = Tab.HOME
        selectedRoom = null
    }
    var showRoomDialog by remember { mutableStateOf(false) }
    var roomNameInput by remember { mutableStateOf("") }
    var editDevice by remember { mutableStateOf<DeviceItem?>(null) }
    var editName by remember { mutableStateOf("") }
    var editRoom by remember { mutableStateOf("Nicht zugeordnet") }
    var scanStatus by remember { mutableStateOf("") }
    fun persistRooms() { prefs.edit().putString("rooms", rooms.joinToString("|")).apply() }
    fun persistNames() { prefs.edit().putString("deviceNames", JSONObject(customNames as Map<*, *>).toString()).apply() }
    fun persistDeviceRooms() { prefs.edit().putString("deviceRooms", JSONObject(deviceRooms as Map<*, *>).toString()).apply() }
    fun persistHiddenItems() { prefs.edit().putString("hiddenDeviceItems", writeHiddenItems(hiddenItems)).apply() }
    fun applyCustomConfig() {
        for (i in devices.indices) {
            val item = devices[i]
            val id = item.stateId ?: continue
            devices[i] = item.copy(name = customNames[id] ?: item.name, room = deviceRooms[id] ?: when { id.contains("6EDB6C") -> "Wohnzimmer"; id.contains("376CC3") -> "Hauswirtschaft"; else -> "Nicht zugeordnet" })
        }
        for (i in lights.indices) {
            val item = lights[i]; val id = item.stateId ?: continue
            lights[i] = item.copy(name = customNames[id] ?: item.name, room = deviceRooms[id] ?: "Nicht zugeordnet")
        }
    }
    var serverUrl by remember { mutableStateOf((prefs.getString("server", null) ?: "http://192.168.178.54:8087").let { saved -> if (saved.trimEnd('/').endsWith(":8082")) saved.trimEnd('/').removeSuffix(":8082") + ":8087" else saved }) }
        var connectionStatus by remember { mutableStateOf("Nicht verbunden") }
    var connected by remember { mutableStateOf(false) }
    var showConnectionAlert by remember { mutableStateOf(false) }
    var washOn by remember { mutableStateOf(false) }
    var waterOn by remember { mutableStateOf(false) }
    val ioClient = remember { IoBrokerClient() }
    DisposableEffect(Unit) { onDispose { ioClient.disconnect() } }

    fun connectToIoBroker(manualDeviceSearch: Boolean = false) {
        prefs.edit().putString("server", serverUrl).apply()
        connectionStatus = "Verbindung wird aufgebaut …"
        ioClient.connect(serverUrl) { ok, msg ->
            connected = ok
            connectionStatus = if (ok) msg else "Bitte Verbindung prüfen. $msg"
            showConnectionAlert = !ok
            if (ok) {
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
                    } else connectionStatus = "Verbindung steht, Shelly-Geräte konnten aber nicht geladen werden. Bitte prüfen: $err"
                }
                ioClient.discoverSwitches(serverUrl, "hue.0") { found, err ->
                    if (err == null) {
                        val candidates = found.filter { it.stateId !in hiddenIds && it.stateId !in importedLightIds }
                        if (manualDeviceSearch) {
                            discoveredLights.clear()
                            discoveredLights.addAll(candidates)
                            selectedLightIds = emptySet()
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
                        if (manualDeviceSearch) scanResultMessage = "Die Hue-Suche ist fehlgeschlagen. Prüfe die ioBroker Simple API und den Adapter hue.0. Details: $err"
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(350)
        connectToIoBroker()
    }
    LaunchedEffect(connected, serverUrl) {
        while (connected) {
            delay(8000)
            refreshStates(ioClient, serverUrl, devices, lights)
        }
    }

    val bg = if (dark) DarkBg else LightBg
    val panel = if (dark) Color(0xFF1B2734) else Color.White
    val text = if (dark) DarkText else LightText
    val scheme = if (dark) darkColorScheme(primary = Color(0xFF78B9F3), secondary = Color(0xFF67D4A5), background = bg, surface = panel, onSurface = text, onBackground = text, surfaceVariant = Color(0xFF293747), onSurfaceVariant = Color(0xFFB2C1D0)) else lightColorScheme(primary = Blue, secondary = Green, background = bg, surface = panel, onSurface = text, onBackground = text, surfaceVariant = Color(0xFFE6EDF5), onSurfaceVariant = Muted, outline = Color(0xFFCBD5E1))

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().statusBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Header(dark = dark, text = text, onSettings = { selectedTab = Tab.SETTINGS })
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
                    when (selectedTab) {
                        Tab.HOME -> HomeScreen(rooms, lights, devices, floodAlarm, floodBattery, panel, text, onOpenRoom = { room -> selectedRoom = room; selectedTab = Tab.ROOMS }, onTab = { selectedTab = it })
                        Tab.LIGHTS -> LightsScreen(lights, rooms, panel, text, connected,
                            onEditDevice = { item -> editDevice = item; editName = item.name; editRoom = item.room },
                            onSetNumeric = { item, id, value -> ioClient.setNumericState(serverUrl, id, value) { ok, message -> connectionStatus = message } },
                            onSetDefaultBrightness = { item, percent -> prefs.edit().putInt("lightOnBrightness:" + (item.stateId ?: item.name), percent).apply() },
                            defaultBrightness = { item -> prefs.getInt("lightOnBrightness:" + (item.stateId ?: item.name), 100) },
                            onToggle = { item, value ->
                            val id = item.stateId
                            if (id != null && item.writable) {
                                fun switchLightOn() {
                                    ioClient.setState(serverUrl, id, value) { ok, message ->
                                        connectionStatus = message
                                        if (ok) ioClient.getState(serverUrl, id) { state, err -> if (state != null) updateDeviceState(devices, lights, id, state); if (err != null) connectionStatus = err }
                                    }
                                }
                                if (value && item.brightnessId != null) {
                                    val percent = prefs.getInt("lightOnBrightness:" + (item.stateId ?: item.name), 100)
                                    ioClient.setNumericState(serverUrl, item.brightnessId, percent * 2.54) { ok, message ->
                                        connectionStatus = message
                                        switchLightOn()
                                    }
                                } else switchLightOn()
                            }
                        })
                        Tab.DEVICES -> DevicesScreen(devices, rooms, panel, text, connected,
                            onEditDevice = { item -> editDevice = item; editName = item.name; editRoom = item.room },
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
                        Tab.SAFETY -> SafetyScreen(floodAlarm, floodBattery, panel, text, onAlarm = { floodAlarm = it }, onBattery = { floodBattery = it })
                        Tab.ROOMS -> RoomsScreen(rooms, devices, lights, panel, text,
                            onSelectRoom = { selectedRoom = it }, onAddRoom = { roomNameInput = ""; showRoomDialog = true },
                            onRenameRoom = { old, new -> if (new.isNotBlank() && new !in rooms) { rooms = rooms.map { if (it == old) new.trim() else it }.toMutableList(); persistRooms(); devices.indices.forEach { i -> if (devices[i].room == old) devices[i] = devices[i].copy(room = new.trim()) }; lights.indices.forEach { i -> if (lights[i].room == old) lights[i] = lights[i].copy(room = new.trim()) } } },
                            selectedRoom = selectedRoom, onEditDevice = { item -> editDevice = item; editName = item.name; editRoom = item.room },
                            hiddenItems = hiddenItems,
                            onShowHidden = { showHiddenDevices = true },
                            onRequestDeleteRoom = { room -> confirmDeleteRoom = room })
                        Tab.SETTINGS -> SettingsScreen(dark, onDark = { dark = it }, panel, text,
                            serverUrl = serverUrl, onServerUrl = { serverUrl = it },
                            status = if (connected) "Verbindung erfolgreich hergestellt" else connectionStatus, connected = connected,
                            onConnect = { connectToIoBroker(manualDeviceSearch = true) })
                    }
                    if (showConnectionAlert) AlertDialog(
                        onDismissRequest = { showConnectionAlert = false },
                        icon = { Icon(Icons.Default.WifiOff, null, tint = Color(0xFFD97706)) },
                        title = { Text("Keine Verbindung zu ioBroker") },
                        text = { Text("HomePilot konnte keine Verbindung herstellen. Bitte prüfe, ob ioBroker läuft, dein Smartphone im richtigen WLAN ist und die Serveradresse in den Einstellungen stimmt.\n\nDetails: $connectionStatus") },
                        confirmButton = {
                            TextButton(onClick = { showConnectionAlert = false; connectToIoBroker() }) { Text("Erneut versuchen") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConnectionAlert = false; selectedTab = Tab.SETTINGS }) { Text("Einstellungen prüfen") }
                        }
                    )
                    if (showRoomDialog) AlertDialog(onDismissRequest = { showRoomDialog = false }, title = { Text("Raum hinzufügen") }, text = { OutlinedTextField(value = roomNameInput, onValueChange = { roomNameInput = it }, label = { Text("Raumname") }, singleLine = true) }, confirmButton = { TextButton(onClick = { val n = roomNameInput.trim(); if (n.isNotBlank() && n !in rooms) { rooms = (rooms.filterNot { it == "Nicht zugeordnet" } + n + "Nicht zugeordnet").toMutableList(); persistRooms() }; showRoomDialog = false }) { Text("Hinzufügen") } }, dismissButton = { TextButton(onClick = { showRoomDialog = false }) { Text("Abbrechen") } })
                    if (editDevice != null) AlertDialog(onDismissRequest = { editDevice = null }, title = { Text("Gerät bearbeiten") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Gerätename") }, singleLine = true); Text("Raum"); rooms.forEach { room -> Row(Modifier.fillMaxWidth().clickable { editRoom = room }, verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = editRoom == room, onClick = { editRoom = room }); Text(room) } } } }, confirmButton = { TextButton(onClick = { val item = editDevice!!; val id = item.stateId; if (id != null) { customNames = customNames.toMutableMap().apply { put(id, editName.trim().ifBlank { item.name }) }; deviceRooms = deviceRooms.toMutableMap().apply { put(id, editRoom) }; persistNames(); persistDeviceRooms(); val di = devices.indexOfFirst { it.stateId == id }; if (di >= 0) devices[di] = devices[di].copy(name = customNames[id] ?: item.name, room = editRoom); val li = lights.indexOfFirst { it.stateId == id }; if (li >= 0) lights[li] = lights[li].copy(name = customNames[id] ?: item.name, room = editRoom) }; editDevice = null }) { Text("Speichern") } }, dismissButton = { Row { TextButton(onClick = { confirmRemoveDevice = editDevice; editDevice = null }) { Text("Aus HomePilot entfernen") }; TextButton(onClick = { editDevice = null }) { Text("Abbrechen") } } })
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
                        title = { Text("Lampensuche") },
                        text = { Text(scanResultMessage ?: "") },
                        confirmButton = { TextButton(onClick = { scanResultMessage = null }) { Text("OK") } }
                    )
                    if (showLightImportDialog) AlertDialog(
                        onDismissRequest = { showLightImportDialog = false },
                        title = { Text("Hue-Lampen auswählen") },
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
                                            Text(item.name, color = LightText, fontWeight = FontWeight.SemiBold)
                                            Text(id, color = Muted, fontSize = 9.sp)
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val chosen = discoveredLights.filter { it.stateId in selectedLightIds }
                                val chosenIds = chosen.mapNotNull { it.stateId }.toSet()
                                importedLightIds = importedLightIds + chosenIds
                                prefs.edit().putStringSet("importedLights", importedLightIds).apply()
                                lights.addAll(chosen.filter { item -> lights.none { it.stateId == item.stateId } })
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
                                    if (id != null) { hiddenIds = hiddenIds - id; prefs.edit().putStringSet("hiddenDevices", hiddenIds).apply(); hiddenItems = hiddenItems.filterNot { it.stateId == id }; persistHiddenItems(); if (item.kind == "light") { if (lights.none { it.stateId == id }) lights.add(item) } else { if (devices.none { it.stateId == id }) devices.add(item) }; applyCustomConfig(); refreshStates(ioClient, serverUrl, devices, lights) }
                                }) { Text("Wieder hinzufügen") }
                            } }
                        } },
                        confirmButton = { TextButton(onClick = { showHiddenDevices = false }) { Text("Fertig") } }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                NavigationBar(containerColor = panel) {
                    listOf(Tab.HOME, Tab.LIGHTS, Tab.DEVICES, Tab.SAFETY, Tab.ROOMS, Tab.SETTINGS).forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, fontSize = 10.sp, maxLines = 1) },
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
private fun Header(dark: Boolean, text: Color, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("HOMEPILOT", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("Dein Zuhause", color = text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Smart Home auf einen Blick", color = if (dark) Color(0xFFB2C1D0) else Muted, fontSize = 13.sp)
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(if (dark) Color(0xFF1B2734) else Color.White)) {
            Icon(Icons.Default.Settings, contentDescription = "Einstellungen", tint = text)
        }
    }
}

@Composable
private fun HomeScreen(
    rooms: List<String>, lights: List<DeviceItem>, devices: List<DeviceItem>, floodAlarm: Boolean, floodBattery: Int,
    panel: Color, text: Color, onOpenRoom: (String) -> Unit, onTab: (Tab) -> Unit
) {
    Text("Deine Räume", color = text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    Text("Tippe auf einen Raum für die Geräteübersicht.", color = Muted, fontSize = 13.sp)
    val visibleRooms = rooms.filter { it != "Nicht zugeordnet" }
    visibleRooms.chunked(2).forEach { rowRooms ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            rowRooms.forEach { room ->
                val count = (devices + lights).count { it.room == room }
                val roomIcon = when (room.lowercase()) {
                    "wohnzimmer" -> Icons.Default.Weekend
                    "küche" -> Icons.Default.Kitchen
                    "schlafzimmer" -> Icons.Default.Bed
                    "bad", "badezimmer" -> Icons.Default.Bathtub
                    "hauswirtschaft" -> Icons.Default.LocalLaundryService
                    else -> Icons.Default.HomeWork
                }
                val roomAccent = when (room.lowercase()) {
                    "wohnzimmer" -> Color(0xFF6C63C7)
                    "küche" -> Color(0xFFE09A22)
                    "schlafzimmer" -> Color(0xFF4787C7)
                    "bad", "badezimmer" -> Color(0xFF159A9C)
                    "hauswirtschaft" -> Color(0xFF2E9B70)
                    else -> Blue
                }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                        .background(roomAccent.copy(alpha = if (panel == Color.White) 0.20f else 0.30f))
                        .clickable { onOpenRoom(room) }.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(roomAccent), contentAlignment = Alignment.Center) {
                        Icon(roomIcon, null, tint = Color.White, modifier = Modifier.size(27.dp))
                    }
                    Text(room, color = text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("$count Geräte", color = Muted, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Öffnen", color = Blue, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Icon(Icons.Default.ChevronRight, null, tint = Blue, modifier = Modifier.size(16.dp))
                    }
                }
            }
            if (rowRooms.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    CardBlock(panel, text, "Sicherheit", "Wassermelder Küche", Icons.Default.WaterDrop, action = { onTab(Tab.SAFETY) }) {
        Text("Wassermelder-Daten werden eingebunden, sobald die echten Datenpunkte zugeordnet sind.", color = Muted, fontSize = 12.sp)
    }
    Text("Später erweiterbar: KlimaPilot, Mähroboter und Saugroboter.", color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
        Icons.Default.Palette -> Color(0xFFD46A9A)
        Icons.Default.Shield -> Color(0xFF2E9B70)
        Icons.Default.VisibilityOff -> Color(0xFF7A8797)
        Icons.Default.RoomPreferences -> Color(0xFFE29A25)
        else -> Blue
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(accent.copy(alpha = if (panel == Color.White) 0.20f else 0.30f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(accent), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            if (action != null) IconButton(onClick = action) { Icon(actionIcon, null, tint = Blue) }
        }
        content()
    }
}

@Composable
private fun DeviceRow(item: DeviceItem, panel: Color, text: Color, compact: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
            .background(if (item.initialOn) Color(0xFF1769AA).copy(alpha = 0.07f) else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = if (compact) 9.dp else 13.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(if (item.initialOn) Color(0xFFDCEBFA) else if (panel == Color.White) Color(0xFFF0F3F7) else Color(0xFF293747)), contentAlignment = Alignment.Center) {
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
    onToggle: (DeviceItem, Boolean) -> Unit,
    onEditDevice: (DeviceItem) -> Unit,
    onSetNumeric: (DeviceItem, String, Double) -> Unit,
    onSetDefaultBrightness: (DeviceItem, Int) -> Unit,
    defaultBrightness: (DeviceItem) -> Int
) {
    Text("Hue-Lichter", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text)
    Text("Nach Räumen geordnet · Echte Zustände aus ioBroker", color = Muted, fontSize = 13.sp)
    if (!connected) Text("Bitte zuerst unter Einstellungen verbinden.", color = Muted)
    else if (lights.isEmpty()) Text("Keine schaltbaren Hue-Zustände gefunden. Prüfe die Hue-Objekte unter hue.0.", color = Muted)
    val grouped = lights.groupBy { it.room.ifBlank { "Nicht zugeordnet" } }
    val roomOrder = (rooms.filter { it != "Nicht zugeordnet" } + "Nicht zugeordnet" + grouped.keys.filter { it !in rooms && it != "Nicht zugeordnet" }).distinct()
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

@Composable
private fun DevicesScreen(devices: List<DeviceItem>, rooms: List<String>, panel: Color, text: Color, connected: Boolean, onToggle: (DeviceItem, Boolean) -> Unit, onEditDevice: (DeviceItem) -> Unit) {
    val unassigned = devices.filter { it.room.isBlank() || it.room == "Nicht zugeordnet" }
    Text("Shelly-Geräte", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text)
    Text("Echte Schaltzustände aus ioBroker", color = Muted, fontSize = 13.sp)
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
    if (!connected) Text("Bitte zuerst unter Einstellungen verbinden.", color = Muted)
    else if (devices.isEmpty()) Text("Noch keine Shelly-Geräte gefunden.", color = Muted)
    val grouped = devices.groupBy { it.room.ifBlank { "Nicht zugeordnet" } }
    val roomOrder = (rooms.filter { it != "Nicht zugeordnet" } + "Nicht zugeordnet" + grouped.keys.filter { it !in rooms && it != "Nicht zugeordnet" }).distinct()
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
                CardBlock(panel, text, item.name, item.subtitle, if (item.name == "Wasser") Icons.Default.WaterDrop else Icons.Default.Power, action = { onEditDevice(item) }, actionIcon = Icons.Default.Edit) {
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
private fun RoomsScreen(rooms: List<String>, devices: List<DeviceItem>, lights: List<DeviceItem>, panel: Color, text: Color,
    onSelectRoom: (String?) -> Unit, onAddRoom: () -> Unit, onRenameRoom: (String, String) -> Unit,
    selectedRoom: String?, onEditDevice: (DeviceItem) -> Unit,
    hiddenItems: List<DeviceItem>, onShowHidden: () -> Unit,
    onRequestDeleteRoom: (String) -> Unit) {
    var renameRoom by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    Text("Räume & Geräte", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text)
    Text("Räume und Zuordnungen selbst verwalten", color = Muted, fontSize = 13.sp)
    Button(onClick = onAddRoom, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Raum hinzufügen") }
    if (selectedRoom != null) {
        TextButton(onClick = { onSelectRoom(null) }) { Icon(Icons.Default.ArrowBack, null); Text("Alle Räume") }
        Text(selectedRoom, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = text)
        val roomItems = (devices + lights).filter { it.room == selectedRoom }.distinctBy { it.stateId ?: it.name }
        val lamps = roomItems.filter { it.kind == "light" }
        val switches = roomItems.filter { it.kind in listOf("plug", "relay", "switch") }
        val otherItems = roomItems.filter { it.kind !in listOf("light", "plug", "relay", "switch") }
        if (roomItems.isEmpty()) Text("Noch keine Geräte zugeordnet.", color = Muted)
        if (lamps.isNotEmpty()) {
            Text("Lampen", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = text)
            lamps.forEach { item ->
                CardBlock(panel, text, item.name, "", Icons.Default.Lightbulb, action = { onEditDevice(item) }, actionIcon = Icons.Default.Edit) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (item.initialOn) "Eingeschaltet" else "Ausgeschaltet", color = if (item.initialOn) Green else Muted, modifier = Modifier.weight(1f))
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
    } else {
        rooms.forEach { room ->
            val count = (devices + lights).count { it.room == room }
            CardBlock(panel, text, room, "$count Geräte", Icons.Default.HomeWork, action = { onSelectRoom(room) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { renameRoom = room; renameValue = room }) { Text("Umbenennen") }
                    if (room != "Nicht zugeordnet") TextButton(onClick = { onRequestDeleteRoom(room) }) { Text("Löschen") }
                }
            }
        }
        CardBlock(panel, text, "Ausgeblendete Geräte", "${hiddenItems.size} ausgeblendet · bei Bedarf wieder hinzufügen", Icons.Default.VisibilityOff, action = onShowHidden) {
            TextButton(onClick = onShowHidden) { Text("Ausgeblendete Geräte verwalten") }
        }
        CardBlock(panel, text, "Geräte verwalten", "Umbenennen und Räume zuweisen", Icons.Default.Devices) {
            (devices + lights).distinctBy { it.stateId }.forEach { item -> Row(Modifier.fillMaxWidth().clickable { onEditDevice(item) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.name, color = text, fontWeight = FontWeight.SemiBold); Text(item.room, color = Muted, fontSize = 12.sp) }; Icon(Icons.Default.Edit, null, tint = Blue) } }
        }
    }
    if (renameRoom != null) AlertDialog(onDismissRequest = { renameRoom = null }, title = { Text("Raum umbenennen") }, text = { OutlinedTextField(value = renameValue, onValueChange = { renameValue = it }, singleLine = true, label = { Text("Name") }) }, confirmButton = { TextButton(onClick = { onRenameRoom(renameRoom!!, renameValue); renameRoom = null }) { Text("Speichern") } }, dismissButton = { TextButton(onClick = { renameRoom = null }) { Text("Abbrechen") } })
}


private fun writeHiddenItems(items: List<DeviceItem>): String {
    val array = JSONArray()
    items.forEach { item ->
        val obj = JSONObject()
        obj.put("name", item.name); obj.put("subtitle", item.subtitle); obj.put("kind", item.kind)
        obj.put("initialOn", item.initialOn); obj.put("detail", item.detail); obj.put("stateId", item.stateId)
        obj.put("powerId", item.powerId); obj.put("writable", item.writable); obj.put("powerWatts", item.powerWatts); obj.put("room", item.room)
        obj.put("brightnessId", item.brightnessId); obj.put("colorTempId", item.colorTempId); obj.put("hueId", item.hueId); obj.put("saturationId", item.saturationId)
        array.put(obj)
    }
    return array.toString()
}

private fun readHiddenItems(raw: String?): List<DeviceItem> = try {
    val array = JSONArray(raw ?: "[]")
    (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        DeviceItem(o.optString("name"), o.optString("subtitle"), o.optString("kind", "plug"), o.optBoolean("initialOn"), o.optString("detail"), o.optString("stateId").takeIf { it.isNotBlank() && it != "null" }, o.optString("powerId").takeIf { it.isNotBlank() && it != "null" }, o.optBoolean("writable", true), if (o.has("powerWatts") && !o.isNull("powerWatts")) o.optDouble("powerWatts") else null, o.optString("room", "Nicht zugeordnet"), o.optString("brightnessId").takeIf { it.isNotBlank() && it != "null" }, o.optString("colorTempId").takeIf { it.isNotBlank() && it != "null" }, o.optString("hueId").takeIf { it.isNotBlank() && it != "null" }, o.optString("saturationId").takeIf { it.isNotBlank() && it != "null" })
    }
} catch (_: Exception) { emptyList() }

private fun readStringMap(raw: String?): Map<String, String> = try {
    val obj = JSONObject(raw ?: "{}"); val result = mutableMapOf<String, String>(); val keys = obj.keys(); while (keys.hasNext()) { val key = keys.next(); result[key] = obj.optString(key) }; result
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
            if (watts != null) {
                val d = devices.indexOfFirst { it.stateId == id }
                if (d >= 0) devices[d] = devices[d].copy(powerWatts = watts)
            }
        }
    }
}

@Composable
private fun SafetyScreen(alarm: Boolean, battery: Int, panel: Color, text: Color, onAlarm: (Boolean) -> Unit, onBattery: (Int) -> Unit) {
    Text("Sicherheit", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text)
    CardBlock(panel, text, "Wassermelder Küche", "Shelly Flood Gen1", Icons.Default.WaterDrop) {
        Icon(Icons.Default.Info, null, tint = Muted, modifier = Modifier.size(30.dp))
        Text("Die echten Datenpunkte des Wassermelders sind noch nicht zugeordnet. Deshalb zeigt HomePilot hier bewusst keinen vermeintlich sicheren Zustand an.", color = text)
        Text("Als nächstes binden wir den tatsächlichen Alarm- und Batteriestatus aus ioBroker an.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SettingsScreen(dark: Boolean, onDark: (Boolean) -> Unit, panel: Color, text: Color,
    serverUrl: String, onServerUrl: (String) -> Unit, status: String, connected: Boolean, onConnect: () -> Unit) {
    Text("Einstellungen", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = text)
    CardBlock(panel, text, "Darstellung", "Helles Design standardmäßig", Icons.Default.Palette) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Dunkles Design", color = text, fontWeight = FontWeight.SemiBold); Text("Kann jederzeit umgeschaltet werden", color = Muted, fontSize = 12.sp) }
            Switch(checked = dark, onCheckedChange = onDark)
        }
    }
    CardBlock(panel, text, "ioBroker-Verbindung", if (connected) "Verbunden" else "Verbindung konfigurieren", Icons.Default.Cloud) {
        OutlinedTextField(value = serverUrl, onValueChange = onServerUrl, label = { Text("Serveradresse") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(status, color = if (connected) Green else Muted, fontSize = 12.sp)
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text(if (connected) "Geräte erneut suchen" else "Verbinden und Geräte suchen") }
        Text("Verbindung über Simple API auf Port 8087. In der aktuellen ioBroker-Konfiguration erfolgt der Zugriff ohne Anmeldung.", color = Muted, fontSize = 11.sp)
    }
    CardBlock(panel, text, "App-Informationen", "Versionsnummer", Icons.Default.Info) {
        Text("HomePilot ${BuildConfig.VERSION_NAME}", color = text, fontWeight = FontWeight.SemiBold)
    }
    CardBlock(panel, text, "Geplante Erweiterungen", "Nach erfolgreichem Verbindungstest", Icons.Default.AddCircle) {
        Text("• Live-Erkennung von Hue- und Shelly-Schaltern\n• Wassermelder-Datenpunkte als nächster Schritt\n• Später: KlimaPilot und Roboter", color = text, fontSize = 13.sp, lineHeight = 22.sp)
    }
}
