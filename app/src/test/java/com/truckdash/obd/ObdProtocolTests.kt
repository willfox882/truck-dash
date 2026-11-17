package com.truckdash.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdProtocolTests {
    private fun a(raw: String?, prefix: String = "4104") = ObdProtocol.parseByteA(raw, prefix)

    @Test fun parsesPlainReply() = assertEquals(0xFF, a("4104FF"))
    @Test fun parsesSpacedReplyWithCarriageReturns() = assertEquals(0x7F, a("41 04 7F \r\r"))
    @Test fun stripsSearchingAndBusInit() {
        assertEquals(0x20, a("SEARCHING...\r41 04 20\r\r"))
        assertEquals(0x20, a("BUS INIT: OK\r4104 20"))
        assertEquals(0x20, a("SEARCHING...\rSTOPPED\r4104 20"))
    }
    @Test fun findsPrefixAfterEchoOrJunk() {
        assertEquals(0x21, a("0104\r4104 21"))
        assertEquals(0x21, a("xx>>zz 41 04 21 zz"))
    }
    @Test fun rejectsNoDataAndErrors() {
        assertNull(a("NO DATA"))
        assertNull(a("NO DATA\r\r"))
        assertNull(a("4104FF\rNO DATA"))   // anything saying NO DATA wins, like the original
        assertNull(a("CAN ERROR"))
        assertNull(a("BUFFER FULL ERROR"))
        assertNull(a("UNABLE TO CONNECT"))
        assertNull(a("SEARCHING...\rUNABLE TO CONNECT"))
    }
    @Test fun rejectsGarbageAndShortReplies() {
        assertNull(a(null))
        assertNull(a(""))
        assertNull(a("?"))
        assertNull(a("4104"))        // prefix without a data byte
        assertNull(a("41040"))       // half a byte
        assertNull(a("4111FF"))      // a different PID's reply
        assertNull(a("@@@###"))
    }
    // ---- per-line matching at offset 0 ---------------------------------------------------------------------------------

    @Test fun aMatchNeverSpansTwoLines() {
        assertNull(a("3E41\r0412"))                       // "41" + "04" joined would have matched 4104 and read 12
        assertEquals(0xFF, a("3E41\r4104FF"))
    }
    @Test fun isoTpContinuationLinesAreIgnored() {
        assertNull(a("00B\r0: 41 04 FF 00 00\r1: 00 00"))      // multi-frame: not a single-frame reply, never corrupt
        assertEquals(0x46, a("00B\r0: 7F 22 31\r410F46", "410F"))
    }
    @Test fun aTcmLineAfterANegativeResponseIsFound() {
        val raw = "7F2231\r6219407D\r\r"                  // the ECM refuses, the TCM answers
        assertEquals(0x7D, a(raw, "621940"))
        assertEquals(0x7D, a("7F 22 31\r\r62 19 40 7D", "621940"))
        assertNull(a("7F2231", "621940"))                   // the refusal alone is not an answer
        assertEquals(0x7D, a("SEARCHING...\r7F2231\r\n6219407D", "621940"))
    }
    @Test fun firstMatchingLineWins() = assertEquals(0x10, a("410F10\r410F20", "410F"))
    @Test fun echoLineBeforeTheReplyIsSkipped() = assertEquals(0x46, a("010F\r410F46", "410F"))
    @Test fun initAndHeaderRepliesAreChecked() {
        assertTrue(ObdProtocol.initReplyOk("ATZ", "\r\rELM327 v1.5\r\r"))
        assertTrue(ObdProtocol.initReplyOk("ATZ", "ATZ\r\rELM327 v2.1"))
        assertFalse(ObdProtocol.initReplyOk("ATZ", "OK"))
        assertFalse(ObdProtocol.initReplyOk("ATZ", "?"))
        for (c in listOf("ATE0", "ATL0", "ATS0", "ATSP6")) {
            assertTrue(ObdProtocol.initReplyOk(c, "OK\r\r"))
            assertTrue(ObdProtocol.initReplyOk(c, "ATE0\rOK"))
            assertFalse(ObdProtocol.initReplyOk(c, "?"))
            assertFalse(ObdProtocol.initReplyOk(c, ""))
        }
        assertTrue(ObdProtocol.headerReplyOk("OK\r\r"))
        assertFalse(ObdProtocol.headerReplyOk("?"))
    }

    @Test fun prefixIsMatchedCaseInsensitivelyAndRespectsOtherPids() {
        assertEquals(0x46, a("410f46", "410F"))
        assertEquals(0x7D, a("62 19 40 7D", "621940"))
    }

    @Test fun conversionsAreMetric() {
        assertEquals(30f, ObdProtocol.celsius(70), 0f)
        assertEquals(-40f, ObdProtocol.celsius(0), 0f)
        assertEquals(215f, ObdProtocol.celsius(255), 0f)
        assertEquals(100f, ObdProtocol.percent(255), 0.001f)
        assertEquals(0f, ObdProtocol.percent(0), 0f)
        assertEquals(50f, ObdProtocol.percent(127), 0.2f)
    }

    @Test fun commandTableMatchesTheTruckPids() {
        assertEquals(listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATSP6"), ObdProtocol.INIT.map { it.command })
        assertEquals(7000L, ObdProtocol.INIT.first().timeoutMs)
        assertEquals(listOf("0104", "0111", "010F"), ObdProtocol.STANDARD.map { it.command })
        assertEquals(listOf("4104", "4111", "410F"), ObdProtocol.STANDARD.map { it.prefix })
        assertEquals("221940" to "621940", ObdProtocol.TRANS.command to ObdProtocol.TRANS.prefix)
        assertEquals("0105" to "4105", ObdProtocol.COOLANT.command to ObdProtocol.COOLANT.prefix)
    }
}

class DeviceSelectorTests {
    private val speaker = PairedDevice("Sony speaker", "AA:00")
    private val elm = PairedDevice("OBDII", "BB:11")
    private val other = PairedDevice("Linkin Radio", "DD:33")

    @Test fun picksByNameHintCaseInsensitively() {
        assertEquals(elm, DeviceSelector.pick(listOf(speaker, elm), null))
        for (n in listOf("obd2", "ELM327", "Vgate iCar", "OBDLink MX", "VLINK", "my link")) {
            assertEquals(n, n, DeviceSelector.pick(listOf(speaker, PairedDevice(n, "X")), null)?.name)
        }
    }
    @Test fun specificHintBeatsGenericLink() {
        assertEquals(elm, DeviceSelector.pick(listOf(other, elm), null))
    }
    @Test fun noMatchGivesNull() {
        assertNull(DeviceSelector.pick(listOf(speaker), null))
        assertNull(DeviceSelector.pick(emptyList(), null))
    }
    @Test fun savedAddressWinsEvenWithoutAHintAndIgnoresCase() {
        assertEquals(speaker, DeviceSelector.pick(listOf(elm, speaker), "aa:00"))
    }
    @Test fun savedAddressThatIsGoneFallsBackToAuto() {
        assertEquals(elm, DeviceSelector.pick(listOf(speaker, elm), "FF:FF"))
    }
}
