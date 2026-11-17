package com.sierracode.autocontrol

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.sierracode.autocontrol.core.ui.LocalAppConfig
import com.sierracode.autocontrol.core.ui.theme.AutoControlTheme
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.navigation.AppNavHost
import com.sierracode.autocontrol.navigation.NavIntent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsStore: SettingsStore

    private var navIntent by mutableStateOf<NavIntent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Mantener splash hasta que onboarded se resuelva (evita flash blanco).
        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }
        lifecycleScope.launch {
            settingsStore.onboarded.first()
            keepSplash = false
        }

        enableEdgeToEdge()

        // Solo procesar intent en arranque en frío (no en rotación/recreación).
        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        setContent {
            val vm: MainActivityViewModel = hiltViewModel()
            val dark by vm.darkMode.collectAsStateWithLifecycle()
            val appConfig by vm.appConfig.collectAsStateWithLifecycle()

            AutoControlTheme(
                darkTheme = dark ?: isSystemInDarkTheme(),
                dynamicColor = appConfig.dynamicColor
            ) {
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

        val data = intent.data
        if (data?.scheme == "autocontrol" && data.host == "action") {
            val action = data.pathSegments.firstOrNull() ?: return
            lifecycleScope.launch {
                val vehicleId = settingsStore.activeVehicleId.first()
                if (vehicleId != null) {
                    navIntent = NavIntent(vehicleId = vehicleId, action = action)
                }
            }
            return
        }

        val vehicleId = intent.getStringExtra("vehicleId")
        val sourceType = intent.getStringExtra("sourceType")
        val sourceId = intent.getStringExtra("sourceId")

        if (vehicleId != null && sourceType != null && sourceId != null) {
            navIntent = NavIntent(
                vehicleId = vehicleId,
                sourceType = sourceType,
                sourceId = sourceId
            )
        }
    }
}