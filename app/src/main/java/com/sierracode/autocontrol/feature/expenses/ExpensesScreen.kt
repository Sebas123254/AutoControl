package com.sierracode.autocontrol.feature.expenses

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.sierracode.autocontrol.core.format.formatDate
import com.sierracode.autocontrol.core.format.formatMoneyCents
import com.sierracode.autocontrol.data.local.dao.AttachmentDao
import com.sierracode.autocontrol.data.local.entity.AttachmentEntity
import com.sierracode.autocontrol.data.local.entity.ExpenseEntity
import com.sierracode.autocontrol.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val expenseRepo: ExpenseRepository,
    private val attachmentDao: AttachmentDao
) : ViewModel() {
    private val vehicleIdFlow = MutableStateFlow<String?>(null)

    val expenses = vehicleIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else expenseRepo.observeByVehicle(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var selectedExpense by mutableStateOf<ExpenseEntity?>(null)
    var selectedAttachment by mutableStateOf<AttachmentEntity?>(null)

    fun loadVehicle(id: String) {
        vehicleIdFlow.value = id
    }

    fun selectExpense(expense: ExpenseEntity) {
        selectedExpense = expense
        viewModelScope.launch {
            selectedAttachment = attachmentDao.getForOwner("expense", expense.id)
        }
    }

    fun dismissDialog() {
        selectedExpense = null
        selectedAttachment = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    vehicleId: String,
    onBack: () -> Unit,
    vm: ExpensesViewModel = hiltViewModel()
) {
    LaunchedEffect(vehicleId) {
        vm.loadVehicle(vehicleId)
    }

    val expenses by vm.expenses.collectAsState()

    if (vm.selectedExpense != null) {
        val expense = vm.selectedExpense!!
        AlertDialog(
            onDismissRequest = { vm.dismissDialog() },
            title = { Text(expense.description ?: "Detalle del gasto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Monto: ${formatMoneyCents(expense.amountCents)}", fontWeight = FontWeight.Bold)
                    Text("Fecha: ${formatDate(expense.dateEpochDay)}")
                    if (expense.odometer != null) {
                        Text("Kilometraje: ${expense.odometer} km")
                    }

                    Spacer(Modifier.height(8.dp))

                    if (vm.selectedAttachment != null) {
                        Text("Recibo adjunto:", style = MaterialTheme.typography.labelMedium)
                        AsyncImage(
                            model = Uri.parse(vm.selectedAttachment!!.uri),
                            contentDescription = "Recibo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .padding(top = 8.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text("Sin recibo adjunto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.dismissDialog() }) { Text("Cerrar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial de Gastos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = "Mis Vehículos")
                    }
                }
            )
        }
    ) { padding ->
        if (expenses.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No hay gastos registrados aún.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(expenses, key = { it.id }) { expense ->
                    ExpenseRow(
                        expense = expense,
                        onClick = { vm.selectExpense(expense) }
                    )
                }
            }
        }
    }
}

@Composable
fun ExpenseRow(expense: ExpenseEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.description ?: "Gasto",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatDate(expense.dateEpochDay),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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