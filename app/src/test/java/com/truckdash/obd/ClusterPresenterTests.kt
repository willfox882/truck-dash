package com.truckdash.obd

import com.truckdash.model.Settings
import com.truckdash.ui.DashTab
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.TestDispatcher
import kotlin.coroutines.CoroutineContext
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
    var canRequest = true
    var settingsOpened = 0
    var calls = 0
    var maxLive = 0

    override fun hasPermission(): Boolean { calls++; return permission }
    override fun canRequestPermission(): Boolean { calls++; return canRequest }
    override fun openPermissionSettings() { calls++; settingsOpened++ }
    override fun requestPermission(onResult: (Boolean) -> Unit) { calls++; permissionRequests++; pendingResult = onResult }
    override fun newLink(preferredAddress: String?): ObdLink {
        calls++
        addresses += preferredAddress
        return ScriptLink(log, failure, 0) { cmd ->
            maxLive = maxOf(maxLive, links.count { it.opened && !it.closed })
            healthyReply(cmd)
        }.also { links += it }
    }
    override fun pairedDevices(): List<PairedDevice> { calls++; return listOf(PairedDevice("OBDII", "BB:11")) }
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

    @Test fun creatingAndNeverOpeningMakesNoPlatformCallAtAll() = runTest {
        val p = FakePlatform(permission = false)
        val vm = presenter(p)
        advanceTimeBy(60_000); runCurrent()
        vm.setTabVisible(false)
        p.activeFlow.value = false; runCurrent(); p.activeFlow.value = true; runCurrent()
        assertEquals(0, p.calls)
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

    /**
     * Runs queued tasks newest first, like a busy multi-threaded pool can: a job cancelled before its body started
     * completes before the older run that is still closing its link. Delays are the test scheduler's.
     */
    @OptIn(InternalCoroutinesApi::class)
    private class NewestFirstDispatcher(private val inner: TestDispatcher) : CoroutineDispatcher(), Delay by inner {
        private val pending = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            pending.addLast(block)
            inner.dispatch(context, Runnable { while (pending.isNotEmpty()) pending.removeLast().run() })
        }
    }

    @Test fun cancelledBeforeStartNeverLetsTwoSessionsOverlap() = runTest {
        for (reverse in listOf(false, true)) {
            val p = FakePlatform()
            val io = StandardTestDispatcher(testScheduler)
            val vm = ClusterPresenter(
                p, MutableStateFlow(Settings()), {}, backgroundScope,
                if (reverse) NewestFirstDispatcher(io) else io,
            )
            vm.setTabVisible(true)
            advanceTimeBy(700); runCurrent()                       // session 1 is live
            assertEquals(ClusterPhase.Live, vm.state.value.phase)
            // flip WITHOUT running anything in between: the middle jobs are cancelled before their body starts
            vm.setTabVisible(false); vm.setTabVisible(true); vm.setTabVisible(false); vm.setTabVisible(true)
            vm.setTabVisible(false); vm.setTabVisible(true)
            advanceTimeBy(2_000); runCurrent()
            assertEquals("reverse=$reverse", ClusterPhase.Live, vm.state.value.phase)
            assertEquals("reverse=$reverse", 1, p.links.count { !it.closed })
            assertEquals("reverse=$reverse", 1, p.maxLive)
            vm.setTabVisible(false); runCurrent()
            assertEquals(0, p.links.count { !it.closed })
        }
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

    @Test fun retryAfterADenialAsksAgainWhileTheSystemStillShowsTheDialog() = runTest {
        val p = FakePlatform(permission = false)
        p.failure = ObdFailure.PermissionNeeded
        val vm = presenter(p)
        vm.setTabVisible(true); runCurrent()
        assertEquals(1, p.permissionRequests)
        p.pendingResult!!(false); runCurrent()                  // user said no, rationale still possible
        assertFalse(vm.state.value.permissionViaSettings)
        vm.retry(); runCurrent()
        assertEquals(2, p.permissionRequests)
        assertEquals(0, p.settingsOpened)
    }

    @Test fun retryOpensTheAppSettingsWhenAndroidWillNotAskAgain() = runTest {
        val p = FakePlatform(permission = false)
        p.failure = ObdFailure.PermissionNeeded
        val vm = presenter(p)
        vm.setTabVisible(true); runCurrent()
        p.canRequest = false                                    // denied and "don't ask again"
        p.pendingResult!!(false); runCurrent()
        assertTrue(vm.state.value.permissionViaSettings)
        val reason = ClusterText.reason(vm.state.value.failure!!, vm.state.value.permissionViaSettings)
        assertTrue(reason, reason.contains("settings"))
        assertEquals(0, p.settingsOpened)                       // never opened behind the user's back
        vm.retry(); runCurrent()
        assertEquals(1, p.settingsOpened)
        assertEquals(1, p.permissionRequests)                   // no dialog attempt that cannot show
        p.permission = true; p.failure = null                   // allowed in settings, back in the app
        vm.retry(); runCurrent(); advanceTimeBy(700); runCurrent()
        assertEquals(ClusterPhase.Live, vm.state.value.phase)
        assertEquals(1, p.settingsOpened)
    }

    @Test fun openingTheTabWithAPermanentDenialSaysSoWithoutOpeningSettings() = runTest {
        val p = FakePlatform(permission = false)
        p.failure = ObdFailure.PermissionNeeded
        p.canRequest = false
        val vm = presenter(p)
        vm.setTabVisible(true); runCurrent()
        assertTrue(vm.state.value.permissionViaSettings)
        assertEquals(0, p.permissionRequests)
        assertEquals(0, p.settingsOpened)
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
        // once the physical stage has answered it is just a live truck
        val confirmed = live.copy(transStage = TransStage.Physical, transConfirmed = true, device = "V-LINK")
        assertEquals("Live · V-LINK", ClusterText.status(confirmed).text)
        assertEquals(ClusterText.Tone.Ok, ClusterText.status(confirmed).tone)
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
        assertEquals(116f, ClusterGauges.TRANS.warn!!, 0f)       // original 240 F
        assertEquals(118f, ClusterGauges.COOLANT.warn!!, 0f)
        assertEquals(54f, ClusterGauges.INTAKE.warn!!, 0f)       // original 130 F
        assertEquals(-40f, ClusterGauges.INTAKE.min, 0f)
        assertEquals(80f, ClusterGauges.INTAKE.max, 0f)
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
        assertEquals(GaugeSpec.Level.Ok, t.level(115f))
        assertEquals(GaugeSpec.Level.Warn, t.level(116f))
        assertEquals(GaugeSpec.Level.Warn, ClusterGauges.INTAKE.level(54f))
        assertEquals(GaugeSpec.Level.Ok, ClusterGauges.INTAKE.level(30f))
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
