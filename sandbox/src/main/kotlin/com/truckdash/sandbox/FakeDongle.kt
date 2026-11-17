package com.truckdash.sandbox

import com.truckdash.obd.ClusterPlatform
import com.truckdash.obd.ObdFailure
import com.truckdash.obd.ObdLink
import com.truckdash.obd.ObdLinkException
import com.truckdash.obd.ObdTimeoutException
import com.truckdash.obd.PairedDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sin

/** What the simulated ELM327 does. Switchable live from the sandbox control panel. */
enum class DongleScenario(val title: String, val blurb: String) {
    Healthy("Healthy", "Answers everything, with the odd SEARCHING..., NO DATA and garbage line mixed in."),
    Absent("Absent", "Cannot connect (adapter unplugged or out of range)."),
    Drops("Drops mid-session", "Link dies about 40 commands after connecting, then recovers on the next try."),
    TransSilent("Trans silent", "Mode 22 trans temp never answers: functional, physical (7E1), then coolant."),
    TransTcmOnly("Trans via TCM", "Trans temp answers only when addressed to the TCM (ATSH7E1): stops at the physical stage."),
}

/**
 * Simulated Bluetooth adapter + ELM327. [newLink] gives one [FakeLink] per connection attempt, like the real thing.
 * Replies are raw ELM text (SEARCHING..., NO DATA, spaces off after ATS0), so the real [com.truckdash.obd.ObdProtocol]
 * parser is what gets exercised. [latencyMs] is per command and uses coroutine `delay`, so tests run in virtual time.
 */
class FakeDongle(initial: DongleScenario = DongleScenario.Healthy, val latencyMs: Long = 30) {
    val scenario = MutableStateFlow(initial)

    /** Links opened and commands answered, for tests and the control panel. */
    @Volatile var opens = 0
    @Volatile var commands = 0
    private var dropArmed = true

    fun select(s: DongleScenario) {
        scenario.value = s
        dropArmed = s == DongleScenario.Drops
    }

    fun newLink(): ObdLink = FakeLink()

    private inner class FakeLink : ObdLink {
        override var deviceName: String? = null
            private set
        private var opened = false
        private var closed = false
        private var pending: String? = null
        private var spaces = true
        private var header = "7DF"
        private var served = 0
        private var willDrop = false

        override suspend fun open() {
            opens++
            delay(300)
            if (scenario.value == DongleScenario.Absent) throw ObdLinkException(ObdFailure.ConnectionFailed, "fake: no adapter")
            willDrop = scenario.value == DongleScenario.Drops && dropArmed
            opened = true
            deviceName = "V-LINK"
        }

        override suspend fun write(text: String) {
            check(opened && !closed) { "write on a closed link" }
            pending = text.trim()
        }

        override suspend fun readUntilPrompt(timeoutMs: Long): String {
            check(opened && !closed) { "read on a closed link" }
            val cmd = pending ?: throw ObdTimeoutException("nothing sent")
            pending = null
            delay(if (cmd == "ATZ") 600 else latencyMs)
            served++
            commands++
            if (willDrop && served > 40) {
                willDrop = false
                dropArmed = false
                throw ObdLinkException(ObdFailure.LinkLost, "fake: link dropped")
            }
            return reply(cmd)
        }

        override fun close() { closed = true }

        private fun reply(cmd: String): String {
            when (cmd) {
                "ATZ" -> { spaces = true; header = "7DF"; return "\r\rELM327 v1.5\r\r" }
                "ATE0", "ATL0", "ATSP6" -> return "OK\r\r"
                "ATS0" -> { spaces = false; return "OK\r\r" }
                "ATSH7E1" -> { header = "7E1"; return "OK\r\r" }
                "ATSH7DF" -> { header = "7DF"; return "OK\r\r" }
            }
            val t = commands / 4.0
            fun hex(v: Int) = "%02X".format(v.coerceIn(0, 255))
            fun bytes(prefix: String, a: Int): String =
                (prefix + hex(a)).let { if (spaces) it.chunked(2).joinToString(" ") else it } + "\r\r"
            // the odd bit of line noise real adapters produce
            if (commands % 37 == 0) return "?\r\r"
            if (commands == 5) return "SEARCHING...\r" + bytes("4104", 120)
            return when (cmd) {
                "0104" -> bytes("4104", (120 + 100 * sin(t * 0.21)).toInt())
                "0111" -> if (commands % 29 == 0) "NO DATA\r\r" else bytes("4111", (95 + 85 * sin(t * 0.16 + 1.3)).toInt())
                "010F" -> bytes("410F", 40 + 20 + (14 * sin(t * 0.05)).toInt()) // 20 .. 34 degC
                "0105" -> bytes("4105", 40 + 88 + (6 * sin(t * 0.04)).toInt())  // about 88 degC
                "221940" -> {
                    val answers = when (scenario.value) {
                        DongleScenario.TransSilent -> false
                        DongleScenario.TransTcmOnly -> header == "7E1"
                        else -> true
                    }
                    if (answers) bytes("621940", 40 + 85 + (22 * sin(t * 0.09)).toInt()) else "NO DATA\r\r" // 85 +- 22 degC
                }
                else -> "?\r\r"
            }
        }
    }
}

/** Sandbox stand-in for the Android Bluetooth layer. Always active, permission always granted. */
class SandboxClusterPlatform(val dongle: FakeDongle) : ClusterPlatform {
    override val appActive: StateFlow<Boolean> = MutableStateFlow(true)
    override fun hasPermission() = true
    override fun requestPermission(onResult: (Boolean) -> Unit) = onResult(true)
    override fun newLink(preferredAddress: String?): ObdLink = dongle.newLink()
    override fun pairedDevices(): List<PairedDevice> = listOf(
        PairedDevice("V-LINK", "00:1D:A5:68:98:8B"),
        PairedDevice("Head unit audio", "A4:C1:38:00:11:22"),
    )
}
