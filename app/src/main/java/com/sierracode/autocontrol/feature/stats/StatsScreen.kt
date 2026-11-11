package com.sierracode.autocontrol.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.core.format.formatMoneyCents
import com.sierracode.autocontrol.data.local.dao.CategoryDao
import com.sierracode.autocontrol.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max

data class CategoryStat(val name: String, val totalCents: Long, val percentage: Float)
data class MonthlyStat(val monthLabel: String, val totalCents: Long)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val categoryDao: CategoryDao
) : ViewModel() {
    private val vehicleIdFlow = MutableStateFlow<String?>(null)

    val monthlyStats = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else expenseRepo.observeByVehicle(id).map { expenses ->
            val formatter = DateTimeFormatter.ofPattern("MMM", Locale("es", "MX"))
            expenses.groupBy {
                val date = LocalDate.ofEpochDay(it.dateEpochDay)
                date.format(formatter).replaceFirstChar { char -> char.titlecase(Locale.getDefault()) }
            }.map { (month, list) ->
                MonthlyStat(month, list.sumOf { it.amountCents })
            }.reversed()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categoryStats = combine(
        vehicleIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else expenseRepo.observeByCategory(id, 0L, Long.MAX_VALUE)
        },
        categoryDao.observeAll()
    ) { totals, categories ->
        val grandTotal = totals.sumOf { it.totalCents }
        if (grandTotal == 0L) return@combine emptyList()

        totals.mapNotNull { row ->
            val catName = categories.find { it.id == row.categoryId }?.name ?: "Otros"
            CategoryStat(
                name = catName,
                totalCents = row.totalCents,
                percentage = (row.totalCents.toFloat() / grandTotal.toFloat()) * 100f
            )
        }.sortedByDescending { it.totalCents }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadVehicle(id: String) {
        vehicleIdFlow.value = id
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    vehicleId: String,
    onBack: () -> Unit,
    vm: StatsViewModel = hiltViewModel()
) {
    LaunchedEffect(vehicleId) {
        vm.loadVehicle(vehicleId)
    }

    val monthlyStats by vm.monthlyStats.collectAsState()
    val categoryStats by vm.categoryStats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estadísticas", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = "Mis Vehículos")
                    }
                }
            )
        }
    ) { padding ->
        if (monthlyStats.isEmpty() && categoryStats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Registra gastos para ver estadísticas.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Text(
                        "Evolución Mensual",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))

                    if (monthlyStats.size >= 2) {
                        CustomLineChart(monthlyStats, MaterialTheme.colorScheme.primary)
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Aún no hay suficiente historial",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Registra gastos en al menos 2 meses diferentes para ver la gráfica de evolución.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                if (categoryStats.isNotEmpty()) {
                    item {
                        Text(
                            "Distribución por Categoría",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(categoryStats) { stat ->
                        StatBar(stat = stat)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomLineChart(data: List<MonthlyStat>, lineColor: Color) {
    val maxVal = data.maxOfOrNull { it.totalCents }?.toFloat() ?: 1f
    val minVal = data.minOfOrNull { it.totalCents }?.toFloat() ?: 0f
    val range = max(1f, maxVal - minVal)

    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(220.dp)
        .padding(8.dp)
    ) {
        if (data.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height
        val stepX = if (data.size > 1) width / (data.size - 1) else width

        val points = data.mapIndexed { index, stat ->
            val x = index * stepX
            val normalizedY = (stat.totalCents - minVal) / range
            val y = height - (normalizedY * height * 0.8f) - (height * 0.1f)
            Offset(x, y)
        }

        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val midX = (p1.x + p2.x) / 2
                cubicTo(midX, p1.y, midX, p2.y, p2.x, p2.y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 6f, cap = StrokeCap.Round)
        )

        points.forEach { point ->
            drawCircle(color = lineColor, radius = 8f, center = point)
            drawCircle(color = Color.White, radius = 4f, center = point)
        }
    }
}

@Composable
fun StatBar(stat: CategoryStat) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = stat.name, fontWeight = FontWeight.Medium)
            Text(text = formatMoneyCents(stat.totalCents), fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = { stat.percentage / 100f },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = String.format(Locale.getDefault(), "%.1f%% del total", stat.percentage),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}