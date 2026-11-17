package com.truckdash.obd

/** Which dial a value belongs to. [Trans] shows coolant temperature after the staged fallback. */
enum class GaugeId { Trans, Load, Throttle, Intake }

/** One PID: request, expected reply prefix (hex, no spaces), conversion of data byte A to the displayed unit. */
class Pid(val id: GaugeId, val command: String, val prefix: String, val convert: (Int) -> Float)

/** An initialisation command with its timeout. */
class InitCommand(val command: String, val timeoutMs: Long)

/**
 * ELM327 command table, reply parsing and unit conversion. Pure, deterministic: never trusts the dongle's
 * formatting (spaces, echo, SEARCHING..., stray lines). Temperatures are degrees C, loads and positions are percent.
 */
object ObdProtocol {
    /** Reset (slow, the chip reboots), echo off, linefeeds off, spaces off, protocol 6 = ISO 15765-4 CAN 11 bit / 500 kbaud. */
    val INIT = listOf(
        InitCommand("ATZ", 7000), InitCommand("ATE0", 2000), InitCommand("ATL0", 2000),
        InitCommand("ATS0", 2000), InitCommand("ATSP6", 2000),
    )
    const val COMMAND_TIMEOUT_MS = 2000L

    fun celsius(a: Int): Float = (a - 40).toFloat()
    fun percent(a: Int): Float = a * 100f / 255f

    /** SAE J1979 mode 01 PIDs, mandatory on every 2008+ vehicle. */
    val STANDARD = listOf(
        Pid(GaugeId.Load, "0104", "4104", ::percent),
        Pid(GaugeId.Throttle, "0111", "4111", ::percent),
        Pid(GaugeId.Intake, "010F", "410F", ::celsius),
    )

    /** GM extended mode 22 transmission fluid temperature. Acquisition is staged, see [ObdSession]. */
    val TRANS = Pid(GaugeId.Trans, "221940", "621940", ::celsius)

    /** Guaranteed SAE engine coolant temperature, the permanent fallback for the Trans dial. */
    val COOLANT = Pid(GaugeId.Trans, "0105", "4105", ::celsius)

    const val ADDRESS_TCM = "ATSH7E1"
    const val ADDRESS_BROADCAST = "ATSH7DF"

    private val NOISE = listOf("SEARCHING...", "SEARCHING", "BUS INIT", "STOPPED")

    /**
     * Extracts data byte A from a reply for [prefix]. The reply is split into lines (CR / LF); each line has SEARCHING /
     * BUS INIT noise, spaces and non-hex characters removed, and the first line that STARTS with [prefix] wins, so a
     * negative response (7F...) or an ISO-TP continuation line ("0:", "1:") on another line can never produce a match
     * that spans two lines. Anything saying NO DATA / ERROR / UNABLE TO CONNECT rejects the whole reply. Null when off.
     */
    fun parseByteA(raw: String?, prefix: String): Int? {
        if (raw == null) return null
        val want = prefix.uppercase()
        var found: Int? = null
        for (line in raw.split('\r', '\n')) {
            var up = line.uppercase()
            for (junk in NOISE) up = up.replace(junk, "")
            val packed = up.filter { !it.isWhitespace() }
            if (packed.isEmpty()) continue
            if ("NODATA" in packed || "ERROR" in packed || "UNABLETOCONNECT" in packed) return null
            if (found != null) continue
            if (packed.length > 1 && packed[1] == ':') continue // ISO-TP frame index ("0:", "1:"): not a single-frame reply
            val hex = packed.filter { it in '0'..'9' || it in 'A'..'F' }
            if (hex.startsWith(want) && hex.length >= want.length + 2) {
                found = hex.substring(want.length, want.length + 2).toInt(16)
            }
        }
        return found
    }

    /** Did the adapter accept this init command? ATZ answers with its banner (ELM...), everything else with OK. */
    fun initReplyOk(command: String, reply: String): Boolean {
        val up = reply.uppercase()
        return if (command == "ATZ") "ELM" in up else "OK" in up
    }

    /** Did the adapter accept ATSH (header change)? */
    fun headerReplyOk(reply: String): Boolean = "OK" in reply.uppercase()
}
