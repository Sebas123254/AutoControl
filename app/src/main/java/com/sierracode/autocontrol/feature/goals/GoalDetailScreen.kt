package com.sierracode.autocontrol.feature.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.format.formatMoneyCents
import com.sierracode.autocontrol.core.ui.LocalAppConfig
import com.sierracode.autocontrol.core.ui.components.CurrencyField
import com.sierracode.autocontrol.domain.calc.goalProgress
import com.sierracode.autocontrol.data.local.entity.CategoryEntity
import com.sierracode.autocontrol.data.local.entity.ExpenseEntity
import com.sierracode.autocontrol.data.local.entity.GoalEntity
import com.sierracode.autocontrol.data.repository.CategoryRepository
import com.sierracode.autocontrol.data.repository.ExpenseRepository
import com.sierracode.autocontrol.data.repository.GoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@HiltViewModel
class GoalDetailViewModel @Inject constructor(
    private val goalRepo: GoalRepository,
    private val expenseRepo: ExpenseRepository,
    categoryRepo: CategoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val goalId: String = savedStateHandle["goalId"] ?: ""

    val categories = categoryRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val goalFlow = MutableStateFlow<GoalEntity?>(null)

    val uiState = combine(goalFlow, categories) { goal, cats ->
        if (goal == null) return@combine null
        val expense = goal.linkedExpenseId?.let { expenseRepo.get(it) }
        val categoryName = cats.find { it.id == goal.categoryId }?.name
        Triple(goal, expense, categoryName)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            goalFlow.value = goalRepo.get(goalId)
        }
    }

    fun addSavings(amountCents: Long) {
        viewModelScope.launch {
            val current = goalFlow.value ?: return@launch
            val updated = current.copy(savedCents = (current.savedCents + amountCents).coerceAtLeast(0L))
            goalRepo.save(updated)
            goalFlow.value = updated
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onBuyClick: () -> Unit,
    vm: GoalDetailViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val config = LocalAppConfig.current
    var showAddSavingDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goals_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                }
            )
        }
    ) { padding ->
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val (goal, linkedExpense, categoryName) = state!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = goal.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { },
                        label = { Text(stringResource(R.string.goals_priority, goal.priority)) }
                    )
                    if (categoryName != null) {
                        AssistChip(
                            onClick = { },
                            label = { Text(categoryName) }
                        )
                    }
                }

                HorizontalDivider()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (goal.status == "PENDIENTE") {
                            Text(stringResource(R.string.goals_estimated_cost), style = MaterialTheme.typography.labelLarge)
                            if (goal.estimatedMaxCents != null) {
                                Text(
                                    stringResource(R.string.goals_range, formatMoneyCents(goal.estimatedCents), formatMoneyCents(goal.estimatedMaxCents)),
                                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(formatMoneyCents(goal.estimatedCents), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }

                            val est = if (goal.estimatedMaxCents != null && goal.estimatedMaxCents != goal.estimatedCents) {
                                stringResource(R.string.goals_range, formatMoneyCents(goal.estimatedCents), formatMoneyCents(goal.estimatedMaxCents))
                            } else formatMoneyCents(goal.estimatedCents)
                            val progress = goalProgress(goal.savedCents, goal.estimatedCents)
                            val percent = (progress * 100).toInt()

                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.goals_saved, formatMoneyCents(goal.savedCents), est, percent),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = if (progress >= 1f) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        } else {
                            Text(stringResource(R.string.goals_status_completed), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.goals_estimated_cost), style = MaterialTheme.typography.labelMedium)
                            Text(formatMoneyCents(goal.estimatedCents), style = MaterialTheme.typography.titleMedium)

                            linkedExpense?.let { exp ->
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.goals_real_cost), style = MaterialTheme.typography.labelMedium)
                                Text(formatMoneyCents(exp.amountCents), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                                val diff = exp.amountCents - goal.estimatedCents
                                if (diff > 0) {
                                    Text(stringResource(R.string.goals_diff_over, formatMoneyCents(diff)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                } else if (diff < 0) {
                                    Text(stringResource(R.string.goals_diff_saved, formatMoneyCents(-diff)), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                if (goal.targetDateEpochDay != null) {
                    Text(stringResource(R.string.goals_target_date, formatDate(goal.targetDateEpochDay)), style = MaterialTheme.typography.bodyLarge)
                }

                if (!goal.notes.isNullOrBlank()) {
                    Text(stringResource(R.string.goals_notes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(goal.notes, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.weight(1f))

                if (goal.status == "PENDIENTE") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAddSavingDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Icon(Icons.Default.Savings, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.goals_add_saving))
                        }

                        Button(
                            onClick = onBuyClick,
                            modifier = Modifier
                                .weight(1.2f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.goals_buy_action), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }

            if (showAddSavingDialog) {
                var savingInput by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showAddSavingDialog = false },
                    title = { Text(stringResource(R.string.goals_add_saving_title)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.goals_add_saving_desc))
                            CurrencyField(
                                value = savingInput,
                                onValueChange = { savingInput = it },
                                label = stringResource(R.string.goals_add_saving),
                                currencyCode = config.currencyCode,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val cents = savingInput.toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
                                if (cents > 0L) {
                                    vm.addSavings(cents)
                                }
                                showAddSavingDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.action_ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddSavingDialog = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
        }
    }
}