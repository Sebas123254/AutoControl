package com.sierracode.autocontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.sierracode.autocontrol.core.ui.LocalAppConfig
import com.sierracode.autocontrol.core.ui.theme.AutoControlTheme
import com.sierracode.autocontrol.navigation.AppNavHost
import com.sierracode.autocontrol.navigation.NavIntent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var navIntent by mutableStateOf<NavIntent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        handleIntent(intent)

        setContent {
            val vm: MainActivityViewModel = hiltViewModel()
            val dark by vm.darkMode.collectAsState(initial = null)
            val appConfig by vm.appConfig.collectAsState()

            AutoControlTheme(darkTheme = dark ?: isSystemInDarkTheme()) {
                CompositionLocalProvider(LocalAppConfig provides appConfig) {
                    val nav = rememberNavController()
                    AppNavHost(
                        nav = nav,
                        navIntent = navIntent,
                        onIntentConsumed = { navIntent = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val vehicleId = intent.getStringExtra("vehicleId")
        val sourceType = intent.getStringExtra("sourceType")
        val sourceId = intent.getStringExtra("sourceId")

        if (vehicleId != null && sourceType != null && sourceId != null) {
            navIntent = NavIntent(vehicleId, sourceType, sourceId)
        }
    }
}