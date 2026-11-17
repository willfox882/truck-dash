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
     * Extracts data byte A from a reply for [prefix]: drop SEARCHING/BUS INIT noise, reject NO DATA / ERROR /
     * UNABLE TO CONNECT, keep hex digits only, find the prefix, take the next two hex digits. Null when anything is off.
     */
    fun parseByteA(raw: String?, prefix: String): Int? {
        if (raw == null) return null
        var up = raw.uppercase()
        for (junk in NOISE) up = up.replace(junk, "")
        val packed = up.replace(" ", "").replace("\r", "").replace("\n", "")
        if ("NODATA" in packed || "ERROR" in packed || "UNABLETOCONNECT" in packed) return null
        val hex = packed.filter { it in '0'..'9' || it in 'A'..'F' }
        val i = hex.indexOf(prefix.uppercase())
        if (i < 0 || hex.length < i + prefix.length + 2) return null
        return hex.substring(i + prefix.length, i + prefix.length + 2).toInt(16)
    }
}
