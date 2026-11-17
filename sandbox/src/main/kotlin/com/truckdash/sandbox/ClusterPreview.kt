package com.truckdash.sandbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.truckdash.obd.ClusterPhase
import com.truckdash.obd.ClusterState
import com.truckdash.obd.GaugeId
import com.truckdash.obd.ObdFailure
import com.truckdash.obd.TransStage
import com.truckdash.ui.Dash
import com.truckdash.ui.DashTab
import com.truckdash.ui.DesignScale
import com.truckdash.ui.Hairline
import com.truckdash.ui.TabBar
import com.truckdash.ui.cluster.ClusterView

/** Hand-made Cluster states for screenshots and render tests (no coroutines, no timing). */
object ClusterPreview {
    private val liveValues = mapOf(GaugeId.Trans to 87f, GaugeId.Load to 46f, GaugeId.Throttle to 22f, GaugeId.Intake to 24f)

    val states: List<Pair<String, ClusterState>> = listOf(
        "connecting" to ClusterState(phase = ClusterPhase.Connecting),
        "failed-unpaired" to ClusterState(phase = ClusterPhase.Failed, failure = ObdFailure.NoPairedDevice),
        "failed-btoff" to ClusterState(phase = ClusterPhase.Failed, failure = ObdFailure.BluetoothOff, retrying = true),
        "failed-connection" to ClusterState(phase = ClusterPhase.Failed, failure = ObdFailure.ConnectionFailed),
        "live" to ClusterState(phase = ClusterPhase.Live, device = "V-LINK", values = liveValues),
        "live-warn" to ClusterState(
            phase = ClusterPhase.Live, device = "V-LINK",
            values = mapOf(GaugeId.Trans to 121f, GaugeId.Load to 94f, GaugeId.Throttle to 88f, GaugeId.Intake to 41f),
        ),
        "live-critical" to ClusterState(
            phase = ClusterPhase.Live, device = "V-LINK",
            values = mapOf(GaugeId.Trans to 134f, GaugeId.Load to 97f, GaugeId.Throttle to 100f, GaugeId.Intake to -12f),
        ),
        "trans-physical" to ClusterState(
            phase = ClusterPhase.Live, device = "V-LINK", transStage = TransStage.Physical, values = liveValues - GaugeId.Trans,
        ),
        "trans-physical-live" to ClusterState(
            phase = ClusterPhase.Live, device = "V-LINK", transStage = TransStage.Physical, transConfirmed = true, values = liveValues,
        ),
        "failed-permission-settings" to ClusterState(
            phase = ClusterPhase.Failed, failure = ObdFailure.PermissionNeeded, permissionViaSettings = true,
        ),
        "fallback-coolant" to ClusterState(
            phase = ClusterPhase.Live, device = "V-LINK", transStage = TransStage.Fallback,
            values = liveValues + (GaugeId.Trans to 92f),
        ),
    )

    /** The Cluster tab as the app shows it: screen, hairline, four-tab bar with Cluster selected. */
    @Composable
    fun Shell(state: ClusterState, dongleChosen: Boolean = false) {
        DesignScale {
            Column(Modifier.fillMaxSize().background(Dash.bg)) {
                Box(Modifier.weight(1f)) { ClusterView(state, dongleChosen, {}, {}) }
                Hairline()
                TabBar(DashTab.visible(true), DashTab.Cluster, locked = false) {}
            }
        }
    }
}
