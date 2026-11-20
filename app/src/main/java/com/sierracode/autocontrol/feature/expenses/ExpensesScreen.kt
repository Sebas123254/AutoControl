package com.sierracode.autocontrol.feature.expenses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.format.formatMoneyCents
import com.sierracode.autocontrol.core.ui.components.EmptyStateCard
import com.sierracode.autocontrol.core.ui.components.showUndoSnackbar
import com.sierracode.autocontrol.data.local.entity.ExpenseEntity
import com.sierracode.autocontrol.data.repository.AttachmentRepository
import com.sierracode.autocontrol.data.repository.CategoryRepository
import com.sierracode.autocontrol.data.repository.ExpenseRepository
import com.sierracode.autocontrol.domain.calc.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val attachmentRepo: AttachmentRepository,
    categoryRepo: CategoryRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val vehicleIdFlow = MutableStateFlow<String?>(null)

    val allCategories = categoryRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val queryFlow = MutableStateFlow("")
    val searchQuery: StateFlow<String> = queryFlow.asStateFlow()
    private val periodFlow = MutableStateFlow(ExpensePeriod.ALL)
    private val customMonthFlow = MutableStateFlow<YearMonth?>(null)
    private val categoryIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    var isSearchActive by mutableStateOf(false)
        private set

    fun updateSearchActive(active: Boolean) {
        isSearchActive = active
        if (!active) queryFlow.value = ""
    }

    fun setQuery(q: String) { queryFlow.value = q }
    fun setPeriod(p: ExpensePeriod) {
        periodFlow.value = p
        if (p != ExpensePeriod.CUSTOM) customMonthFlow.value = null
    }
    fun setCustomMonth(m: YearMonth) {
        customMonthFlow.value = m
        periodFlow.value = ExpensePeriod.CUSTOM
    }
    fun setCategories(ids: Set<String>) { categoryIdsFlow.value = ids }

    val filterState = combine(queryFlow.debounce(250), periodFlow, customMonthFlow, categoryIdsFlow) { q, p, m, c ->
        ExpenseFilterState(q, p, m, c)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseFilterState())

    // Conjunto de IDs pendientes de borrado (borrado diferido)
    private val pendingDeleteIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    val pendingDeleteIds: StateFlow<Set<String>> = pendingDeleteIdsFlow.asStateFlow()

    val filteredExpenses = combine(
        vehicleIdFlow.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else expenseRepo.observeByVehicle(id) },
        allCategories,
        filterState,
        pendingDeleteIdsFlow
    ) { expenses, cats, state, pendingIds ->
        val visible = if (pendingIds.isEmpty()) expenses else expenses.filter { it.id !in pendingIds }
        filterExpenses(visible, cats, state)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCents = filteredExpenses.map { calculateTotalCents(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    var selectedExpense by mutableStateOf<ExpenseEntity?>(null)

    // Estado observable de selección respaldado por savedStateHandle como List<String>
    val selectedIds: StateFlow<Set<String>> = savedStateHandle
        .getStateFlow("selectedIds", emptyList<String>())
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // isSelectionMode derivado directamente de si selectedIds no está vacío
    val isSelectionMode: StateFlow<Boolean> = selectedIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    var isDeleting by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            combine(filteredExpenses, vehicleIdFlow) { visible, vId -> Pair(visible, vId) }
                .collect { (visible, vId) ->
                    // Solo depurar selección si hay un vehículo cargado y la lista proviene de la BD
                    if (vId != null) {
                        val visibleIds = visible.map { it.id }.toSet()
                        val currentSelected = selectedIds.value
                        if (currentSelected.isNotEmpty()) {
                            val newSelection = currentSelected.intersect(visibleIds)
                            if (newSelection.size != currentSelected.size) {
                                savedStateHandle["selectedIds"] = newSelection.toList()
                            }
                        }
                    }
                }
        }
    }

    fun loadVehicle(id: String) { vehicleIdFlow.value = id }
    fun selectExpense(expense: ExpenseEntity) { selectedExpense = expense }
    fun dismissDialog() { selectedExpense = null }

    fun toggleSelection(id: String) {
        val current = selectedIds.value
        val newSet = if (current.contains(id)) current - id else current + id
        savedStateHandle["selectedIds"] = newSet.toList()
    }

    fun enterSelectionMode(id: String) {
        savedStateHandle["selectedIds"] = listOf(id)
    }

    fun selectAll() {
        val allIds = filteredExpenses.value.map { it.id }
        savedStateHandle["selectedIds"] = allIds
    }

    fun clearSelection() {
        savedStateHandle["selectedIds"] = emptyList<String>()
    }

    /**
     * Oculta de inmediato los IDs seleccionados para el borrado diferido.
     * Retorna la lista de IDs a borrar para que la UI maneje el Snackbar con Undo.
     */
    fun startDeferredDelete(): List<String> {
        val idsToDelete = selectedIds.value.toList()
        if (idsToDelete.isEmpty()) return emptyList()

        pendingDeleteIdsFlow.value = pendingDeleteIdsFlow.value + idsToDelete
        clearSelection()
        return idsToDelete
    }

    /**
     * El usuario pulsó Deshacer: los IDs reaparecen inmediatamente sin tocar Room.
     */
    fun undoDeferredDelete(ids: List<String>) {
        pendingDeleteIdsFlow.value = pendingDeleteIdsFlow.value - ids.toSet()
    }

    /**
     * El Snackbar expiró o fue descartado: confirmamos el borrado en Room.
     */
    fun commitDeferredDelete(ids: List<String>) {
        viewModelScope.launch {
            try {
                expenseRepo.deleteMultiple(ids)
            } finally {
                pendingDeleteIdsFlow.value = pendingDeleteIdsFlow.value - ids.toSet()
            }
        }
    }

    /**
     * Si el ViewModel se destruye con borrados pendientes, se confirman en Room.
     */
    override fun onCleared() {
        super.onCleared()
        val pending = pendingDeleteIdsFlow.value.toList()
        if (pending.isNotEmpty()) {
            kotlinx.coroutines.runBlocking {
                try {
                    expenseRepo.deleteMultiple(pending)
                } catch (e: Exception) {
                    // Ignorar al salir
                }
            }
        }
    }

    fun clearFilters() {
        queryFlow.value = ""
        periodFlow.value = ExpensePeriod.ALL
        customMonthFlow.value = null
        categoryIdsFlow.value = emptySet()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExpensesScreen(
    vehicleId: String,
    vehicleName: String,
    onDetailClick: (String) -> Unit,
    onAddClick: () -> Unit,
    vm: ExpensesViewModel = hiltViewModel()
) {
    LaunchedEffect(vehicleId) { vm.loadVehicle(vehicleId) }

    val expenses by vm.filteredExpenses.collectAsStateWithLifecycle()
    val totalCents by vm.totalCents.collectAsStateWithLifecycle()
    val categories by vm.allCategories.collectAsStateWithLifecycle()
    val filterState by vm.filterState.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val isSelectionMode by vm.isSelectionMode.collectAsStateWithLifecycle()
    val selectedIds by vm.selectedIds.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val selectedDesc = stringResource(R.string.expenses_tooltip_single)
    val notSelectedDesc = stringResource(R.string.expenses_tooltip_none)
    val undoLabel = stringResource(R.string.action_undo)

    BackHandler(enabled = isSelectionMode) { vm.clearSelection() }
    BackHandler(enabled = vm.isSearchActive) { vm.updateSearchActive(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.expenses_delete_title)) },
            text = { Text(stringResource(R.string.expenses_delete_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        val idsToDelete = vm.startDeferredDelete()
                        if (idsToDelete.isNotEmpty()) {
                            val count = idsToDelete.size
                            val message = context.resources.getQuantityString(
                                R.plurals.expenses_deleted_count, count, count
                            )
                            scope.launch {
                                val undone = snackbarHostState.showUndoSnackbar(
                                    message = message,
                                    actionLabel = undoLabel,
                                    duration = SnackbarDuration.Long,
                                    onUndo = {
                                        vm.undoDeferredDelete(idsToDelete)
                                    }
                                )
                                if (!undone) {
                                    vm.commitDeferredDelete(idsToDelete)
                                }
                            }
                        }
                    },
                    enabled = !vm.isDeleting
                ) {
                    if (vm.isDeleting) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    else Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    if (showCategorySheet) {
        ModalBottomSheet(onDismissRequest = { showCategorySheet = false }) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.filter_category_sheet_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                categories.forEach { cat ->
                    val isChecked = filterState.categoryIds.contains(cat.id)
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            val newSet = if (isChecked) filterState.categoryIds - cat.id else filterState.categoryIds + cat.id
                            vm.setCategories(newSet)
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = isChecked, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(cat.name)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { showCategorySheet = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.filter_apply))
                }
            }
        }
    }

    if (showMonthPicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showMonthPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        vm.setCustomMonth(YearMonth.from(date))
                    }
                    showMonthPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showMonthPicker = false }) { Text(stringResource(R.string.action_cancel)) } }
        ) {
            DatePicker(state = state)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!isSelectionMode && !vm.isSearchActive) {
                FloatingActionButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.expenses_add))
                }
            }
        },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.expenses_selected_count, selectedIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = { vm.clearSelection() }) { Icon(Icons.Default.Close, stringResource(R.string.expenses_cancel_selection)) }
                    },
                    actions = {
                        IconButton(onClick = { vm.selectAll() }) { Icon(Icons.Default.SelectAll, stringResource(R.string.expenses_select_all)) }
                        IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            } else if (vm.isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { vm.setQuery(it) },
                            placeholder = { Text(stringResource(R.string.expenses_search_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Clear, stringResource(R.string.action_clear)) }
                                }
                            }
                        )
                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { vm.updateSearchActive(false) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.expenses_title), fontWeight = FontWeight.Bold)
                            if (vehicleName.isNotBlank()) {
                                Text(vehicleName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.updateSearchActive(true) }) { Icon(Icons.Default.Search, stringResource(R.string.action_search)) }
                    }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = filterState.period == ExpensePeriod.ALL && filterState.categoryIds.isEmpty() && filterState.query.isEmpty(),
                        onClick = { vm.clearFilters() },
                        label = { Text(stringResource(R.string.expenses_filter_all)) }
                    )
                }
                item {
                    FilterChip(
                        selected = filterState.period == ExpensePeriod.THIS_MONTH,
                        onClick = { vm.setPeriod(ExpensePeriod.THIS_MONTH) },
                        label = { Text(stringResource(R.string.dashboard_period_this_month)) }
                    )
                }
                item {
                    FilterChip(
                        selected = filterState.period == ExpensePeriod.LAST_MONTH,
                        onClick = { vm.setPeriod(ExpensePeriod.LAST_MONTH) },
                        label = { Text(stringResource(R.string.dashboard_period_last_month)) }
                    )
                }
                item {
                    FilterChip(
                        selected = filterState.period == ExpensePeriod.CUSTOM,
                        onClick = { showMonthPicker = true },
                        label = {
                            val text = filterState.customMonth?.let { it.format(DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault())) } ?: stringResource(R.string.filter_month_placeholder)
                            Text(text)
                        }
                    )
                }
                item {
                    FilterChip(
                        selected = filterState.categoryIds.isNotEmpty(),
                        onClick = { showCategorySheet = true },
                        label = { Text(if (filterState.categoryIds.isEmpty()) stringResource(R.string.expenses_filter_categories) else stringResource(R.string.expenses_filter_categories_count, filterState.categoryIds.size)) }
                    )
                }
            }

            if (expenses.isNotEmpty()) {
                val count = expenses.size
                val total = formatMoneyCents(totalCents)
                val plural = pluralStringResource(R.plurals.expenses_count, count, count)
                Text(
                    text = "$plural · $total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }
                )
            }

            if (expenses.isEmpty()) {
                val isFiltering = filterState.query.isNotBlank() || filterState.period != ExpensePeriod.ALL || filterState.categoryIds.isNotEmpty()
                Box(Modifier.weight(1f).padding(16.dp), contentAlignment = Alignment.Center) {
                    EmptyStateCard(
                        icon = Icons.Default.Receipt,
                        title = if (isFiltering) stringResource(R.string.expenses_empty_filter) else stringResource(R.string.expenses_empty),
                        description = if (isFiltering) stringResource(R.string.expenses_empty_filter) else stringResource(R.string.expenses_empty),
                        actionLabel = if (isFiltering) stringResource(R.string.expenses_filter_clear) else stringResource(R.string.expenses_add),
                        onActionClick = { if (isFiltering) vm.clearFilters() else onAddClick() }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(expenses, key = { it.id }) { expense ->
                        val isSelected = selectedIds.contains(expense.id)
                        ExpenseRow(
                            expense = expense,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    vm.toggleSelection(expense.id)
                                } else {
                                    onDetailClick(expense.id)
                                }
                            },
                            onLongClick = { vm.enterSelectionMode(expense.id) },
                            selectedDesc = selectedDesc,
                            notSelectedDesc = notSelectedDesc
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpenseRow(
    expense: ExpenseEntity,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selectedDesc: String,
    notSelectedDesc: String
) {
    val semanticsModifier = if (isSelectionMode) {
        Modifier.semantics {
            this.selected = isSelected
            contentDescription = if (isSelected) selectedDesc else notSelectedDesc
        }
    } else Modifier

    val displayTitle = if (expense.customCategoryName != null) {
        "Otro · ${expense.customCategoryName}"
    } else {
        expense.description ?: stringResource(R.string.expenses_default_name)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(semanticsModifier),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = formatDate(expense.dateEpochDay), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = formatMoneyCents(expense.amountCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
