package com.sierracode.autocontrol.feature.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.data.local.entity.CategoryEntity
import com.sierracode.autocontrol.data.local.entity.GoalEntity
import com.sierracode.autocontrol.data.repository.CategoryRepository
import com.sierracode.autocontrol.data.repository.GoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GoalEditViewModel @Inject constructor(
    private val goalRepo: GoalRepository,
    categoryRepo: CategoryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val vehicleId: String = savedStateHandle["vehicleId"] ?: ""
    // AQUÍ ESTÁ EL ARREGLO: takeIf { it.isNotBlank() } evita que "" se use como ID
    private val goalId: String? = savedStateHandle.get<String>("goalId")?.takeIf { it.isNotBlank() }

    val categories = categoryRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var title by mutableStateOf("")
    var amount by mutableStateOf("")
    var maxAmount by mutableStateOf("")
    var priority by mutableStateOf("MEDIA")
    var targetDate by mutableStateOf<LocalDate?>(null)
    var selectedCategory by mutableStateOf<CategoryEntity?>(null)
    var notes by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    init {
        if (goalId != null) {
            viewModelScope.launch {
                goalRepo.get(goalId)?.let { goal ->
                    title = goal.title
                    amount = (goal.estimatedCents / 100.0).toString()
                    maxAmount = goal.estimatedMaxCents?.let { (it / 100.0).toString() } ?: ""
                    priority = goal.priority
                    targetDate = goal.targetDateEpochDay?.let { LocalDate.ofEpochDay(it) }
                    notes = goal.notes ?: ""

                    val allCats = categories.value
                    selectedCategory = allCats.find { it.id == goal.categoryId }
                }
            }
        }
    }

    fun save(context: android.content.Context, onDone: () -> Unit) {
        val est = amount.toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
        val max = maxAmount.toBigDecimalOrNull()?.movePointRight(2)?.toLong()

        if (title.isBlank() || est <= 0) {
            error = context.getString(R.string.goal_err_valid)
            return
        }

        viewModelScope.launch {
            isLoading = true
            try {
                val existing = goalId?.let { goalRepo.get(it) }
                val goal = GoalEntity(
                    id = goalId ?: UUID.randomUUID().toString(), // Si goalId es null, genera UUID
                    vehicleId = vehicleId,
                    title = title,
                    estimatedCents = est,
                    estimatedMaxCents = max,
                    priority = priority,
                    targetDateEpochDay = targetDate?.toEpochDay(),
                    status = existing?.status ?: "PENDIENTE",
                    categoryId = selectedCategory?.id,
                    notes = notes.takeIf { it.isNotBlank() },
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    linkedExpenseId = existing?.linkedExpenseId
                )
                goalRepo.save(goal)
                onDone()
            } catch (e: Exception) {
                error = context.getString(R.string.expense_edit_err_generic, e.message ?: "")
            } finally {
                isLoading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditScreen(onDone: () -> Unit, vm: GoalEditViewModel = hiltViewModel()) {
    val cats by vm.categories.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var expandedCat by remember { mutableStateOf(false) }
    var expandedPrio by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goals_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(value = vm.title, onValueChange = { vm.title = it }, label = { Text(stringResource(R.string.goals_field_title)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = vm.amount, onValueChange = { vm.amount = it }, label = { Text(stringResource(R.string.goals_field_amount)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = vm.maxAmount, onValueChange = { vm.maxAmount = it }, label = { Text(stringResource(R.string.goals_field_max)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

            ExposedDropdownMenuBox(expanded = expandedPrio, onExpandedChange = { expandedPrio = !expandedPrio }) {
                OutlinedTextField(value = vm.priority, onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.goals_field_priority)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedPrio) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = expandedPrio, onDismissRequest = { expandedPrio = false }) {
                    listOf("ALTA", "MEDIA", "BAJA").forEach { p ->
                        DropdownMenuItem(text = { Text(p) }, onClick = { vm.priority = p; expandedPrio = false })
                    }
                }
            }

            ExposedDropdownMenuBox(expanded = expandedCat, onExpandedChange = { expandedCat = !expandedCat }) {
                OutlinedTextField(value = vm.selectedCategory?.name ?: stringResource(R.string.expense_edit_select_category), onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.goals_field_category)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedCat) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded = expandedCat, onDismissRequest = { expandedCat = false }) {
                    cats.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { vm.selectedCategory = c; expandedCat = false }) }
                }
            }

            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = vm.targetDate?.let { formatDate(it.toEpochDay()) } ?: "",
                    onValueChange = {}, readOnly = true, enabled = false, label = { Text(stringResource(R.string.goals_field_date)) },
                    trailingIcon = { Icon(Icons.Default.DateRange, null) },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Box(Modifier.matchParentSize().clickable { showDatePicker = true })
            }

            if (showDatePicker) {
                val state = rememberDatePickerState(initialSelectedDateMillis = vm.targetDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli())
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            state.selectedDateMillis?.let { vm.targetDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() };
                            showDatePicker = false
                        }) { Text(stringResource(R.string.action_understood)) }
                    },
                    dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } }
                ) { DatePicker(state = state) }
            }

            OutlinedTextField(value = vm.notes, onValueChange = { vm.notes = it }, label = { Text(stringResource(R.string.goals_field_notes)) }, modifier = Modifier.fillMaxWidth())

            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = { vm.save(context, onDone) }, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !vm.isLoading) {
                if (vm.isLoading) CircularProgressIndicator(Modifier.size(24.dp)) else Text(stringResource(R.string.goals_save))
            }
        }
    }
}