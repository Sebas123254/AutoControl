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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatMoneyCents
import com.sierracode.autocontrol.core.format.localizedCategoryName
import com.sierracode.autocontrol.core.ui.LocalAppConfig
import com.sierracode.autocontrol.core.ui.components.EmptyStateCard
import com.sierracode.autocontrol.data.repository.CategoryRepository
import com.sierracode.autocontrol.data.repository.ExpenseRepository
import com.sierracode.autocontrol.domain.calc.buildMonthlyTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

data class CategoryStat(
    val categoryId: String,
    val fallbackName: String?,
    val totalCents: Long,
    val percentage: Float
)
data class MonthlyStat(val monthLabel: String, val totalCents: Long)

/** Rango de meses de la gráfica. months = null significa "desde el primer gasto". */
enum class StatsRange(val months: Int?) { LAST_6(6), LAST_12(12), ALL(null) }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val categoryRepo: CategoryRepository
) : ViewModel() {
    private val vehicleIdFlow = MutableStateFlow<String?>(null)
    private val rangeFlow = MutableStateFlow(StatsRange.LAST_12)
    val range = rangeFlow.asStateFlow()

    val monthlyStats = combine(
        vehicleIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else expenseRepo.observeByVehicle(id)
        },
        rangeFlow
    ) { expenses, selected ->
        val formatter = DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())
        buildMonthlyTotals(
            expenses = expenses.map { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) to it.amountCents },
            monthsBack = selected.months
        ).map { total ->
            MonthlyStat(
                monthLabel = total.month.format(formatter).replaceFirstChar { it.titlecase(Locale.getDefault()) },
                totalCents = total.totalCents
            )
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
            val catName = categories.find { it.id == row.categoryId }?.name
            CategoryStat(
                categoryId = row.categoryId,
                fallbackName = catName,
                totalCents = row.totalCents,
                percentage = (row.totalCents.toFloat() / grandTotal.toFloat()) * 100f
            )
        }.sortedByDescending { it.totalCents }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadVehicle(id: String) {
        vehicleIdFlow.value = id
    }

    fun setRange(newRange: StatsRange) {
        rangeFlow.value = newRange
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

    val monthlyStats by vm.monthlyStats.collectAsStateWithLifecycle()
    val categoryStats by vm.categoryStats.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()

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
                    description = stringResource(R.string.stats_empty_desc),
                    actionLabel = stringResource(R.string.stats_go_to_expenses),
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
                    Spacer(Modifier.height(12.dp))
                    StatsRangeChips(selected = range, onSelect = vm::setRange)
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

                    items(categoryStats, key = { it.categoryId }) { stat ->
                        StatBar(stat = stat)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsRangeChips(selected: StatsRange, onSelect: (StatsRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatsRange.entries.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = {
                    Text(
                        stringResource(
                            when (option) {
                                StatsRange.LAST_6 -> R.string.stats_range_6
                                StatsRange.LAST_12 -> R.string.stats_range_12
                                StatsRange.ALL -> R.string.stats_range_all
                            }
                        )
                    )
                }
            )
        }
    }
}

private const val AXIS_STEPS = 4

/** Máximo "redondo" del eje Y (en centavos), de modo que las 4 divisiones queden en cifras limpias. */
private fun niceAxisMax(maxCents: Long): Float {
    if (maxCents <= 0L) return 10_000f
    val rawStep = maxCents.toDouble() / AXIS_STEPS
    val magnitude = 10.0.pow(floor(log10(rawStep)))
    val normalized = rawStep / magnitude
    val niceStep = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 5.0 -> 5.0
        else -> 10.0
    } * magnitude
    return (niceStep * AXIS_STEPS).toFloat()
}

/** Etiquetas del eje Y con el símbolo de la divisa de la app y abreviaturas (k, M). */
private fun axisLabelsFor(axisMaxCents: Float, currencyCode: String, locale: Locale): List<String> {
    val symbol = try {
        Currency.getInstance(currencyCode).getSymbol(locale)
    } catch (_: Exception) {
        "\$"
    }
    val nf = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    return (0..AXIS_STEPS).map { i ->
        val major = axisMaxCents / 100.0 * i / AXIS_STEPS
        val compact = when {
            major >= 1_000_000 -> nf.format(major / 1_000_000) + "M"
            major >= 1_000 -> nf.format(major / 1_000) + "k"
            else -> nf.format(major)
        }
        symbol + compact
    }
}

@Composable
fun CustomLineChart(data: List<MonthlyStat>, lineColor: Color) {
    if (data.isEmpty()) return

    val config = LocalAppConfig.current
    val locale = LocalConfiguration.current.locales[0]

    // Eje Y siempre desde cero. Puntos y etiquetas usan la MISMA escala (axisMax).
    val axisMax = remember(data) { niceAxisMax(data.maxOf { it.totalCents }) }
    val axisLabels = remember(axisMax, config.currencyCode, locale) {
        axisLabelsFor(axisMax, config.currencyCode, locale)
    }

    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val dotInnerColor = MaterialTheme.colorScheme.surface
    val tooltipBgColor = MaterialTheme.colorScheme.inverseSurface
    val tooltipTextColor = MaterialTheme.colorScheme.inverseOnSurface

    var selectedIndex by remember(data) { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    val formattedAmounts = data.map { formatMoneyCents(it.totalCents) }

    val chartDescriptionPrefix = stringResource(R.string.stats_chart_desc)
    val chartDescription = remember(data, chartDescriptionPrefix) {
        buildString {
            append(chartDescriptionPrefix)
            data.forEachIndexed { index, stat ->
                append("${stat.monthLabel}: ${formattedAmounts[index]}. ")
            }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(top = 16.dp, end = 32.dp, start = 56.dp, bottom = 32.dp)
            .semantics { contentDescription = chartDescription }
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

        // Única función valor -> posición vertical, usada por líneas, etiquetas y puntos.
        val yFor = { cents: Float -> height - (cents / axisMax) * height }

        // Líneas guía y etiquetas del eje Y
        for (i in 0..AXIS_STEPS) {
            val y = yFor(axisMax * i / AXIS_STEPS)
            drawLine(
                color = textColor.copy(alpha = 0.2f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 2f
            )
            val label = textMeasurer.measure(
                text = axisLabels[i],
                style = TextStyle(color = textColor, fontSize = 10.sp)
            )
            drawText(
                textLayoutResult = label,
                topLeft = Offset(-8.dp.toPx() - label.size.width, y - label.size.height / 2f)
            )
        }

        val points = data.mapIndexed { index, stat ->
            Offset(index * stepX, yFor(stat.totalCents.toFloat()))
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

        // Etiquetas del eje X: se espacian para que no se encimen; el último mes siempre se muestra.
        val maxLabels = max(1, (width / 52.dp.toPx()).toInt())
        val labelEvery = max(1, ceil(data.size / maxLabels.toFloat()).toInt())

        points.forEachIndexed { index, point ->
            drawCircle(color = lineColor, radius = 8f, center = point)
            drawCircle(color = dotInnerColor, radius = 4f, center = point)

            if ((data.lastIndex - index) % labelEvery == 0) {
                val monthLabel = textMeasurer.measure(
                    text = data[index].monthLabel,
                    style = TextStyle(color = textColor, fontSize = 10.sp)
                )
                drawText(
                    textLayoutResult = monthLabel,
                    topLeft = Offset(point.x - monthLabel.size.width / 2f, height + 12.dp.toPx())
                )
            }

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
    val displayName = if (stat.categoryId.isNotBlank() && stat.fallbackName != null) {
        localizedCategoryName(stat.categoryId, stat.fallbackName)
    } else {
        stringResource(R.string.stats_others)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = displayName,
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