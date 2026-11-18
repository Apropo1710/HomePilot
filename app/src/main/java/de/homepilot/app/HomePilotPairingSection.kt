package de.homepilot.app

import android.app.Activity
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhonelinkRing
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.common.BitMatrix
import de.homepilot.app.sync.HomePilotPairingAcknowledgement
import de.homepilot.app.sync.HomePilotPairingInvitation
import de.homepilot.app.sync.HomePilotPairingQrCode
import de.homepilot.app.sync.HomePilotPairingResponse
import de.homepilot.app.sync.HomePilotPairingSessionController
import de.homepilot.app.sync.HomePilotPairingTcpChannel
import de.homepilot.app.sync.HomePilotPeerIdentityStore
import de.homepilot.app.sync.HomePilotTrustedPeerStore
import de.homepilot.app.sync.HomePilotWifiListenerAddressSelector
import java.net.Inet4Address

@Composable
internal fun HomePilotPairingSection(panel: Color, text: Color) {
    val context = LocalContext.current
    val identityStore = remember { HomePilotPeerIdentityStore() }
    val trustedStore = remember(context) { HomePilotTrustedPeerStore(context) }
    val invitationFactory = remember(identityStore) { HomePilotPairingInvitation(identityStore) }
    val responseFactory = remember(identityStore) { HomePilotPairingResponse(identityStore) }
    var controller by remember { mutableStateOf<HomePilotPairingSessionController?>(null) }
    var localFingerprint by remember { mutableStateOf<String?>(null) }
    var displayName by remember { mutableStateOf("HomePilot-Gerät") }
    var qrPayload by remember { mutableStateOf<String?>(null) }
    var scannedInvitationPayload by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<HomePilotPairingSessionController.PendingConfirmation?>(null) }
    var listener by remember { mutableStateOf<HomePilotPairingTcpChannel.Listener?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ein Gerät zeigt den QR-Code, das andere scannt ihn.") }
    var expanded by remember { mutableStateOf(false) }

    fun getController(): HomePilotPairingSessionController {
        controller?.let { return it }
        val identity = identityStore.getOrCreateIdentity()
        localFingerprint = identity.fingerprint
        return HomePilotPairingSessionController(
            localPeerId = identity.peerId,
            trustedPeerStore = trustedStore,
            createInvitation = { throw IllegalStateException("Der WLAN-Rückkanal muss vor der Einladung gestartet werden.") },
            createResponse = { invitation, now -> responseFactory.create(invitation, now) }
        ).also { controller = it }
    }

    fun closeListener() {
        listener?.close()
        listener = null
    }

    fun cancelSession(message: String) {
        closeListener()
        controller?.cancel()
        qrPayload = null
        scannedInvitationPayload = null
        pending = null
        busy = false
        status = message
    }

    fun findWifi(): Pair<Network, Inet4Address> {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: throw IllegalStateException("Android-Netzwerkverwaltung ist nicht verfügbar.")
        val candidates = connectivity.allNetworks.toList().sortedBy { if (it == connectivity.activeNetwork) 0 else 1 }
        for (network in candidates) {
            if (connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) continue
            val props = connectivity.getLinkProperties(network) ?: continue
            val iface = props.interfaceName ?: continue
            val address = HomePilotWifiListenerAddressSelector.select(iface, props.linkAddresses.map { it.address })
            if (address is Inet4Address && !address.isLinkLocalAddress && address.isSiteLocalAddress) return network to address
        }
        throw IllegalStateException("Kein privates IPv4-WLAN gefunden. Bitte beide Smartphones mit demselben WLAN verbinden.")
    }

    fun startInvitation() {
        runCatching {
            closeListener()
            val (network, address) = findWifi()
            val channel = HomePilotPairingTcpChannel.openListener(address)
            listener = channel
            val active = getController()
            val invitation = invitationFactory.create(channel.host, channel.port)
            qrPayload = active.startOutgoingInvitation(invitation)
            pending = null
            busy = true
            status = "Warte auf das andere Gerät …"
            Thread({
                @Suppress("UNUSED_VARIABLE") val selectedWifiNetwork = network
                try {
                    channel.acceptOnce { responsePayload ->
                        val confirmation = active.prepareOutgoingResponse(responsePayload)
                            ?: throw IllegalArgumentException("Die signierte Antwort ist ungültig oder gehört nicht zu dieser Einladung.")
                        active.confirmOutgoingResponseAndCreateAcknowledgement(
                            confirmation = confirmation,
                            displayName = "HomePilot-Gerät",
                            createAcknowledgement = { response, now ->
                                HomePilotPairingAcknowledgement.create(identityStore, response, now).toPayload()
                            }
                        )
                    }
                    busy = false
                    qrPayload = null
                    status = "Gerät erfolgreich gekoppelt."
                PeerSyncSignals.pairingRevision++
                    closeListener()
                } catch (error: Exception) {
                    busy = false
                    qrPayload = null
                    status = error.message ?: "Pairing-Rückkanal fehlgeschlagen."
                    closeListener()
                    active.cancel()
                }
            }, "HomePilot-Pairing-Listener").apply { isDaemon = true; start() }
        }.onFailure { error ->
            closeListener()
            busy = false
            status = error.message ?: "Einladung konnte nicht gestartet werden."
        }
    }

    fun startScan() {
        val activity = context as? Activity
        if (activity == null) {
            status = "Der QR-Scanner kann in diesem Kontext nicht geöffnet werden."
            return
        }
        busy = true
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
        runCatching { GmsBarcodeScanning.getClient(activity, options).startScan() }
            .onSuccess { task ->
                task.addOnSuccessListener { barcode ->
                    busy = false
                    val raw = barcode.rawValue
                    if (raw.isNullOrBlank()) {
                        status = "Der QR-Code enthält keine lesbaren Daten."
                        return@addOnSuccessListener
                    }
                    scannedInvitationPayload = raw
                    val confirmation = getController().prepareIncomingInvitation(raw)
                    if (confirmation == null) {
                        status = "Der QR-Code ist ungültig, abgelaufen oder gehört zu einer aktiven Sitzung."
                    } else {
                        pending = confirmation
                        qrPayload = null
                        status = "Tippe auf „Koppeln“, um die Geräte zu verbinden."
                    }
                }.addOnCanceledListener {
                    busy = false
                    status = "QR-Scan abgebrochen."
                }.addOnFailureListener { error ->
                    busy = false
                    status = error.message ?: "QR-Scanner konnte nicht gestartet werden."
                }
            }
            .onFailure { error ->
                busy = false
                status = error.message ?: "QR-Scanner ist nicht verfügbar."
            }
    }

    fun confirmPairing() {
        val confirmation = pending ?: return
        busy = true
        status = "Geräte werden gekoppelt …"
        Thread({
            try {
                val (network, _) = findWifi()
                val active = getController()
                val response = active.confirmIncomingInvitation(confirmation, displayName)
                val invitation = HomePilotPairingInvitation.parseAndVerify(
                    scannedInvitationPayload ?: throw IllegalStateException("Die Einladungssitzung ist nicht verfügbar.")
                ) ?: throw IllegalStateException("Die Einladungssitzung ist abgelaufen.")
                val acknowledgement = HomePilotPairingTcpChannel.exchange(
                    network.socketFactory, invitation.pairingHost, invitation.pairingPort, response
                )
                active.acknowledgeIncomingInvitation(acknowledgement)
                pending = null
                busy = false
                status = "Gerät erfolgreich gekoppelt."
                PeerSyncSignals.pairingRevision++
            } catch (error: Exception) {
                getController().cancel()
                pending = null
                busy = false
                status = error.message ?: "Kopplung fehlgeschlagen. Es wurde kein Erfolg gespeichert."
            }
        }, "HomePilot-Pairing-Client").apply { isDaemon = true; start() }
    }

    DisposableEffect(Unit) {
        onDispose { closeListener(); controller?.cancel() }
    }

    val qrBitmap = remember(qrPayload) {
        qrPayload?.let { payload -> runCatching { HomePilotPairingQrCode.encode(payload, 640).toBitmap() }.getOrNull() }
    }
    val pairedCount = trustedStore.trustedPeers().size
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // Keep the section open while a pairing session is running.
    val open = expanded || pending != null || qrBitmap != null || busy
    SettingsMenuSection(
        panel, text, "Smartphones koppeln",
        if (pairedCount == 0) "Konten, Räume, Geräte und Profile direkt zwischen Smartphones abgleichen"
        else "Gekoppelte Geräte: $pairedCount · Abgleich im WLAN alle 30 s",
        Icons.Default.PhonelinkRing, open, { expanded = !open }
    ) {
        OutlinedTextField(value = displayName, onValueChange = { displayName = it.take(48) }, label = { Text("Name dieses Geräts") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (pending != null) {
            Text("Mit diesem Gerät koppeln?", color = text, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { confirmPairing() }, enabled = !busy && displayName.isNotBlank(), modifier = Modifier.weight(1f)) { Text(if (busy) "Kopple …" else "Koppeln") }
                OutlinedButton(onClick = { cancelSession("Kopplung abgebrochen. Es wurde kein Gerät gespeichert.") }) { Text("Abbrechen") }
            }
        } else if (qrBitmap != null) {
            Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "HomePilot-Kopplungs-QR-Code", modifier = Modifier.size(240.dp).align(Alignment.CenterHorizontally), contentScale = ContentScale.Fit)
            Text("Mit dem anderen Smartphone scannen. Gültig 2 Minuten.", color = muted, fontSize = 11.sp)
            OutlinedButton(onClick = { cancelSession("Kopplung abgebrochen.") }, modifier = Modifier.fillMaxWidth()) { Text("Kopplung abbrechen") }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { startInvitation() }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("QR-Code anzeigen", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                OutlinedButton(onClick = { startScan() }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("QR-Code scannen", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
            }
        }
        Text(status, color = if (status.contains("ungültig") || status.contains("fehlgeschlagen") || status.contains("abgebrochen")) MaterialTheme.colorScheme.error else muted, fontSize = 11.sp)
        if (pairedCount > 0 && PeerSyncSignals.lastStatus.isNotBlank()) {
            Text("Letzter Abgleich: ${PeerSyncSignals.lastStatus}", color = muted, fontSize = 11.sp)
        }
    }
}

private fun BitMatrix.toBitmap(): Bitmap {
    val pixels = IntArray(width * height) { index ->
        val x = index % width
        val y = index / width
        if (this[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}
