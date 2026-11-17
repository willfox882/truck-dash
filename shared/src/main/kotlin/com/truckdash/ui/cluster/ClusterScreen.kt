package com.truckdash.ui.cluster

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truckdash.obd.ClusterGauges
import com.truckdash.obd.ClusterPhase
import com.truckdash.obd.ClusterPresenter
import com.truckdash.obd.ClusterState
import com.truckdash.obd.ClusterText
import com.truckdash.obd.PairedDevice
import com.truckdash.ui.Dash
import com.truckdash.ui.DashDialog
import com.truckdash.ui.Dot
import com.truckdash.ui.Hairline
import com.truckdash.ui.Label
import com.truckdash.ui.LocalPaneSpec
import com.truckdash.ui.PillButton
import com.truckdash.ui.PillKind
import com.truckdash.ui.ScreenTitle
import com.truckdash.ui.TextAction
import com.truckdash.ui.pane

/** Stateful wrapper: reads the presenter, owns the dongle picker dialog. */
@Composable
fun ClusterScreen(vm: ClusterPresenter, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    var picking by remember { mutableStateOf<List<PairedDevice>?>(null) }
    ClusterView(
        state, vm.selectedAddress != null, onRetry = vm::retry,
        onChooseDongle = { picking = vm.pairedDevices() }, modifier = modifier,
    )
    picking?.let { devices ->
        DonglePickerDialog(devices, vm.selectedAddress, onDismiss = { picking = null }) { vm.selectDevice(it); picking = null }
    }
}

/** Stateless Cluster screen (used directly by the sandbox screenshots and render tests). */
@Composable
fun ClusterView(
    state: ClusterState,
    dongleChosen: Boolean,
    onRetry: () -> Unit,
    onChooseDongle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        if (state.phase == ClusterPhase.Failed) FailedView(state, dongleChosen, onRetry, onChooseDongle)
        else DialsView(state, dongleChosen, onChooseDongle)
    }
}

@Composable
private fun StatusLine(state: ClusterState, modifier: Modifier = Modifier) {
    val st = ClusterText.status(state)
    val color = when (st.tone) {
        ClusterText.Tone.Ok -> Dash.green
        ClusterText.Tone.Caution -> Dash.amber
        ClusterText.Tone.Neutral -> Dash.faint
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Dot(color, pane(8, 7))
        Text(
            st.text, color = if (st.tone == ClusterText.Tone.Caution) Dash.amber else Dash.dim, fontSize = pane(14.sp, 12.sp, 12.sp),
            maxLines = 1, modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun DongleRow(state: ClusterState, chosen: Boolean, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    val name = state.device
    val text = when {
        name != null && !chosen -> "$name · auto"
        name != null -> name
        chosen -> "Chosen device"
        else -> "Automatic"
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label("Dongle")
        Text(text, color = Dash.dim, fontSize = pane(14.sp, 12.sp), maxLines = 1, modifier = Modifier.padding(start = 10.dp).weight(1f))
        TextAction("Change", onChoose, color = Dash.text)
    }
}

@Composable
private fun DialsView(state: ClusterState, dongleChosen: Boolean, onChooseDongle: () -> Unit) {
    val p = LocalPaneSpec.current
    val specs = ClusterGauges.forStage(state.transStage)
    val values = specs.map { state.value(it.id) }
    if (p.tiny) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.Center) {
            StatusLine(state, Modifier.padding(bottom = 14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                specs.forEachIndexed { i, s -> ClusterReadout(s, values[i], Modifier.weight(1f)) }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = pane(20.dp, 16.dp))) {
        if (p.compact) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Cluster")
                Spacer(Modifier.weight(1f))
                StatusLine(state)
            }
        } else {
            ScreenTitle("Cluster", Modifier.padding(top = 18.dp))
            StatusLine(state, Modifier.padding(top = 6.dp))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val gap = pane(12.dp, 10.dp)
            // compact dials carry their label under the circle (the bottom wedge is too narrow for it), tucked up a little
            val labelH = if (p.compact) 14.dp else 0.dp
            val side = minOf((maxWidth - gap) / 2, ((maxHeight - gap) / 2 - labelH) / (if (p.compact) 0.91f else 1f))
            val tuck = if (p.compact) -(side * 0.09f) else 0.dp
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                for (row in 0..1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (col in 0..1) {
                            val i = row * 2 + col
                            val spec = specs[i]
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(tuck)) {
                                ClusterDial(
                                    spec, values[i], Modifier.size(side),
                                    numeralSize = if (p.compact) (side.value * 0.22f).sp else (side.value * 0.23f).sp,
                                    showUnit = !p.compact,
                                    labelText = if (p.compact) "" else spec.label,
                                )
                                if (p.compact) {
                                    // same look as Label, with a fixed line height so the two rows fit the pane exactly
                                    Text(
                                        "${spec.short} ${spec.unit}".uppercase(), color = Dash.faint, fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium, letterSpacing = 1.3.sp, lineHeight = 14.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        DongleRow(state, dongleChosen, onChooseDongle, Modifier.padding(bottom = pane(12.dp, 4.dp)))
    }
}

@Composable
private fun FailedView(state: ClusterState, dongleChosen: Boolean, onRetry: () -> Unit, onChooseDongle: () -> Unit) {
    val p = LocalPaneSpec.current
    val reason = ClusterText.reason(state.failure ?: com.truckdash.obd.ObdFailure.ConnectionFailed)
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = pane(36.dp, 24.dp, 24.dp)),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!p.tiny) Dot(Dash.amber, pane(9, 8))
            Text(
                ClusterText.FAILED_TITLE, color = Dash.text, fontSize = pane(24.sp, 19.sp, 16.sp), fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, lineHeight = pane(30.sp, 24.sp, 20.sp),
                modifier = Modifier.padding(top = pane(18.dp, 8.dp, 0.dp)),
            )
            Text(
                reason, color = Dash.dim, fontSize = pane(15.sp, 13.sp, 12.sp), textAlign = TextAlign.Center,
                lineHeight = pane(22.sp, 18.sp, 16.sp),
                modifier = Modifier.padding(top = pane(8.dp, 4.dp, 3.dp)),
            )
            PillButton(
                "Retry", onRetry, Modifier.padding(top = pane(24.dp, 12.dp, 8.dp)), kind = PillKind.Primary, small = p.compact,
            )
            if (!p.tiny) StatusLine(state, Modifier.padding(top = pane(20.dp, 10.dp)))
        }
        if (!p.tiny) {
            Hairline()
            DongleRow(state, dongleChosen, onChooseDongle, Modifier.padding(horizontal = pane(20.dp, 16.dp), vertical = pane(8.dp, 2.dp)))
        }
    }
}

/** Pick "Automatic" (match by name) or one specific bonded device. Applied with Save. */
@Composable
fun DonglePickerDialog(devices: List<PairedDevice>, selected: String?, onDismiss: () -> Unit, onSave: (String?) -> Unit) {
    var choice by remember { mutableStateOf(selected) }
    DashDialog("OBD dongle", onDismiss, "Save", { onSave(choice) }) {
        PickRow("Automatic", "Picks a paired OBD, ELM or V-LINK adapter by name", choice == null) { choice = null }
        devices.forEach { d -> Hairline(); PickRow(d.name.ifBlank { d.address }, d.address, choice.equals(d.address, true)) { choice = d.address } }
        if (devices.isEmpty()) {
            Text("No paired Bluetooth devices. Pair the adapter in the unit's Bluetooth settings.", color = Dash.faint, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PickRow(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = pane(8.dp, 6.dp)), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (selected) Dash.text else Dash.dim, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = Dash.faint, fontSize = 12.sp)
        }
        if (selected) Dot(Dash.text, 8)
    }
}
