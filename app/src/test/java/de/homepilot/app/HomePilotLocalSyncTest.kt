package de.homepilot.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomePilotLocalSyncTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clear() {
        listOf("homepilot", "homepilot_accounts", "homepilot_sync_docs")
            .forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    private fun registry(revision: Long, vararg names: String) = HomePilotAccountRegistry(
        revision = revision,
        accounts = names.map { HomePilotAccount.create(it, it, "sicheresPasswort1".toCharArray(), HomePilotAccount.ADMIN) }
    )

    private fun config(revision: Long, updatedAt: Long, vararg rooms: String, lights: Set<String> = emptySet()) =
        HomePilotSharedConfig(revision = revision, updatedAt = updatedAt, rooms = rooms.toList(), importedLights = lights)

    private fun profile(revision: Long, updatedAt: Long, vararg favorites: String) =
        HomePilotUserProfile(revision = revision, updatedAt = updatedAt, favoriteIds = favorites.toSet())

    @Test
    fun accountRegistryHigherRevisionWinsAndEmptyRemoteIsIgnored() {
        val store = HomePilotAccountStore(context)
        assertTrue(store.mergeRemote(registry(3, "apropo")))
        assertTrue(store.mergeRemote(registry(5, "apropo", "partner")))
        assertFalse(store.mergeRemote(registry(4, "apropo")))
        assertFalse(store.mergeRemote(HomePilotAccountRegistry(revision = 9)))
        assertEquals(2, store.loadedRegistry()!!.accounts.size)
    }

    @Test
    fun accountRegistryTieIsDecidedDeterministically() {
        val first = registry(2, "apropo")
        val second = registry(2, "partner")
        // Beide Geräte müssen beim selben Ergebnis landen, egal in welcher Reihenfolge sie abgleichen.
        val storeA = HomePilotAccountStore(context)
        storeA.mergeRemote(first); storeA.mergeRemote(second)
        val resultA = storeA.loadedRegistry()!!.toJson().toString()
        clear()
        val storeB = HomePilotAccountStore(context)
        storeB.mergeRemote(second); storeB.mergeRemote(first)
        assertEquals(resultA, storeB.loadedRegistry()!!.toJson().toString())
    }

    @Test
    fun localSaveRequiresCurrentRevision() {
        val store = HomePilotAccountStore(context)
        assertTrue(store.saveInitial(HomePilotAccountRegistry()))
        val admin = HomePilotAccountRegistry().createInitialAdmin("apropo", "Apropo", "sicheresPasswort1".toCharArray())
        assertNull(store.save(0L, admin))
        assertNotNull(store.save(0L, admin))
        assertFalse(store.saveInitial(HomePilotAccountRegistry()))
    }

    @Test
    fun importOnlyReplacesMissingOrEmptyRegistry() {
        val store = HomePilotAccountStore(context)
        assertTrue(store.saveInitial(HomePilotAccountRegistry()))
        assertTrue(store.replaceEmptyWithImported(registry(4, "apropo")))
        assertFalse(store.replaceEmptyWithImported(registry(9, "fremd")))
        assertEquals("apropo", store.loadedRegistry()!!.accounts.single().username)
    }

    @Test
    fun corruptLocalRegistryIsNeverOverwritten() {
        context.getSharedPreferences("homepilot_accounts", Context.MODE_PRIVATE).edit()
            .putString("homepilot_account_registry_v1", "{kaputt").commit()
        val store = HomePilotAccountStore(context)
        assertTrue(store.load() is HomePilotAccountLoadResult.Invalid)
        assertFalse(store.mergeRemote(registry(7, "apropo")))
        assertFalse(store.replaceEmptyWithImported(registry(7, "apropo")))
        assertTrue(store.load() is HomePilotAccountLoadResult.Invalid)
    }

    @Test
    fun configAndProfilesMergeByRevisionThenTime() {
        val docs = HomePilotDocStore(context)
        assertTrue(docs.mergeConfig(config(2, 100, "Küche")))
        assertFalse(docs.mergeConfig(config(1, 999, "Bad")))
        assertTrue(docs.mergeConfig(config(2, 200, "Wohnzimmer")))
        assertFalse(docs.mergeConfig(config(2, 150, "Flur")))
        assertEquals(listOf("Wohnzimmer"), docs.config()!!.rooms)

        assertTrue(docs.mergeProfile("apropo", profile(1, 10, "a")))
        assertTrue(docs.mergeProfile("Apropo", profile(2, 5, "b")))
        assertEquals(setOf("b"), docs.profile("apropo")!!.favoriteIds)
        assertFalse(docs.mergeProfile("../böse", profile(9, 9, "x")))
        assertEquals(setOf("apropo"), docs.profiles().keys)
    }

    @Test
    fun peerSnapshotConvergesBothPhones() {
        // Smartphone A: Konten und neuere Konfiguration, ein Profil.
        HomePilotAccountStore(context).mergeRemote(registry(4, "apropo", "partner"))
        HomePilotDocStore(context).putLocalConfig(config(6, 600, "Küche", "Garage", lights = setOf("hue.0.lampe1")))
        HomePilotDocStore(context).putLocalProfile("apropo", profile(3, 300, "shelly.0.a"))
        IoBrokerAddress.store(context.getSharedPreferences("homepilot", Context.MODE_PRIVATE), "192.168.178.54")
        val snapshotA = HomePilotPeerDataSync(context).buildSnapshot()

        // Smartphone B: älterer Stand, aber neueres Profil eines anderen Benutzers.
        clear()
        HomePilotAccountStore(context).mergeRemote(registry(2, "apropo"))
        HomePilotDocStore(context).putLocalConfig(config(5, 500, "Bad"))
        HomePilotDocStore(context).putLocalProfile("partner", profile(7, 700, "hue.0.lampe1"))
        val snapshotB = HomePilotPeerDataSync(context).buildSnapshot()

        val result = HomePilotPeerDataSync(context).applySnapshot(JSONObject(snapshotA.toString()))
        assertTrue(result.accountsChanged)
        assertTrue(result.configChanged)
        assertEquals(1, result.profilesChanged)
        assertEquals(2, HomePilotAccountStore(context).loadedRegistry()!!.accounts.size)
        assertEquals(listOf("Küche", "Garage"), HomePilotDocStore(context).config()!!.rooms)
        assertEquals(setOf("hue.0.lampe1"), HomePilotDocStore(context).config()!!.importedLights)
        assertEquals(setOf("shelly.0.a"), HomePilotDocStore(context).profile("apropo")!!.favoriteIds)
        assertEquals(setOf("hue.0.lampe1"), HomePilotDocStore(context).profile("partner")!!.favoriteIds)
        assertEquals("ws://192.168.178.54:8084", context.getSharedPreferences("homepilot", Context.MODE_PRIVATE).getString("server", null))

        // Ein zweiter Abgleich mit demselben Stand ändert nichts mehr.
        assertFalse(HomePilotPeerDataSync(context).applySnapshot(JSONObject(snapshotA.toString())).anyChange)
        // Der ältere Stand von B kann A nicht zurückdrehen.
        val back = HomePilotPeerDataSync(context).applySnapshot(JSONObject(snapshotB.toString()))
        assertFalse(back.accountsChanged || back.configChanged)
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompatibleProtocolIsRejected() {
        HomePilotPeerDataSync(context).applySnapshot(JSONObject().put("v", 99))
    }

    @Test
    fun setupQrRoundTripAndExpiry() {
        val now = 1_700_000_000_000L
        val raw = HomePilotSetupTransfer.create(registry(3, "apropo"), "192.168.178.54:8084", now)
        val parsed = HomePilotSetupTransfer.parse(raw, now + 60_000)!!
        assertEquals("apropo", parsed.registry.accounts.single().username)
        assertEquals("ws://192.168.178.54:8084", parsed.ioBrokerUrl)
        assertFalse(raw.contains("sicheresPasswort1"))
        assertNull(HomePilotSetupTransfer.parse(raw, now + HomePilotSetupTransfer.VALIDITY_MS + 1))
        assertNull(HomePilotSetupTransfer.parse("KLIMAPILOT-SETUP:{}", now))
        assertNull(HomePilotSetupTransfer.parse(HomePilotSetupTransfer.create(HomePilotAccountRegistry(), null, now), now))
    }

    @Test
    fun addressNormalization() {
        assertEquals("ws://192.168.178.54:8084", IoBrokerAddress.normalize("192.168.178.54"))
        assertEquals("ws://host:9000", IoBrokerAddress.normalize("host:9000/"))
        assertEquals("wss://host:8084", IoBrokerAddress.normalize("wss://host"))
        assertNull(IoBrokerAddress.normalize("http://host"))
        assertNull(IoBrokerAddress.normalize("host:99999"))
        assertNull(IoBrokerAddress.normalize(" "))
    }

    @Test
    fun olderConfigWithoutImportedLightsStillParses() {
        val legacy = JSONObject(config(4, 400, "Küche").toJson().toString()).apply { remove("importedLights") }
        val parsed = HomePilotSharedConfig.fromJson(legacy)!!
        assertEquals(emptySet<String>(), parsed.importedLights)
        assertEquals(4L, parsed.revision)
    }
}
