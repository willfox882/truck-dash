package com.truckdash.obd

import com.truckdash.model.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the [ObdSession] and decides when it runs: only while the Cluster tab is visible AND the app is active.
 * Creating one makes no platform call at all (not even reading `appActive`); everything starts from [setTabVisible].
 * On Android one instance lives for the whole process (`AppGraph`), so the trans stage survives activity recreation.
 * Call from the main thread.
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
    private var job: Job? = null
    private var running = false
    private var permissionAsked = false
    private var activeWatcher: Job? = null

    /**
     * Only one session may ever touch the adapter. A run that was cancelled before its body started never reaches the
     * lock, one that is running releases it only after its link is closed and the state reset, so the next run waits.
     */
    private val runLock = Mutex()

    fun setTabVisible(v: Boolean) {
        if (v == visible) return
        visible = v
        if (v) watchActive()
        if (!v) permissionAsked = false
        update()
    }

    /** Follows the activity (visible / not); started on the first open so a hidden tab never touches the platform. */
    private fun watchActive() {
        if (activeWatcher != null) return
        active = platform.appActive.value
        activeWatcher = scope.launch {
            platform.appActive.collect { active = it; update() }
        }
    }

    private fun update() {
        val want = visible && active
        if (want && !running) {
            if (!platform.hasPermission() && !permissionAsked) {
                permissionAsked = true
                askPermission(userInitiated = false)
            }
            running = true
            job = scope.launch(ioDispatcher) { runLock.withLock { session.run() } }
        } else if (!want && running) {
            stopJob()
        }
    }

    private fun stopJob() {
        job?.cancel()
        running = false
    }

    /**
     * Dialog when Android still shows one; otherwise (denied before, no rationale) the failure text says so and the Retry
     * button opens the app's settings ([userInitiated]; never automatically when the tab merely opens).
     */
    private fun askPermission(userInitiated: Boolean) {
        if (platform.canRequestPermission()) {
            session.setPermissionViaSettings(false)
            platform.requestPermission { granted ->
                if (granted) {
                    session.setPermissionViaSettings(false)
                    session.retryNow()
                } else {
                    session.setPermissionViaSettings(!platform.canRequestPermission())
                }
            }
        } else {
            session.setPermissionViaSettings(true)
            if (userInitiated) platform.openPermissionSettings()
        }
    }

    /** The Retry button: asks for a missing permission again (or opens the settings), then skips the reconnect wait. */
    fun retry() {
        if (visible) {
            if (!platform.hasPermission()) askPermission(userInitiated = true) else session.setPermissionViaSettings(false)
        }
        session.retryNow()
    }

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
