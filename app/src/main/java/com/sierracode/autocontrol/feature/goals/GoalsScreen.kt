package com.sierracode.autocontrol.feature.goals

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flag
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
import com.sierracode.autocontrol.data.local.entity.GoalEntity
import com.sierracode.autocontrol.data.repository.ExpenseRepository
import com.sierracode.autocontrol.data.repository.GoalRepository
import com.sierracode.autocontrol.domain.calc.GoalCalcData
import com.sierracode.autocontrol.domain.calc.calculatePendingTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoalWithExpense(val goal: GoalEntity, val realCents: Long?)

data class GoalsUiState(
    val pendingGoals: List<GoalEntity> = emptyList(),
    val completedGoals: List<GoalWithExpense> = emptyList(),
    val totals: com.sierracode.autocontrol.domain.calc.GoalTotals? = null,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepo: GoalRepository,
    private val expenseRepo: ExpenseRepository
) : ViewModel() {
    private val vehicleIdFlow = MutableStateFlow<String?>(null)

    val uiState = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(GoalsUiState(isLoading = false)) else combine(
            goalRepo.observeByVehicle(id),
            expenseRepo.observeByVehicle(id)
        ) { goals, expenses ->
            // --- FILTRO DE SEGURIDAD: Ignoramos los objetivos con ID vacío ---
            val validGoals = goals.filter { it.id.isNotBlank() }

            val pending = validGoals.filter { it.status == "PENDIENTE" }
            val completed = validGoals.filter { it.status == "COMPRADO" }.map { g ->
                val exp = expenses.find { it.id == g.linkedExpenseId }
                GoalWithExpense(g, exp?.amountCents)
            }
            val calcData = pending.map { GoalCalcData(it.estimatedCents, it.estimatedMaxCents) }
            GoalsUiState(pending, completed, calculatePendingTotals(calcData), false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    fun loadVehicle(id: String) { vehicleIdFlow.value = id }

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
    onBack: () -> Unit,
    onAddClick: () -> Unit,
    onGoalClick: (String) -> Unit,
    vm: GoalsViewModel = hiltViewModel()
) {
    LaunchedEffect(vehicleId) { vm.loadVehicle(vehicleId) }
    val state by vm.uiState.collectAsState()

    var expandedCompleted by remember { mutableStateOf(false) }
    var goalToDelete by remember { mutableStateOf<GoalEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    if (goalToDelete != null) {
        AlertDialog(
            onDismissRequest = { goalToDelete = null },
            title = { Text(stringResource(R.string.goal_delete_title)) },
            text = { Text(stringResource(R.string.goal_delete_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteGoal(goalToDelete!!.id) {
                        goalToDelete = null
                    }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { goalToDelete = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goals_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.Flag, contentDescription = stringResource(R.string.vehicles_title)) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.goals_add)) }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Tarjeta de Resumen
            state.totals?.let { totals ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.goals_total_pending), style = MaterialTheme.typography.labelLarge)
                        if (totals.minTotal == totals.maxTotal) {
                            Text(formatMoneyCents(totals.minTotal), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        } else {
                            Text(stringResource(R.string.goals_range, formatMoneyCents(totals.minTotal), formatMoneyCents(totals.maxTotal)), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } ?: Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.goals_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        onDelete = { goalToDelete = goal }
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
                                onDelete = { goalToDelete = item.goal }
                            )
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
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                val iconColor = when (goal.priority) {
                    "ALTA" -> MaterialTheme.colorScheme.error
                    "MEDIA" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
                Icon(Icons.Default.Flag, contentDescription = null, tint = iconColor)
                Spacer(Modifier.width(16.dp))

                Column(Modifier.weight(1f)) {
                    Text(goal.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val est = if (goal.estimatedMaxCents != null && goal.estimatedMaxCents != goal.estimatedCents) {
                        stringResource(R.string.goals_range, formatMoneyCents(goal.estimatedCents), formatMoneyCents(goal.estimatedMaxCents))
                    } else formatMoneyCents(goal.estimatedCents)
                    Text(stringResource(R.string.goals_est, est), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                onClick = {
                    showMenu = false
                    onDelete()
                }
            )
        }
    }
}