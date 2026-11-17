package com.truckdash.obd

import kotlinx.coroutines.delay

/**
 * A scripted ELM327 for session tests. [handler] gets each command (without CR) and returns the reply text,
 * returns null for "no prompt" (a timeout) or throws to simulate a dropped link.
 */
class ScriptLink(
    private val log: MutableList<String>,
    private val openFailure: ObdFailure? = null,
    private val openDelayMs: Long = 0,
    private val handler: (String) -> String?,
) : ObdLink {
    override val deviceName: String? = "TEST-OBD"
    var opened = false
    var closed = false
    private var pending = ""

    override suspend fun open() {
        log += "open"
        if (openDelayMs > 0) delay(openDelayMs)
        openFailure?.let { throw ObdLinkException(it) }
        opened = true
    }

    override suspend fun write(text: String) { pending = text.trim(); log += pending }

    override suspend fun readUntilPrompt(timeoutMs: Long): String = handler(pending) ?: throw ObdTimeoutException()

    override fun close() { closed = true }
}

/** Replies like a healthy truck. Override pieces through [overrides] (command -> reply). */
fun healthyReply(cmd: String, overrides: Map<String, String> = emptyMap()): String = overrides[cmd] ?: when (cmd) {
    "ATZ" -> "\r\rELM327 v1.5\r\r"
    "ATE0", "ATL0", "ATS0", "ATSP6", "ATSH7E1", "ATSH7DF" -> "OK\r\r"
    "0104" -> "4104FF\r\r"       // 100 %
    "0111" -> "41117F\r\r"       // 49.8 %
    "010F" -> "410F46\r\r"       // 70 - 40 = 30 degC
    "0105" -> "41055A\r\r"       // 90 - 40 = 50 degC
    "221940" -> "6219407D\r\r"   // 125 - 40 = 85 degC
    else -> "?\r\r"
}
