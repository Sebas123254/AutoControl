package com.sierracode.autocontrol.feature.goals

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatMoneyCents
import com.sierracode.autocontrol.core.ui.components.EmptyStateCard
import com.sierracode.autocontrol.data.local.entity.GoalEntity
import com.sierracode.autocontrol.data.preferences.SettingsStore
import com.sierracode.autocontrol.data.repository.ExpenseRepository
import com.sierracode.autocontrol.data.repository.GoalRepository
import com.sierracode.autocontrol.domain.calc.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalWithExpense(val goal: GoalEntity, val realCents: Long?)

data class GoalsUiState(
    val pendingGoals: List<GoalEntity> = emptyList(),
    val completedGoals: List<GoalWithExpense> = emptyList(),
    val filteredTotal: GoalTotals? = null,
    val generalTotal: GoalTotals? = null,
    val countAll: Int = 0,
    val countHigh: Int = 0,
    val countMedium: Int = 0,
    val countLow: Int = 0,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepo: GoalRepository,
    private val expenseRepo: ExpenseRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {
    private val vehicleIdFlow = MutableStateFlow<String?>(null)

    private val _activeFilter = MutableStateFlow(GoalFilter.ALL)
    val activeFilter = _activeFilter.asStateFlow()

    val activeSort = settingsStore.goalsSort.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalSort.PRIORITY_DESC)

    val uiState = combine(
        vehicleIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else goalRepo.observeByVehicle(id)
        },
        vehicleIdFlow.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else expenseRepo.observeByVehicle(id)
        },
        _activeFilter,
        activeSort
    ) { goals, expenses, filter, sort ->
        val validGoals = goals.filter { it.id.isNotBlank() }

        val countHigh = validGoals.count { it.priority == "ALTA" }
        val countMedium = validGoals.count { it.priority == "MEDIA" }
        val countLow = validGoals.count { it.priority == "BAJA" }

        val sortedFiltered = filterAndSortGoals(validGoals, filter, sort)

        val pending = sortedFiltered.filter { it.status == "PENDIENTE" }
        val completed = sortedFiltered.filter { it.status == "COMPRADO" }.map { g ->
            val exp = expenses.find { it.id == g.linkedExpenseId }
            GoalWithExpense(g, exp?.amountCents)
        }

        val allPending = validGoals.filter { it.status == "PENDIENTE" }
        val generalTotals = calculatePendingTotals(allPending.map { GoalCalcData(it.estimatedCents, it.estimatedMaxCents) })
        val filteredTotals = calculatePendingTotals(pending.map { GoalCalcData(it.estimatedCents, it.estimatedMaxCents) })

        GoalsUiState(
            pendingGoals = pending,
            completedGoals = completed,
            filteredTotal = filteredTotals,
            generalTotal = generalTotals,
            countAll = validGoals.size,
            countHigh = countHigh,
            countMedium = countMedium,
            countLow = countLow,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    fun loadVehicle(id: String) { vehicleIdFlow.value = id }

    fun setFilter(filter: GoalFilter) { _activeFilter.value = filter }

    fun setSort(sort: GoalSort) {
        viewModelScope.launch { settingsStore.setGoalsSort(sort) }
    }

    fun deleteGoal(id: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            goalRepo.delete(id)
            onSuccess()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    vehicleId: String,
    vehicleName: String,
    onAddClick: () -> Unit,
    onGoalClick: (String) -> Unit,
    vm: GoalsViewModel = hiltViewModel()
) {
    LaunchedEffect(vehicleId) { vm.loadVehicle(vehicleId) }

    val state by vm.uiState.collectAsState()
    val activeFilter by vm.activeFilter.collectAsState()
    val activeSort by vm.activeSort.collectAsState()

    var expandedCompleted by remember { mutableStateOf(false) }
    var goalToDelete by remember { mutableStateOf<GoalEntity?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    if (goalToDelete != null) {
        AlertDialog(
            onDismissRequest = { goalToDelete = null },
            title = { Text(stringResource(R.string.goal_delete_title)) },
            text = { Text(stringResource(R.string.goal_delete_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteGoal(goalToDelete!!.id) { goalToDelete = null }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { goalToDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.goals_title), fontWeight = FontWeight.Bold)
                        if (vehicleName.isNotBlank()) {
                            Text(vehicleName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordenar")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(text = { Text("Prioridad: alta → baja") }, onClick = { vm.setSort(GoalSort.PRIORITY_DESC); showSortMenu = false })
                            DropdownMenuItem(text = { Text("Prioridad: baja → alta") }, onClick = { vm.setSort(GoalSort.PRIORITY_ASC); showSortMenu = false })
                            DropdownMenuItem(text = { Text("Fecha objetivo") }, onClick = { vm.setSort(GoalSort.TARGET_DATE); showSortMenu = false })
                            DropdownMenuItem(text = { Text("Monto estimado") }, onClick = { vm.setSort(GoalSort.AMOUNT_DESC); showSortMenu = false })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.goals_add)) }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { FilterChip(selected = activeFilter == GoalFilter.ALL, onClick = { vm.setFilter(GoalFilter.ALL) }, label = { Text("Todas (${state.countAll})") }) }
                item { FilterChip(selected = activeFilter == GoalFilter.HIGH, onClick = { vm.setFilter(GoalFilter.HIGH) }, label = { Text("Alta (${state.countHigh})") }) }
                item { FilterChip(selected = activeFilter == GoalFilter.MEDIUM, onClick = { vm.setFilter(GoalFilter.MEDIUM) }, label = { Text("Media (${state.countMedium})") }) }
                item { FilterChip(selected = activeFilter == GoalFilter.LOW, onClick = { vm.setFilter(GoalFilter.LOW) }, label = { Text("Baja (${state.countLow})") }) }
            }

            if (state.pendingGoals.isEmpty() && state.completedGoals.isEmpty()) {
                Box(Modifier.weight(1f).padding(16.dp), contentAlignment = Alignment.Center) {
                    EmptyStateCard(
                        icon = Icons.Default.Flag,
                        title = if (activeFilter == GoalFilter.ALL) stringResource(R.string.goals_empty) else "No hay objetivos de prioridad ${activeFilter.name.lowercase()}",
                        description = if (activeFilter == GoalFilter.ALL) "Toca el botón + para agregar algo." else "Prueba cambiando el filtro o agrega uno nuevo.",
                        actionLabel = "Agregar objetivo",
                        onActionClick = onAddClick
                    )
                }
            } else {
                state.filteredTotal?.let { totals ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = if (activeFilter == GoalFilter.ALL) "Total estimado pendiente" else "Mostrando: ${activeFilter.name.lowercase()}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            if (totals.minTotal == totals.maxTotal) {
                                Text(formatMoneyCents(totals.minTotal), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            } else {
                                Text(stringResource(R.string.goals_range, formatMoneyCents(totals.minTotal), formatMoneyCents(totals.maxTotal)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }

                            if (activeFilter != GoalFilter.ALL && state.generalTotal != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Total general pendiente: ${formatMoneyCents(state.generalTotal!!.minTotal)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.pendingGoals, key = { it.id }) { goal ->
                        GoalListCard(goal = goal, onClick = { onGoalClick(goal.id) }, onDelete = { goalToDelete = goal })
                    }

                    if (state.completedGoals.isNotEmpty()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().clickable { expandedCompleted = !expandedCompleted }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.goals_completed_section, state.completedGoals.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Icon(if (expandedCompleted) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                            }
                        }
                        if (expandedCompleted) {
                            items(state.completedGoals, key = { it.goal.id }) { item ->
                                GoalListCard(goal = item.goal, onClick = { onGoalClick(item.goal.id) }, onDelete = { goalToDelete = item.goal })
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GoalListCard(goal: GoalEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        Card(
            modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = { showMenu = true })
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val iconColor = when (goal.priority) {
                    "ALTA" -> MaterialTheme.colorScheme.error
                    "MEDIA" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
                val priorityText = when (goal.priority) {
                    "ALTA" -> stringResource(R.string.priority_alta)
                    "MEDIA" -> stringResource(R.string.priority_media)
                    else -> stringResource(R.string.priority_baja)
                }

                Icon(Icons.Default.Flag, contentDescription = priorityText, tint = iconColor)
                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    Text(goal.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(priorityText, style = MaterialTheme.typography.labelSmall, color = iconColor, fontWeight = FontWeight.SemiBold)
                    val est = if (goal.estimatedMaxCents != null && goal.estimatedMaxCents != goal.estimatedCents) {
                        stringResource(R.string.goals_range, formatMoneyCents(goal.estimatedCents), formatMoneyCents(goal.estimatedMaxCents))
                    } else formatMoneyCents(goal.estimatedCents)
                    Text(stringResource(R.string.goals_est, est), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                onClick = { showMenu = false; onDelete() }
            )
        }
    }
}