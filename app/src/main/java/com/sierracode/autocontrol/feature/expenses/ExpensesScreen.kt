package com.sierracode.autocontrol.feature.expenses

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sierracode.autocontrol.R
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.format.formatMoneyCents
import com.sierracode.autocontrol.core.ui.components.EmptyState
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
    categoryRepo: CategoryRepository
) : ViewModel() {
    private val vehicleIdFlow = MutableStateFlow<String?>(null)

    val allCategories = categoryRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val queryFlow = MutableStateFlow("")
    private val periodFlow = MutableStateFlow(ExpensePeriod.ALL)
    private val customMonthFlow = MutableStateFlow<YearMonth?>(null)
    private val categoryIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    var isSearchActive by mutableStateOf(false)
        private set

    // RENOMBRADO: Para evitar choque con el setter generado automáticamente por JVM
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

    val filteredExpenses = combine(
        vehicleIdFlow.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else expenseRepo.observeByVehicle(id) },
        allCategories,
        filterState
    ) { expenses, cats, state ->
        filterExpenses(expenses, cats, state)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCents = filteredExpenses.map { calculateTotalCents(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    var selectedExpense by mutableStateOf<ExpenseEntity?>(null)

    var isSelectionMode by mutableStateOf(false)
        private set
    var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var isDeleting by mutableStateOf(false)
        private set

    init {
        // Si cambian los filtros mientras estamos seleccionando, quitamos los que ya no se ven
        viewModelScope.launch {
            filteredExpenses.collect { visible ->
                if (isSelectionMode) {
                    val visibleIds = visible.map { it.id }.toSet()
                    val newSelection = selectedIds.intersect(visibleIds)
                    selectedIds = newSelection
                    if (newSelection.isEmpty()) isSelectionMode = false
                }
            }
        }
    }

    fun loadVehicle(id: String) { vehicleIdFlow.value = id }

    fun selectExpense(expense: ExpenseEntity) { selectedExpense = expense }
    fun dismissDialog() { selectedExpense = null }

    fun toggleSelection(id: String) {
        selectedIds = if (selectedIds.contains(id)) {
            val newSet = selectedIds - id
            if (newSet.isEmpty()) isSelectionMode = false
            newSet
        } else selectedIds + id
    }

    fun enterSelectionMode(id: String) {
        if (!isSelectionMode) { isSelectionMode = true; selectedIds = setOf(id) }
    }

    fun selectAll() {
        selectedIds = filteredExpenses.value.map { it.id }.toSet()
    }

    fun clearSelection() { isSelectionMode = false; selectedIds = emptySet() }

    fun deleteSelected(onSuccess: (Int) -> Unit) {
        if (selectedIds.isEmpty()) return
        val count = selectedIds.size
        viewModelScope.launch {
            isDeleting = true
            try {
                expenseRepo.deleteMultiple(selectedIds.toList())
                clearSelection()
                onSuccess(count)
            } catch (e: Exception) {
                // Error
            } finally {
                isDeleting = false
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
    onEditClick: (String) -> Unit,
    vm: ExpensesViewModel = hiltViewModel()
) {
    LaunchedEffect(vehicleId) { vm.loadVehicle(vehicleId) }

    val expenses by vm.filteredExpenses.collectAsState()
    val totalCents by vm.totalCents.collectAsState()
    val categories by vm.allCategories.collectAsState()
    val filterState by vm.filterState.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler(enabled = vm.isSelectionMode) { vm.clearSelection() }
    BackHandler(enabled = vm.isSearchActive) { vm.updateSearchActive(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar ${vm.selectedIds.size} gasto(s)") },
            text = { Text("Se eliminarán también sus recibos. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    vm.deleteSelected { count ->
                        kotlinx.coroutines.MainScope().launch {
                            snackbarHostState.showSnackbar("$count gasto(s) eliminado(s)")
                        }
                    }
                }, enabled = !vm.isDeleting) {
                    if (vm.isDeleting) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    else Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showCategorySheet) {
        ModalBottomSheet(onDismissRequest = { showCategorySheet = false }) {
            Column(Modifier.padding(16.dp)) {
                Text("Filtrar por categoría", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
                    Text("Aplicar filtros")
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
                    state.selectedDateMillis?.let {
                        val date = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        vm.setCustomMonth(YearMonth.from(date))
                    }
                    showMonthPicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { showMonthPicker = false }) { Text("Cancelar") } }
        ) {
            DatePicker(state = state)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (vm.isSelectionMode) {
                TopAppBar(
                    title = { Text("${vm.selectedIds.size} seleccionados") },
                    navigationIcon = {
                        IconButton(onClick = { vm.clearSelection() }) { Icon(Icons.Default.Close, "Cancelar") }
                    },
                    actions = {
                        IconButton(onClick = { vm.selectAll() }) { Icon(Icons.Default.SelectAll, "Seleccionar todo") }
                        IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, "Borrar", tint = MaterialTheme.colorScheme.error) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            } else if (vm.isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = filterState.query,
                            onValueChange = { vm.setQuery(it) },
                            placeholder = { Text("Buscar gasto...") },
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
                                if (filterState.query.isNotEmpty()) {
                                    IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Clear, "Limpiar") }
                                }
                            }
                        )
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    },
                    navigationIcon = {
                        IconButton(onClick = { vm.updateSearchActive(false) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cerrar búsqueda") }
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
                        IconButton(onClick = { vm.updateSearchActive(true) }) { Icon(Icons.Default.Search, "Buscar") }
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
                        label = { Text("Todos") }
                    )
                }
                item {
                    FilterChip(
                        selected = filterState.period == ExpensePeriod.THIS_MONTH,
                        onClick = { vm.setPeriod(ExpensePeriod.THIS_MONTH) },
                        label = { Text("Este mes") }
                    )
                }
                item {
                    FilterChip(
                        selected = filterState.period == ExpensePeriod.LAST_MONTH,
                        onClick = { vm.setPeriod(ExpensePeriod.LAST_MONTH) },
                        label = { Text("Mes pasado") }
                    )
                }
                item {
                    FilterChip(
                        selected = filterState.period == ExpensePeriod.CUSTOM,
                        onClick = { showMonthPicker = true },
                        label = {
                            val text = filterState.customMonth?.let { it.format(DateTimeFormatter.ofPattern("MMM yy", Locale("es", "MX"))) } ?: "Elegir mes"
                            Text(text)
                        }
                    )
                }
                item {
                    FilterChip(
                        selected = filterState.categoryIds.isNotEmpty(),
                        onClick = { showCategorySheet = true },
                        label = { Text(if (filterState.categoryIds.isEmpty()) "Categorías" else "Categorías (${filterState.categoryIds.size})") }
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
                EmptyState(
                    icon = Icons.Default.Receipt,
                    title = if (isFiltering) "Sin resultados" else stringResource(R.string.expenses_empty),
                    description = if (isFiltering) "No encontramos gastos que coincidan con los filtros." else "Registra tu primer gasto para comenzar.",
                    modifier = Modifier.weight(1f)
                )
                if (isFiltering) {
                    Button(onClick = { vm.clearFilters() }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 32.dp)) {
                        Text("Limpiar filtros")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(expenses, key = { it.id }) { expense ->
                        val isSelected = vm.selectedIds.contains(expense.id)
                        ExpenseRow(
                            expense = expense,
                            isSelectionMode = vm.isSelectionMode,
                            isSelected = isSelected,
                            onClick = { if (vm.isSelectionMode) vm.toggleSelection(expense.id) else vm.selectExpense(expense) },
                            onLongClick = { vm.enterSelectionMode(expense.id) }
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
    onLongClick: () -> Unit
) {
    val semanticsModifier = if (isSelectionMode) Modifier.semantics { contentDescription = if (isSelected) "Seleccionado" else "No seleccionado" } else Modifier
    val displayTitle = if (expense.customCategoryName != null) "Otro · ${expense.customCategoryName}" else expense.description ?: stringResource(R.string.expenses_default_name)

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).then(semanticsModifier),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() }, modifier = Modifier.padding(end = 12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = displayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(text = formatDate(expense.dateEpochDay), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text = formatMoneyCents(expense.amountCents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}