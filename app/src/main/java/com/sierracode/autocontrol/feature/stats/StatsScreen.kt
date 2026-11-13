package com.sierracode.autocontrol.feature.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatMoneyCents
import com.sierracode.autocontrol.core.ui.components.EmptyStateCard
import com.sierracode.autocontrol.data.repository.CategoryRepository
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
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

data class CategoryStat(val name: String, val totalCents: Long, val percentage: Float)
data class MonthlyStat(val monthLabel: String, val totalCents: Long)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val categoryRepo: CategoryRepository
) : ViewModel() {
    private val vehicleIdFlow = MutableStateFlow<String?>(null)

    val monthlyStats = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else expenseRepo.observeByVehicle(id).map { expenses ->
            val formatter = DateTimeFormatter.ofPattern("MMM yy", Locale("es", "MX"))
            expenses.groupBy {
                YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay))
            }.toSortedMap().map { (yearMonth, list) ->
                MonthlyStat(
                    monthLabel = yearMonth.format(formatter).replaceFirstChar { it.titlecase(Locale.getDefault()) },
                    totalCents = list.sumOf { it.amountCents }
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categoryStats = combine(
        vehicleIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else expenseRepo.observeByCategory(id, 0L, Long.MAX_VALUE)
        },
        categoryRepo.observeAll()
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
    vehicleName: String,
    onOpenExpenses: () -> Unit,
    vm: StatsViewModel = hiltViewModel()
) {
    LaunchedEffect(vehicleId) { vm.loadVehicle(vehicleId) }

    val monthlyStats by vm.monthlyStats.collectAsState()
    val categoryStats by vm.categoryStats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.stats_title), fontWeight = FontWeight.Bold)
                        if (vehicleName.isNotBlank()) {
                            Text(
                                vehicleName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (monthlyStats.isEmpty() && categoryStats.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.Center) {
                EmptyStateCard(
                    icon = Icons.Default.Receipt,
                    title = stringResource(R.string.stats_empty),
                    description = "Registra tu primer gasto para empezar a ver estadísticas de tu vehículo.",
                    actionLabel = "Ir a Gastos",
                    onActionClick = onOpenExpenses
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Text(
                        stringResource(R.string.stats_monthly_evolution),
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
                                    text = stringResource(R.string.stats_not_enough_history),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.stats_history_hint),
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
                            stringResource(R.string.stats_category_distribution),
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
    if (data.isEmpty()) return

    val maxVal = data.maxOfOrNull { it.totalCents }?.toFloat() ?: 1f
    val minVal = data.minOfOrNull { it.totalCents }?.toFloat() ?: 0f
    val range = max(1f, maxVal - minVal)

    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dotInnerColor = MaterialTheme.colorScheme.surface
    val tooltipBgColor = MaterialTheme.colorScheme.inverseSurface
    val tooltipTextColor = MaterialTheme.colorScheme.inverseOnSurface

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    // SOLUCIÓN: Evaluar en contexto composable (línea a línea) y guardar la lista resultante.
    val formattedAmounts = data.map { formatMoneyCents(it.totalCents) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(top = 16.dp, end = 32.dp, start = 48.dp, bottom = 32.dp)
            .pointerInput(data) {
                detectTapGestures { offset ->
                    val stepX = if (data.size > 1) size.width / (data.size - 1) else size.width
                    val index = (offset.x / stepX).roundToInt().coerceIn(0, data.size - 1)
                    selectedIndex = if (selectedIndex == index) null else index
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val stepX = if (data.size > 1) width / (data.size - 1) else width

        // Dibujar líneas de fondo (Eje Y)
        val ySteps = 4
        for (i in 0..ySteps) {
            val y = height - (height * i / ySteps)
            drawLine(
                color = textColor.copy(alpha = 0.2f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 2f
            )
            val valAtY = minVal + (range * i / ySteps)
            val label = if (valAtY >= 1000) "$${(valAtY/1000).roundToInt()}k" else "$${valAtY.roundToInt()}"
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                topLeft = Offset(-40.dp.toPx(), y - 8.dp.toPx()),
                style = TextStyle(color = textColor, fontSize = 10.sp)
            )
        }

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

        drawPath(path = path, color = lineColor, style = Stroke(width = 6f, cap = StrokeCap.Round))

        points.forEachIndexed { index, point ->
            drawCircle(color = lineColor, radius = 8f, center = point)
            drawCircle(color = dotInnerColor, radius = 4f, center = point)

            // Etiquetas X
            drawText(
                textMeasurer = textMeasurer,
                text = data[index].monthLabel,
                topLeft = Offset(point.x - 20.dp.toPx(), size.height + 12.dp.toPx()),
                style = TextStyle(color = textColor, fontSize = 10.sp)
            )

            // Tooltip
            if (selectedIndex == index) {
                val tooltipText = formattedAmounts[index]
                val textLayoutResult = textMeasurer.measure(
                    text = tooltipText,
                    style = TextStyle(color = tooltipTextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                )
                val tooltipWidth = textLayoutResult.size.width + 16.dp.toPx()
                val tooltipHeight = textLayoutResult.size.height + 12.dp.toPx()

                val tooltipX = (point.x - tooltipWidth / 2).coerceIn(0f, width - tooltipWidth)
                val tooltipY = (point.y - tooltipHeight - 16.dp.toPx()).coerceAtLeast(0f)

                drawRoundRect(
                    color = tooltipBgColor,
                    topLeft = Offset(tooltipX, tooltipY),
                    size = Size(tooltipWidth, tooltipHeight),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )

                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(tooltipX + 8.dp.toPx(), tooltipY + 6.dp.toPx())
                )
            }
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
            Text(
                text = if (stat.name == "Otros") stringResource(R.string.stats_others) else stat.name,
                fontWeight = FontWeight.Medium
            )
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
            text = stringResource(R.string.stats_percent_total, stat.percentage),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}