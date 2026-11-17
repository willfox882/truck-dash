package com.truckdash.obd

import com.truckdash.model.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the [ObdSession] and decides when it runs: only while the Cluster tab is visible AND the app is active.
 * Creating one does nothing (no Bluetooth call); everything starts from [setTabVisible]. Call from the main thread.
 */
class ClusterPresenter(
    private val platform: ClusterPlatform,
    private val settings: StateFlow<Settings>,
    private val saveObdAddress: (String?) -> Unit,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    reconnectDelayMs: Long = ObdSession.RECONNECT_DELAY_MS,
) {
    private val session = ObdSession({ platform.newLink(settings.value.obdAddress) }, reconnectDelayMs)
    val state: StateFlow<ClusterState> = session.state

    private var visible = false
    private var active = false
    private var job: Job? = null // the newest run; an older cancelled one is joined before the next starts
    private var running = false
    private var permissionAsked = false

    init {
        // follows the activity (resume / pause); a StateFlow read, not a Bluetooth call
        scope.launch {
            platform.appActive.collect { active = it; update() }
        }
    }

    fun setTabVisible(v: Boolean) {
        if (v == visible) return
        visible = v
        if (!v) permissionAsked = false
        update()
    }

    private fun update() {
        val want = visible && active
        if (want && !running) {
            if (!platform.hasPermission() && !permissionAsked) {
                permissionAsked = true
                platform.requestPermission { granted -> if (granted) session.retryNow() }
            }
            val previous = job
            running = true
            job = scope.launch(ioDispatcher) {
                previous?.join() // the old run must have closed its socket and reset the state first
                session.run()
            }
        } else if (!want && running) {
            stopJob()
        }
    }

    private fun stopJob() {
        job?.cancel()
        running = false
    }

    /** The Retry button. */
    fun retry() = session.retryNow()

    fun pairedDevices(): List<PairedDevice> = platform.pairedDevices()

    /** The saved dongle address, or null for automatic selection. */
    val selectedAddress: String? get() = settings.value.obdAddress

    /** Saves the choice and reconnects to it right away. */
    fun selectDevice(address: String?) {
        saveObdAddress(address)
        if (running) {
            stopJob()
            update()
        }
    }
}
