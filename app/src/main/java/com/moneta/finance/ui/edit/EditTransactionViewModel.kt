package com.moneta.finance.ui.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.finance.data.FinanceRepository
import com.moneta.finance.data.db.CategoryEntity
import com.moneta.finance.data.db.RecurringRuleEntity
import com.moneta.finance.domain.Frequency
import com.moneta.finance.domain.Money
import com.moneta.finance.domain.PaymentMethod
import com.moneta.finance.domain.TransactionType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TransactionForm(
    val type: TransactionType,
    val amountText: String = "",
    val categoryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val description: String = "",
    val paymentMethod: PaymentMethod = defaultMethod(type),
    val notes: String = "",
    val repeat: Frequency? = null,
    val amountError: String? = null,
    val categoryError: String? = null,
    val generalError: String? = null,
    val isRecurringOccurrence: Boolean = false,
) {
    companion object {
        fun defaultMethod(type: TransactionType) =
            if (type == TransactionType.INCOME) PaymentMethod.BANK_TRANSFER else PaymentMethod.DEBIT_CARD
    }
}

class EditTransactionViewModel(
    private val repository: FinanceRepository,
    initialType: TransactionType,
    val transactionId: Long?,
) : ViewModel() {

    var form by mutableStateOf(TransactionForm(type = initialType))
        private set
    var loaded by mutableStateOf(transactionId == null)
        private set
    var finished by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set

    val isEditing: Boolean get() = transactionId != null

    val categories: StateFlow<List<CategoryEntity>> =
        repository.categoriesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (transactionId != null) {
            viewModelScope.launch {
                val tx = repository.getTransaction(transactionId)
                if (tx == null) {
                    finished = true
                } else {
                    form = TransactionForm(
                        type = tx.type,
                        amountText = Money.toInputString(tx.amountMinor),
                        categoryId = tx.categoryId,
                        date = LocalDate.ofEpochDay(tx.epochDay),
                        description = tx.description,
                        paymentMethod = tx.paymentMethod,
                        notes = tx.notes,
                        isRecurringOccurrence = tx.recurringRuleId != null,
                    )
                }
                loaded = true
            }
        }
    }

    fun setType(type: TransactionType) {
        if (type == form.type) return
        form = form.copy(
            type = type,
            categoryId = null,
            paymentMethod = if (form.paymentMethod == TransactionForm.defaultMethod(form.type)) TransactionForm.defaultMethod(type) else form.paymentMethod,
            categoryError = null,
        )
    }

    fun setAmount(text: String) {
        Money.sanitizeInput(text)?.let { form = form.copy(amountText = it, amountError = null) }
    }

    fun setCategory(id: Long) { form = form.copy(categoryId = id, categoryError = null) }
    fun setDate(date: LocalDate) { form = form.copy(date = date) }
    fun setDescription(text: String) { form = form.copy(description = text.take(120)) }
    fun setPaymentMethod(method: PaymentMethod) { form = form.copy(paymentMethod = method) }
    fun setNotes(text: String) { form = form.copy(notes = text.take(1000)) }
    fun setRepeat(frequency: Frequency?) { form = form.copy(repeat = frequency) }

    fun save() {
        if (saving) return
        val f = form
        val amount = when (val r = Money.parse(f.amountText)) {
            is Money.ParseResult.Success -> r.minor
            is Money.ParseResult.Failure -> { form = f.copy(amountError = r.message); null }
        }
        val categoryId = f.categoryId?.takeIf { id -> categories.value.any { it.id == id && it.type == f.type } }
        if (categoryId == null) form = form.copy(categoryError = "Choose a category")
        if (amount == null || categoryId == null) return

        saving = true
        viewModelScope.launch {
            try {
                if (f.repeat != null && transactionId == null) {
                    // The rule records the first occurrence itself (if due), so nothing is entered twice.
                    repository.saveRecurringRule(
                        RecurringRuleEntity(
                            type = f.type, amountMinor = amount, categoryId = categoryId,
                            description = f.description, paymentMethod = f.paymentMethod, notes = f.notes,
                            frequency = f.repeat, startEpochDay = f.date.toEpochDay(), endEpochDay = null,
                            createdAt = 0L,
                        )
                    )
                } else {
                    repository.saveTransaction(
                        id = transactionId, type = f.type, amountMinor = amount, categoryId = categoryId,
                        date = f.date, description = f.description, paymentMethod = f.paymentMethod, notes = f.notes,
                    )
                }
                finished = true
            } catch (e: Exception) {
                form = form.copy(generalError = e.message ?: "Could not save the transaction")
            } finally {
                saving = false
            }
        }
    }

    fun delete() {
        val id = transactionId ?: return
        viewModelScope.launch {
            repository.deleteTransaction(id)
            finished = true
        }
    }
}
