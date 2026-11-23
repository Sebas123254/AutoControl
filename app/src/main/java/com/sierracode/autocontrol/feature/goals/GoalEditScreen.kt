package com.sierracode.autocontrol.feature.goals

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.format.localizedCategoryName
import com.sierracode.autocontrol.core.format.localizedPriority
import com.sierracode.autocontrol.core.ui.LocalAppConfig
import com.sierracode.autocontrol.core.ui.components.CurrencyField
import com.sierracode.autocontrol.data.local.entity.CategoryEntity
import com.sierracode.autocontrol.data.local.entity.GoalEntity
import com.sierracode.autocontrol.data.repository.CategoryRepository
import com.sierracode.autocontrol.data.repository.GoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GoalEditViewModel @Inject constructor(
    private val goalRepo: GoalRepository,
    categoryRepo: CategoryRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val vehicleId: String = savedStateHandle["vehicleId"] ?: ""
    private val goalId: String? = savedStateHandle.get<String>("goalId")?.takeIf { it.isNotBlank() }

    val categories: StateFlow<List<CategoryEntity>> = categoryRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val title: StateFlow<String> = savedStateHandle.getStateFlow("title", "")
    val amount: StateFlow<String> = savedStateHandle.getStateFlow("amount", "")
    val maxAmount: StateFlow<String> = savedStateHandle.getStateFlow("maxAmount", "")
    val savedAmount: StateFlow<String> = savedStateHandle.getStateFlow("savedAmount", "")
    val priority: StateFlow<String> = savedStateHandle.getStateFlow("priority", "MEDIA")
    val notes: StateFlow<String> = savedStateHandle.getStateFlow("notes", "")
    val hasUnsavedChanges: StateFlow<Boolean> = savedStateHandle.getStateFlow("hasUnsavedChanges", false)

    val targetDate: StateFlow<LocalDate?> = savedStateHandle
        .getStateFlow<Long?>("targetDateEpochDay", null)
        .map { it?.let { LocalDate.ofEpochDay(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val selectedCategoryId: StateFlow<String?> = savedStateHandle.getStateFlow("categoryId", null)
    val selectedCategory: StateFlow<CategoryEntity?> = combine(selectedCategoryId, categories) { id, cats ->
        cats.find { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        if (goalId != null && !savedStateHandle.contains("loadedFromDb")) {
            viewModelScope.launch {
                goalRepo.get(goalId)?.let { goal ->
                    savedStateHandle["title"] = goal.title
                    savedStateHandle["amount"] = (goal.estimatedCents / 100.0).toString()
                    savedStateHandle["maxAmount"] = goal.estimatedMaxCents?.let { (it / 100.0).toString() } ?: ""
                    savedStateHandle["savedAmount"] = if (goal.savedCents > 0) (goal.savedCents / 100.0).toString() else ""
                    savedStateHandle["priority"] = goal.priority
                    savedStateHandle["targetDateEpochDay"] = goal.targetDateEpochDay
                    savedStateHandle["notes"] = goal.notes ?: ""
                    savedStateHandle["categoryId"] = goal.categoryId
                }
                savedStateHandle["loadedFromDb"] = true
            }
        }
    }

    fun updateTitle(v: String) { savedStateHandle["title"] = v; markDirty(); error = null }
    fun updateAmount(v: String) { savedStateHandle["amount"] = v; markDirty(); error = null }
    fun updateMaxAmount(v: String) { savedStateHandle["maxAmount"] = v; markDirty(); error = null }
    fun updateSavedAmount(v: String) { savedStateHandle["savedAmount"] = v; markDirty(); error = null }
    fun updatePriority(v: String) { savedStateHandle["priority"] = v; markDirty() }
    fun updateTargetDate(d: LocalDate?) { savedStateHandle["targetDateEpochDay"] = d?.toEpochDay(); markDirty() }
    fun updateCategory(cat: CategoryEntity) { savedStateHandle["categoryId"] = cat.id; markDirty() }
    fun updateNotes(v: String) { savedStateHandle["notes"] = v; markDirty() }
    private fun markDirty() { savedStateHandle["hasUnsavedChanges"] = true }

    fun save(context: android.content.Context, onDone: () -> Unit) {
        val currentTitle = title.value
        val est = amount.value.toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
        val max = maxAmount.value.toBigDecimalOrNull()?.movePointRight(2)?.toLong()
        val saved = savedAmount.value.toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L

        if (currentTitle.isBlank() || est <= 0) {
            error = context.getString(R.string.goal_err_valid); return
        }
        if (est < 0 || (max != null && max < 0) || saved < 0) {
            error = context.getString(R.string.goal_err_negative); return
        }
        if (max != null && max < est) {
            error = context.getString(R.string.goal_err_max); return
        }

        val currentPriority = priority.value
        val currentCategoryId = selectedCategory.value?.id
        val currentNotes = notes.value
        val currentTargetDate = targetDate.value

        viewModelScope.launch {
            isLoading = true
            try {
                val existing = goalId?.let { goalRepo.get(it) }
                val goal = GoalEntity(
                    id = goalId ?: UUID.randomUUID().toString(),
                    vehicleId = vehicleId,
                    title = currentTitle,
                    estimatedCents = est,
                    estimatedMaxCents = max,
                    savedCents = saved,
                    priority = currentPriority,
                    targetDateEpochDay = currentTargetDate?.toEpochDay(),
                    status = existing?.status ?: "PENDIENTE",
                    categoryId = currentCategoryId,
                    notes = currentNotes.takeIf { it.isNotBlank() },
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    linkedExpenseId = existing?.linkedExpenseId
                )
                goalRepo.save(goal)

                savedStateHandle["hasUnsavedChanges"] = false
                onDone()
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (e: Exception) {
                error = com.sierracode.autocontrol.core.ui.UiError.from(e).asString(context)
            } finally {
                isLoading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditScreen(onDone: () -> Unit, vm: GoalEditViewModel = hiltViewModel()) {
    val config = LocalAppConfig.current
    val cats by vm.categories.collectAsStateWithLifecycle()

    val title by vm.title.collectAsStateWithLifecycle()
    val amount by vm.amount.collectAsStateWithLifecycle()
    val maxAmount by vm.maxAmount.collectAsStateWithLifecycle()
    val savedAmount by vm.savedAmount.collectAsStateWithLifecycle()
    val priority by vm.priority.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val targetDate by vm.targetDate.collectAsStateWithLifecycle()
    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()
    val hasUnsavedChanges by vm.hasUnsavedChanges.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }
    var expandedCat by remember { mutableStateOf(false) }
    var expandedPrio by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    BackHandler(enabled = hasUnsavedChanges) { showDiscardDialog = true }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = { Text(stringResource(R.string.discard_changes_msg)) },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onDone() }) {
                    Text(stringResource(R.string.action_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.action_keep_editing)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goals_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (hasUnsavedChanges) showDiscardDialog = true else onDone() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            var showMore by rememberSaveable { mutableStateOf(false) }
            val hasDetails = maxAmount.isNotBlank() || notes.isNotBlank() ||
                    targetDate != null || selectedCategory != null

            OutlinedTextField(
                value = title,
                onValueChange = { vm.updateTitle(it) },
                label = { Text(stringResource(R.string.goals_field_title)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth()
            )

            CurrencyField(
                value = amount,
                onValueChange = { vm.updateAmount(it) },
                label = stringResource(R.string.goals_field_amount),
                helperText = stringResource(R.string.goals_amount_hint),
                currencyCode = config.currencyCode,
                modifier = Modifier.fillMaxWidth()
            )

            CurrencyField(
                value = savedAmount,
                onValueChange = { vm.updateSavedAmount(it) },
                label = stringResource(R.string.goals_saved_amount),
                helperText = stringResource(R.string.goals_saved_amount_hint),
                currencyCode = config.currencyCode,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                stringResource(R.string.goals_field_priority),
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALTA", "MEDIA", "BAJA").forEach { p ->
                    FilterChip(
                        selected = priority == p,
                        onClick = { vm.updatePriority(p) },
                        label = { Text(localizedPriority(p).removePrefix("Prioridad "), maxLines = 1) }
                    )
                }
            }

            if (!hasDetails) {
                TextButton(onClick = { showMore = !showMore }) {
                    Text(if (showMore) "Menos detalles" else "Más detalles (opcional)")
                }
            }

            if (showMore || hasDetails) {
                CurrencyField(
                    value = maxAmount,
                    onValueChange = { vm.updateMaxAmount(it) },
                    label = stringResource(R.string.goals_field_max),
                    currencyCode = config.currencyCode,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(expanded = expandedCat, onExpandedChange = { expandedCat = !expandedCat }) {
                    OutlinedTextField(
                        value = selectedCategory?.let { localizedCategoryName(it.id, it.name) }
                            ?: stringResource(R.string.expense_edit_select_category),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.goals_field_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedCat) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expandedCat, onDismissRequest = { expandedCat = false }) {
                        cats.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(localizedCategoryName(c.id, c.name)) },
                                onClick = { vm.updateCategory(c); expandedCat = false }
                            )
                        }
                    }
                }

                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = targetDate?.let { formatDate(it.toEpochDay()) } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text(stringResource(R.string.goals_field_date)) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = stringResource(R.string.action_choose_date)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showDatePicker = true }
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { vm.updateNotes(it) },
                    label = { Text(stringResource(R.string.goals_field_notes)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (showDatePicker) {
                // Fix: UTC end-to-end.
                val initialMillis = (targetDate ?: LocalDate.now())
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            state.selectedDateMillis?.let { millis ->
                                vm.updateTargetDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                            }
                            showDatePicker = false
                        }) { Text(stringResource(R.string.action_ok)) }
                    },
                    dismissButton = {
                        Row {
                            if (targetDate != null) {
                                TextButton(onClick = {
                                    vm.updateTargetDate(null)
                                    showDatePicker = false
                                }) { Text(stringResource(R.string.action_clear)) }
                            }
                            TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
                        }
                    }
                ) { DatePicker(state = state) }
            }

            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = { vm.save(context, onDone) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                enabled = !vm.isLoading
            ) {
                if (vm.isLoading) CircularProgressIndicator(Modifier.size(24.dp))
                else Text(stringResource(R.string.goals_save))
            }
        }
    }
}