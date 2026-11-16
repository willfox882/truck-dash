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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.truckdash.data.AppGraph
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DashFonts.family = FontFamily(
            Font(R.font.geist_light, FontWeight.Light), Font(R.font.geist_regular, FontWeight.Normal),
            Font(R.font.geist_medium, FontWeight.Medium), Font(R.font.geist_semibold, FontWeight.SemiBold),
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        setContent {
            TruckDashTheme {
                CompositionLocalProvider(LocalBackHook provides { enabled, onBack -> BackHandler(enabled, onBack) }) {
                    TruckDashRoot(AppGraph.repo)
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
