package com.sierracode.autocontrol.feature.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.data.preferences.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel() {
    fun complete(onDone: () -> Unit) = viewModelScope.launch {
        settings.setOnboarded(true)
        onDone()
    }
}

data class OnboardingPage(val title: String, val body: String, val icon: ImageVector)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    val pages = listOf(
        OnboardingPage(
            "Conoce el costo real de tu auto.",
            "Registra tus gastos y descubre exactamente cuánto te cuesta cada kilómetro recorrido.",
            Icons.Default.DirectionsCar
        ),
        OnboardingPage(
            "Controla mantenimiento y reparaciones.",
            "Lleva una bitácora completa. Nunca vuelvas a olvidar cuándo te toca el próximo servicio.",
            Icons.Default.Build
        ),
        OnboardingPage(
            "Nunca olvides un trámite importante.",
            "Recibe recordatorios por fecha y kilometraje para tu seguro, verificación y tenencia.",
            Icons.Default.Notifications
        )
    )

    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {

        // Botón Saltar (solo visible si no está en la última página)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (pager.currentPage < pages.size - 1) {
                TextButton(onClick = { vm.complete(onDone) }) {
                    Text("Saltar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.height(48.dp)) // Mantiene el espacio
            }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
            val page = pages[i]
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(32.dp))
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = page.body,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Indicador de páginas (puntitos)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pages.size) { iteration ->
                val color = if (pager.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(10.dp)
                        .background(color, shape = androidx.compose.foundation.shape.CircleShape)
                )
            }
        }

        // Botón Siguiente / Empezar
        Button(
            onClick = {
                if (pager.currentPage < pages.size - 1) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                } else {
                    vm.complete(onDone)
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(if (pager.currentPage < pages.size - 1) "Siguiente" else "Agregar mi primer auto")
        }
    }
}