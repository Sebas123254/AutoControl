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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatMoneyCents
import com.sierracode.autocontrol.core.format.formatMoneyCentsWithCode
import com.sierracode.autocontrol.core.ui.components.EmptyStateCard
import com.sierracode.autocontrol.core.ui.components.showUndoSnackbar
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
        val generalTotals = calculatePendingTotals(allPending.map { GoalCalcData(it.estimatedCents, it.estimatedMaxCents, it.savedCents) })
        val filteredTotals = calculatePendingTotals(pending.map { GoalCalcData(it.estimatedCents, it.estimatedMaxCents, it.savedCents) })

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

    /** Borra inmediatamente; el Snackbar con "Deshacer" se maneja en la UI. */
    suspend fun deleteGoal(id: String) {
        goalRepo.delete(id)
    }

    /** Restaura un goal previamente borrado (undo). */
    fun restoreGoal(goal: GoalEntity) {
        viewModelScope.launch { goalRepo.save(goal) }
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

    val state by vm.uiState.collectAsStateWithLifecycle()
    val activeFilter by vm.activeFilter.collectAsStateWithLifecycle()
    val activeSort by vm.activeSort.collectAsStateWithLifecycle()

    var expandedCompleted by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val priorityAlta = stringResource(R.string.priority_alta)
    val priorityMedia = stringResource(R.string.priority_media)
    val priorityBaja = stringResource(R.string.priority_baja)
    val undoLabel = stringResource(R.string.action_undo)

    val filterAllLabel = stringResource(R.string.goals_filter_all, state.countAll)
    val filterHighLabel = stringResource(R.string.goals_filter_count, priorityAlta, state.countHigh)
    val filterMediumLabel = stringResource(R.string.goals_filter_count, priorityMedia, state.countMedium)
    val filterLowLabel = stringResource(R.string.goals_filter_count, priorityBaja, state.countLow)

    val currentFilterText = when (activeFilter) {
        GoalFilter.ALL -> filterAllLabel
        GoalFilter.HIGH -> priorityAlta
        GoalFilter.MEDIUM -> priorityMedia
        GoalFilter.LOW -> priorityBaja
    }

    // Callback reutilizable para borrar con undo
    val deleteWithUndo: (GoalEntity) -> Unit = { goal ->
        scope.launch {
            vm.deleteGoal(goal.id)
            val msg = context.getString(R.string.goal_deleted, goal.title)
            snackbarHostState.showUndoSnackbar(
                message = msg,
                actionLabel = undoLabel
            ) {
                vm.restoreGoal(goal)
            }
        }
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
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.goals_sort_title))
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.goals_sort_priority_desc)) }, onClick = { vm.setSort(GoalSort.PRIORITY_DESC); showSortMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.goals_sort_priority_asc)) }, onClick = { vm.setSort(GoalSort.PRIORITY_ASC); showSortMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.goals_sort_date)) }, onClick = { vm.setSort(GoalSort.TARGET_DATE); showSortMenu = false })
                            DropdownMenuItem(text = { Text(stringResource(R.string.goals_sort_amount)) }, onClick = { vm.setSort(GoalSort.AMOUNT_DESC); showSortMenu = false })
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
                item { FilterChip(selected = activeFilter == GoalFilter.ALL, onClick = { vm.setFilter(GoalFilter.ALL) }, label = { Text(filterAllLabel) }) }
                item { FilterChip(selected = activeFilter == GoalFilter.HIGH, onClick = { vm.setFilter(GoalFilter.HIGH) }, label = { Text(filterHighLabel) }) }
                item { FilterChip(selected = activeFilter == GoalFilter.MEDIUM, onClick = { vm.setFilter(GoalFilter.MEDIUM) }, label = { Text(filterMediumLabel) }) }
                item { FilterChip(selected = activeFilter == GoalFilter.LOW, onClick = { vm.setFilter(GoalFilter.LOW) }, label = { Text(filterLowLabel) }) }
            }

            if (state.pendingGoals.isEmpty() && state.completedGoals.isEmpty()) {
                Box(Modifier.weight(1f).padding(16.dp), contentAlignment = Alignment.Center) {
                    EmptyStateCard(
                        icon = Icons.Default.Flag,
                        title = if (activeFilter == GoalFilter.ALL) stringResource(R.string.goals_empty) else stringResource(R.string.goals_empty_priority, currentFilterText.lowercase()),
                        description = if (activeFilter == GoalFilter.ALL) stringResource(R.string.goals_empty_desc) else stringResource(R.string.goals_empty_priority_desc),
                        actionLabel = stringResource(R.string.goals_add),
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
                                text = if (activeFilter == GoalFilter.ALL) stringResource(R.string.goals_total_pending) else stringResource(R.string.goals_filter_showing, currentFilterText.lowercase()),
                                style = MaterialTheme.typography.labelLarge
                            )
                            if (totals.minTotal == totals.maxTotal) {
                                Text(formatMoneyCentsWithCode(totals.minTotal), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            } else {
                                Text(stringResource(R.string.goals_range, formatMoneyCentsWithCode(totals.minTotal), formatMoneyCentsWithCode(totals.maxTotal)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }

                            if (totals.totalSaved > 0L) {
                                val overallProgress = if (totals.minTotal > 0L) (totals.totalSaved.toFloat() / totals.minTotal.toFloat()).coerceIn(0f, 1f) else 0f
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.goals_total_saved_label, formatMoneyCents(totals.totalSaved), (overallProgress * 100).toInt()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { overallProgress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                )
                            }

                            if (activeFilter != GoalFilter.ALL && state.generalTotal != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.goals_general_total, formatMoneyCents(state.generalTotal!!.minTotal)),
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
                        GoalListCard(
                            goal = goal,
                            onClick = { onGoalClick(goal.id) },
                            onDelete = { deleteWithUndo(goal) }
                        )
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
                                GoalListCard(
                                    goal = item.goal,
                                    onClick = { onGoalClick(item.goal.id) },
                                    onDelete = { deleteWithUndo(item.goal) }
                                )
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

                    if (goal.status == "PENDIENTE") {
                        val progress = goalProgress(goal.savedCents, goal.estimatedCents)
                        val percent = (progress * 100).toInt()
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.goals_saved, formatMoneyCents(goal.savedCents), est, percent),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (progress >= 1f) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        Text(stringResource(R.string.goals_est, est), style = MaterialTheme.typography.bodyMedium)
                    }
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