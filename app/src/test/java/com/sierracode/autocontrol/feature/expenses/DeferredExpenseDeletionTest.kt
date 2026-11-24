package com.sierracode.autocontrol.feature.expenses

import androidx.lifecycle.SavedStateHandle
import com.sierracode.autocontrol.data.local.dao.CategoryTotalRow
import com.sierracode.autocontrol.data.local.entity.*
import com.sierracode.autocontrol.data.repository.AttachmentRepository
import com.sierracode.autocontrol.data.repository.CategoryRepository
import com.sierracode.autocontrol.data.repository.ExpenseDeletionSnapshot
import com.sierracode.autocontrol.data.repository.ExpenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeferredExpenseDeletionTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeExpenseRepository : ExpenseRepository {
        val deletedIds = mutableListOf<String>()
        override fun observeByVehicle(id: String): Flow<List<ExpenseEntity>> = flowOf(emptyList())
        override fun observeSum(id: String, from: Long, to: Long): Flow<Long?> = flowOf(null)
        override fun observeByCategory(id: String, from: Long, to: Long): Flow<List<CategoryTotalRow>> = flowOf(emptyList())
        override fun observeOne(id: String): Flow<ExpenseEntity?> = flowOf(null)
        override fun observeMinOdometer(id: String): Flow<Long?> = flowOf(null)
        override suspend fun get(id: String): ExpenseEntity? = null
        override suspend fun getFuelDetail(id: String): FuelDetailEntity? = null
        override suspend fun getMaintenanceDetail(id: String): MaintenanceDetailEntity? = null
        override suspend fun saveWithDetails(expense: ExpenseEntity, fuel: FuelDetailEntity?, maint: MaintenanceDetailEntity?, attachment: AttachmentEntity?) {}
        override suspend fun delete(id: String) {}
        override suspend fun deleteMultiple(ids: List<String>) {
            deletedIds.addAll(ids)
        }
        override suspend fun deleteDeferred(id: String) {}
        override suspend fun captureForDeletion(id: String): ExpenseDeletionSnapshot? = null
        override suspend fun restoreFromSnapshot(snapshot: ExpenseDeletionSnapshot) {}
        override fun purgeAttachmentFile(uriString: String?) {}
    }

    private class FakeAttachmentRepository : AttachmentRepository {
        override fun observeByOwner(type: String, id: String): Flow<List<AttachmentEntity>> = flowOf(emptyList())
        override suspend fun getForOwner(type: String, id: String): AttachmentEntity? = null
        override suspend fun upsert(attachment: AttachmentEntity) {}
        override suspend fun deleteById(id: String) {}
        override suspend fun getAllOnce(): List<AttachmentEntity> = emptyList()
        override suspend fun deleteAll() {}
    }

    private class FakeCategoryRepository : CategoryRepository {
        override fun observeAll(): Flow<List<CategoryEntity>> = flowOf(emptyList())
        override suspend fun count(): Int = 0
        override suspend fun upsertAll(categories: List<CategoryEntity>) {}
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `seleccion y borrado diferido oculta items y permite deshacer sin tocar el repositorio`() = runTest {
        val fakeRepo = FakeExpenseRepository()
        val savedStateHandle = SavedStateHandle()
        val vm = ExpensesViewModel(fakeRepo, FakeAttachmentRepository(), FakeCategoryRepository(), savedStateHandle)

        // 1. Iniciar modo selección y seleccionar IDs
        vm.enterSelectionMode("exp_1")
        testDispatcher.scheduler.advanceUntilIdle()
        vm.toggleSelection("exp_2")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.isSelectionMode.value)
        assertEquals(setOf("exp_1", "exp_2"), vm.selectedIds.value)

        // 2. Iniciar borrado diferido: limpia la selección y devuelve los IDs para el Snackbar
        val idsToDelete = vm.startDeferredDelete()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("exp_1", "exp_2"), idsToDelete)
        assertFalse(vm.isSelectionMode.value)
        assertTrue(vm.selectedIds.value.isEmpty())

        // El repositorio de Room aún NO ha sido llamado
        assertTrue(fakeRepo.deletedIds.isEmpty())

        // 3. Usuario toca Deshacer: se cancela el borrado diferido
        vm.undoDeferredDelete(idsToDelete)
        testDispatcher.scheduler.advanceUntilIdle()

        // El repositorio sigue sin ser llamado
        assertTrue(fakeRepo.deletedIds.isEmpty())
    }

    @Test
    fun `confirmar borrado diferido llama a deleteMultiple en el repositorio`() = runTest {
        val fakeRepo = FakeExpenseRepository()
        val savedStateHandle = SavedStateHandle()
        val vm = ExpensesViewModel(fakeRepo, FakeAttachmentRepository(), FakeCategoryRepository(), savedStateHandle)

        vm.enterSelectionMode("exp_10")
        testDispatcher.scheduler.advanceUntilIdle()
        val idsToDelete = vm.startDeferredDelete()
        testDispatcher.scheduler.advanceUntilIdle()

        // Confirmar borrado (Snackbar expiró o fue descartado)
        vm.commitDeferredDelete(idsToDelete)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("exp_10"), fakeRepo.deletedIds)
    }

    @Test
    fun `enterSelectionMode actualiza isSelectionMode a true de forma reactiva sin reiniciar ViewModel`() = runTest {
        val fakeRepo = FakeExpenseRepository()
        val savedStateHandle = SavedStateHandle()
        val vm = ExpensesViewModel(fakeRepo, FakeAttachmentRepository(), FakeCategoryRepository(), savedStateHandle)

        assertFalse(vm.isSelectionMode.value)
        assertTrue(vm.selectedIds.value.isEmpty())

        vm.enterSelectionMode("exp_new")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.isSelectionMode.value)
        assertEquals(setOf("exp_new"), vm.selectedIds.value)

        vm.clearSelection()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.isSelectionMode.value)
        assertTrue(vm.selectedIds.value.isEmpty())
    }
}
