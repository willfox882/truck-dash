package com.truckdash

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.truckdash.data.AppGraph
import com.truckdash.obd.AndroidClusterPlatform
import com.truckdash.service.DriveService
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.truckdash.ui.DashFonts
import com.truckdash.ui.LocalBackHook
import com.truckdash.ui.TruckDashRoot
import com.truckdash.ui.TruckDashTheme

class MainActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        DriveService.start(this)
    }

    // Cluster tab: BLUETOOTH_CONNECT is asked only when the tab is opened (Android 12+), never at startup
    private val bluetoothPermission: androidx.activity.result.ActivityResultLauncher<String> = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        AppGraph.clusterPlatform.onPermissionResult(granted)
    }
    private val clusterHost = object : AndroidClusterPlatform.Host {
        override fun launchPermissionRequest() = bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        override fun shouldShowPermissionRationale() =
            ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT)
    }

    // The dongle follows VISIBILITY (onStart / onStop), not focus: a reverse camera, volume or climate popup pauses the
    // activity without stopping it and must not drop the socket.
    override fun onStart() {
        super.onStart()
        AppGraph.clusterPlatform.setActive(true)
    }

    override fun onStop() {
        AppGraph.clusterPlatform.setActive(false)
        super.onStop()
    }

    override fun onDestroy() {
        AppGraph.clusterPlatform.detach(clusterHost)
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DashFonts.family = FontFamily(
            Font(R.font.geist_light, FontWeight.Light), Font(R.font.geist_regular, FontWeight.Normal),
            Font(R.font.geist_medium, FontWeight.Medium), Font(R.font.geist_semibold, FontWeight.SemiBold),
        )
        AppGraph.clusterPlatform.attach(clusterHost)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        setContent {
            TruckDashTheme {
                CompositionLocalProvider(LocalBackHook provides { enabled, onBack -> BackHandler(enabled, onBack) }) {
                    TruckDashRoot(AppGraph.repo, clusterPresenter = AppGraph.cluster)
                }
            }
        }

        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.launch(needed.toTypedArray())
    }
}
