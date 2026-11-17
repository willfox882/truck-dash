package com.truckdash.obd

import com.truckdash.model.Settings
import com.truckdash.ui.DashTab
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePlatform(permission: Boolean = true, active: Boolean = true) : ClusterPlatform {
    val activeFlow = MutableStateFlow(active)
    override val appActive: StateFlow<Boolean> = activeFlow
    var permission = permission
    var permissionRequests = 0
    var pendingResult: ((Boolean) -> Unit)? = null
    val links = mutableListOf<ScriptLink>()
    val addresses = mutableListOf<String?>()
    val log = mutableListOf<String>()
    var failure: ObdFailure? = null

    override fun hasPermission() = permission
    override fun requestPermission(onResult: (Boolean) -> Unit) { permissionRequests++; pendingResult = onResult }
    override fun newLink(preferredAddress: String?): ObdLink {
        addresses += preferredAddress
        return ScriptLink(log, failure, 0) { healthyReply(it) }.also { links += it }
    }
    override fun pairedDevices() = listOf(PairedDevice("OBDII", "BB:11"))
}

@OptIn(ExperimentalCoroutinesApi::class)
class ClusterPresenterTests {
    private val settings = MutableStateFlow(Settings())

    private fun TestScope.presenter(p: FakePlatform, saved: MutableList<String?> = mutableListOf()) =
        ClusterPresenter(
            p, settings, { a -> saved += a; settings.value = settings.value.copy(obdAddress = a) },
            backgroundScope, StandardTestDispatcher(testScheduler),
        )

    // ---- the tab is hidden by default and a hidden tab means no Bluetooth at all --------------------------------------

    @Test fun clusterTabIsOffByDefaultAndHiddenFromTheTabBar() {
        assertFalse(Settings().showCluster)
        assertNull(Settings().obdAddress)
        assertEquals(listOf(DashTab.Live, DashTab.Trips, DashTab.Health), DashTab.visible(clusterShown = Settings().showCluster))
        assertEquals(listOf(DashTab.Live, DashTab.Trips, DashTab.Health, DashTab.Cluster), DashTab.visible(clusterShown = true))
        assertEquals("CLUSTER", DashTab.Cluster.label)
    }

    @Test fun creatingThePresenterDoesNothingWithBluetooth() = runTest {
        val p = FakePlatform(permission = false)
        presenter(p)
        advanceTimeBy(60_000); runCurrent()
        assertTrue(p.links.isEmpty())
        assertEquals(0, p.permissionRequests)
        assertTrue(p.log.isEmpty())
    }

    @Test fun neverShownMeansNoLinkEvenWhileTheAppIsActive() = runTest {
        val p = FakePlatform()
        val vm = presenter(p)
        p.activeFlow.value = false; runCurrent(); p.activeFlow.value = true; runCurrent()
        vm.setTabVisible(false)
        advanceTimeBy(30_000); runCurrent()
        assertTrue(p.links.isEmpty())
    }

    // ---- connect only while visible and active -------------------------------------------------------------------------

    @Test fun connectsWhenTabOpensAndDisconnectsWhenItCloses() = runTest {
        val p = FakePlatform()
        val vm = presenter(p)
        vm.setTabVisible(true)
        advanceTimeBy(700); runCurrent()
        assertEquals(1, p.links.size)
        assertEquals(ClusterPhase.Live, vm.state.value.phase)
        vm.setTabVisible(false); runCurrent()
        assertTrue(p.links[0].closed)
        assertEquals(ClusterPhase.Idle, vm.state.value.phase)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(1, p.links.size)
    }

    @Test fun backgroundingTheAppDisconnectsAndResumingReconnects() = runTest {
        val p = FakePlatform()
        val vm = presenter(p)
        vm.setTabVisible(true)
        advanceTimeBy(700); runCurrent()
        p.activeFlow.value = false; runCurrent()
        assertTrue(p.links[0].closed)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(1, p.links.size)
        p.activeFlow.value = true; runCurrent()
        advanceTimeBy(700); runCurrent()
        assertEquals(2, p.links.size)
        assertEquals(ClusterPhase.Live, vm.state.value.phase)
    }

    @Test fun inactiveAppNeverConnectsEvenIfTheTabIsShown() = runTest {
        val p = FakePlatform(active = false)
        val vm = presenter(p)
        vm.setTabVisible(true)
        advanceTimeBy(30_000); runCurrent()
        assertTrue(p.links.isEmpty())
    }

    @Test fun quicklyReopeningTheTabNeverRunsTwoSessionsAtOnce() = runTest {
        val p = FakePlatform()
        val vm = presenter(p)
        repeat(5) { vm.setTabVisible(true); runCurrent(); vm.setTabVisible(false); runCurrent() }
        vm.setTabVisible(true)
        advanceTimeBy(700); runCurrent()
        assertEquals(ClusterPhase.Live, vm.state.value.phase)
        assertEquals(1, p.links.count { !it.closed })
    }

    // ---- permission -----------------------------------------------------------------------------------------------------

    @Test fun permissionIsRequestedOnlyWhenTheTabIsOpened() = runTest {
        val p = FakePlatform(permission = false)
        val vm = presenter(p)
        runCurrent()
        assertEquals(0, p.permissionRequests)
        vm.setTabVisible(true); runCurrent()
        assertEquals(1, p.permissionRequests)
        advanceTimeBy(20_000); runCurrent()
        assertEquals(1, p.permissionRequests)                 // the 5 s retry loop does not nag
        vm.setTabVisible(false); runCurrent(); vm.setTabVisible(true); runCurrent()
        assertEquals(2, p.permissionRequests)                 // asked again on the next open
    }

    @Test fun grantingThePermissionRetriesAtOnce() = runTest {
        val p = FakePlatform(permission = false)
        p.failure = ObdFailure.PermissionNeeded
        val vm = presenter(p)
        vm.setTabVisible(true); runCurrent()
        assertEquals(ObdFailure.PermissionNeeded, vm.state.value.failure)
        assertEquals(1, p.links.size)
        p.failure = null; p.permission = true
        p.pendingResult!!(true); runCurrent()
        advanceTimeBy(700); runCurrent()
        assertEquals(2, p.links.size)
        assertEquals(ClusterPhase.Live, vm.state.value.phase)
    }

    // ---- states and reasons ---------------------------------------------------------------------------------------------

    @Test fun failedStateCarriesAPlainReasonAndKeepsRetrying() = runTest {
        val p = FakePlatform()
        p.failure = ObdFailure.NoPairedDevice
        val vm = presenter(p)
        vm.setTabVisible(true); runCurrent()
        assertEquals(ClusterPhase.Failed, vm.state.value.phase)
        assertEquals("Could not connect to the OBD dongle", ClusterText.FAILED_TITLE)
        assertTrue(ClusterText.reason(vm.state.value.failure!!).contains("Pair it in the unit's Bluetooth settings"))
        advanceTimeBy(10_100); runCurrent()
        assertEquals(3, p.links.size)
        vm.retry(); runCurrent()
        assertEquals(4, p.links.size)
    }

    @Test fun everyReasonIsASentenceWithoutEmDashesOrImperialUnits() {
        for (f in ObdFailure.entries) {
            val r = ClusterText.reason(f)
            assertTrue(f.name, r.length > 15 && r.endsWith("."))
            assertFalse(r.contains(Char(0x2014)))
        }
        assertTrue(ClusterText.reason(ObdFailure.BluetoothOff).contains("Bluetooth is off"))
        assertTrue(ClusterText.reason(ObdFailure.ConnectionFailed).contains("Connection failed"))
    }

    @Test fun stagedTransMessagesMatchTheSpec() {
        val live = ClusterState(phase = ClusterPhase.Live, values = mapOf(GaugeId.Load to 10f))
        assertEquals("Live", ClusterText.status(live).text)
        assertEquals(ClusterText.Tone.Ok, ClusterText.status(live).tone)
        assertEquals("Trans temp: asking the transmission module", ClusterText.status(live.copy(transStage = TransStage.Physical)).text)
        assertEquals("Showing coolant temp instead", ClusterText.status(live.copy(transStage = TransStage.Fallback)).text)
        assertEquals(ClusterText.Tone.Caution, ClusterText.status(live.copy(transStage = TransStage.Fallback)).tone)
        assertEquals("Connecting to the OBD dongle", ClusterText.status(ClusterState(phase = ClusterPhase.Connecting)).text)
    }

    // ---- dongle choice --------------------------------------------------------------------------------------------------

    @Test fun choosingADongleSavesItAndReconnectsToIt() = runTest {
        val p = FakePlatform()
        val saved = mutableListOf<String?>()
        val vm = presenter(p, saved)
        vm.setTabVisible(true)
        advanceTimeBy(700); runCurrent()
        assertNull(p.addresses.last())
        assertEquals(listOf(PairedDevice("OBDII", "BB:11")), vm.pairedDevices())
        vm.selectDevice("BB:11"); runCurrent()
        advanceTimeBy(700); runCurrent()
        assertEquals(listOf<String?>("BB:11"), saved)
        assertEquals("BB:11", vm.selectedAddress)
        assertEquals("BB:11", p.addresses.last())
        assertTrue(p.links[0].closed)
        assertEquals(ClusterPhase.Live, vm.state.value.phase)
        vm.selectDevice(null); runCurrent(); advanceTimeBy(700); runCurrent()
        assertNull(p.addresses.last())
    }

    @Test fun choosingADongleWhileTheTabIsClosedDoesNotConnect() = runTest {
        val p = FakePlatform()
        val vm = presenter(p)
        vm.selectDevice("BB:11"); advanceTimeBy(10_000); runCurrent()
        assertTrue(p.links.isEmpty())
    }
}

class ClusterGaugeSpecTests {
    @Test fun temperaturesAreCelsiusWithSensibleRangesAndWarnPoints() {
        assertEquals("°C", ClusterGauges.TRANS.unit)
        assertEquals(118f, ClusterGauges.TRANS.warn!!, 0f)
        assertEquals(118f, ClusterGauges.COOLANT.warn!!, 0f)
        assertEquals("°C", ClusterGauges.INTAKE.unit)
        for (g in listOf(ClusterGauges.TRANS, ClusterGauges.COOLANT, ClusterGauges.INTAKE)) {
            assertTrue("${g.label} range", g.max <= 150f && g.min >= -40f && g.max > g.min)
        }
        assertEquals("%", ClusterGauges.LOAD.unit)
    }

    @Test fun colourIsMeaning() {
        val t = ClusterGauges.TRANS
        assertEquals(GaugeSpec.Level.None, t.level(null))
        assertEquals(GaugeSpec.Level.Ok, t.level(90f))
        assertEquals(GaugeSpec.Level.Warn, t.level(118f))
        assertEquals(GaugeSpec.Level.Critical, t.level(131f))
        assertEquals(GaugeSpec.Level.Ok, ClusterGauges.THROTTLE.level(100f))   // no thresholds: never coloured
        assertEquals(GaugeSpec.Level.Warn, ClusterGauges.LOAD.level(95f))
        assertEquals(0f, t.fraction(-50f), 0f)
        assertEquals(1f, t.fraction(500f), 0f)
    }

    @Test fun firstDialRelabelsAfterTheFallback() {
        assertEquals("Transmission", ClusterGauges.forStage(TransStage.Functional)[0].label)
        assertEquals("Transmission", ClusterGauges.forStage(TransStage.Physical)[0].label)
        val fb = ClusterGauges.forStage(TransStage.Fallback)
        assertEquals("Coolant", fb[0].label)
        assertEquals(GaugeId.Trans, fb[0].id)
        assertEquals(4, fb.size)
    }
}
