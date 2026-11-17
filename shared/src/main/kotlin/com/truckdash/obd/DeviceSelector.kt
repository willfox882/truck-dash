package com.truckdash.obd

/** Chooses which bonded Bluetooth device is the OBD dongle. Pure, so it is unit tested. */
object DeviceSelector {
    /** Name hints, most specific first (a generic "LINK" must not beat "OBD" or "ELM"). */
    val HINTS = listOf("OBDLINK", "OBDII", "OBD", "ELM", "VLINK", "V-LINK", "VGATE", "LINK")

    fun looksLikeObd(name: String): Boolean {
        val up = name.uppercase()
        return HINTS.any { it in up }
    }

    /** The user's saved device if it is still bonded, otherwise the best name match, otherwise null. */
    fun pick(devices: List<PairedDevice>, preferredAddress: String?): PairedDevice? {
        if (preferredAddress != null) {
            devices.firstOrNull { it.address.equals(preferredAddress, ignoreCase = true) }?.let { return it }
        }
        for (hint in HINTS) {
            devices.firstOrNull { hint in it.name.uppercase() }?.let { return it }
        }
        return null
    }
}
