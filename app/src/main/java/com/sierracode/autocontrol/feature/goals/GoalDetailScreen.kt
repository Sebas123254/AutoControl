package com.sierracode.autocontrol.feature.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
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
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.format.formatMoneyCents
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

    // Combinamos el Goal con su Expense vinculado y las categorías para mostrar el nombre de la categoría
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    onBack: () -> Unit,
    onEditClick: () -> Unit,
    onBuyClick: () -> Unit,
    vm: GoalDetailViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsState()

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

                // Costos
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

                // Botón de comprar solo si está pendiente
                if (goal.status == "PENDIENTE") {
                    Button(
                        onClick = onBuyClick,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.goals_buy_action), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}