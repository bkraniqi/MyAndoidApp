package com.moneta.finance.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneta.finance.data.FinanceRepository
import com.moneta.finance.data.db.CategoryEntity
import com.moneta.finance.data.db.RecurringRuleEntity
import com.moneta.finance.domain.Frequency
import com.moneta.finance.domain.Money
import com.moneta.finance.domain.PaymentMethod
import com.moneta.finance.domain.TransactionType
import com.moneta.finance.ui.Dates
import com.moneta.finance.ui.LocalAppContainer
import com.moneta.finance.ui.components.AmountText
import com.moneta.finance.ui.components.CategoryBadge
import com.moneta.finance.ui.components.ConfirmDialog
import com.moneta.finance.ui.components.EmptyState
import com.moneta.finance.ui.components.GroupCard
import com.moneta.finance.ui.components.ScreenScaffold
import com.moneta.finance.ui.edit.AmountField
import com.moneta.finance.ui.edit.CategoryPicker
import com.moneta.finance.ui.edit.ChipRow
import com.moneta.finance.ui.edit.FieldLabel
import com.moneta.finance.ui.theme.Moneta
import com.moneta.finance.ui.transactions.utcDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class RuleItem(val rule: RecurringRuleEntity, val category: CategoryEntity?, val next: LocalDate?)

class RecurringViewModel(private val repository: FinanceRepository) : ViewModel() {
    val rules: StateFlow<List<RuleItem>?> = combine(repository.recurringFlow, repository.categoriesFlow) { rules, categories ->
        val byId = categories.associateBy { it.id }
        rules.map { RuleItem(it, byId[it.categoryId], repository.nextOccurrence(it)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(id: Long) = viewModelScope.launch { repository.deleteRecurringRule(id) }
}

@Composable
fun RecurringScreen(onBack: () -> Unit, onEdit: (Long?) -> Unit) {
    val container = LocalAppContainer.current
    val vm: RecurringViewModel = viewModel { RecurringViewModel(container.repository) }
    val rules by vm.rules.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<RuleItem?>(null) }

    ScreenScaffold(
        title = "Recurring",
        onBack = onBack,
        actions = { IconButton(onClick = { onEdit(null) }) { Icon(Icons.Filled.Add, contentDescription = "Add recurring transaction") } },
    ) {
        item(key = "intro") {
            Text(
                "Each occurrence is recorded automatically on its date, exactly once.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        val list = rules
        if (list != null && list.isEmpty()) {
            item(key = "empty") {
                EmptyState(Icons.Filled.Autorenew, "No recurring transactions", "Add your salary, rent, utilities or subscriptions once and Moneta records them for you.")
            }
        }
        if (!list.isNullOrEmpty()) {
            item(key = "list") {
                GroupCard {
                    list.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        RuleRow(item, onClick = { onEdit(item.rule.id) }, onDelete = { deleting = item })
                    }
                }
            }
        }
    }

    deleting?.let { item ->
        ConfirmDialog(
            title = "Delete recurring transaction?",
            text = "No further entries will be created. Transactions already recorded are kept.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(item.rule.id) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun RuleRow(item: RuleItem, onClick: () -> Unit, onDelete: () -> Unit) {
    val r = item.rule
    val isIncome = r.type == TransactionType.INCOME
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(item.category?.iconKey ?: "more", item.category?.color ?: 0xFF7D8A86.toInt())
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(r.description.ifBlank { item.category?.name ?: "Recurring" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = when {
                !r.active -> "Paused"
                item.next == null -> "Finished"
                else -> "Next ${Dates.medium(item.next)}"
            }
            Text("${r.frequency.label} · $status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AmountText(
            if (isIncome) r.amountMinor else -r.amountMinor,
            signed = true,
            style = MaterialTheme.typography.titleSmall,
            color = if (isIncome) Moneta.colors.income else MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete") }
    }
}

// ------------------------------------------------------------------ editor

data class RuleForm(
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val categoryId: Long? = null,
    val description: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.DEBIT_CARD,
    val notes: String = "",
    val frequency: Frequency = Frequency.MONTHLY,
    val start: LocalDate = LocalDate.now(),
    val end: LocalDate? = null,
    val active: Boolean = true,
    val locked: Boolean = false,
    val amountError: String? = null,
    val error: String? = null,
)

class RecurringEditViewModel(private val repository: FinanceRepository, private val ruleId: Long?) : ViewModel() {
    var form by mutableStateOf(RuleForm())
        private set
    var loaded by mutableStateOf(ruleId == null)
        private set
    var finished by mutableStateOf(false)
        private set
    private var existing: RecurringRuleEntity? = null
    val isEditing get() = ruleId != null

    val categories: StateFlow<List<CategoryEntity>> =
        repository.categoriesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (ruleId != null) viewModelScope.launch {
            val r = repository.getRecurringRule(ruleId)
            if (r == null) finished = true else {
                existing = r
                form = RuleForm(
                    type = r.type, amountText = Money.toInputString(r.amountMinor), categoryId = r.categoryId,
                    description = r.description, paymentMethod = r.paymentMethod, notes = r.notes,
                    frequency = r.frequency, start = LocalDate.ofEpochDay(r.startEpochDay),
                    end = r.endEpochDay?.let(LocalDate::ofEpochDay), active = r.active, locked = r.generatedCount > 0,
                )
            }
            loaded = true
        }
    }

    fun update(transform: (RuleForm) -> RuleForm) { form = transform(form).copy(error = null) }
    fun setAmount(text: String) { Money.sanitizeInput(text)?.let { t -> form = form.copy(amountText = t, amountError = null) } }

    fun save() {
        val f = form
        val amount = when (val r = Money.parse(f.amountText)) {
            is Money.ParseResult.Success -> r.minor
            is Money.ParseResult.Failure -> { form = f.copy(amountError = r.message); return }
        }
        val categoryId = f.categoryId?.takeIf { id -> categories.value.any { it.id == id && it.type == f.type } }
            ?: run { form = f.copy(error = "Choose a category"); return }
        if (f.end != null && f.end.isBefore(f.start)) { form = f.copy(error = "The end date must be after the start date"); return }
        viewModelScope.launch {
            runCatching {
                repository.saveRecurringRule(
                    RecurringRuleEntity(
                        id = existing?.id ?: 0L, type = f.type, amountMinor = amount, categoryId = categoryId,
                        description = f.description, paymentMethod = f.paymentMethod, notes = f.notes,
                        frequency = f.frequency, startEpochDay = f.start.toEpochDay(), endEpochDay = f.end?.toEpochDay(),
                        generatedCount = existing?.generatedCount ?: 0, active = f.active, createdAt = existing?.createdAt ?: 0L,
                    )
                )
            }.onSuccess { finished = true }
                .onFailure { form = form.copy(error = it.message ?: "Could not save") }
        }
    }
}

private enum class DateTarget { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringEditScreen(ruleId: Long?, onClose: () -> Unit, onManageCategories: (TransactionType) -> Unit) {
    val container = LocalAppContainer.current
    val vm: RecurringEditViewModel = viewModel { RecurringEditViewModel(container.repository, ruleId) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val f = vm.form
    var picking by remember { mutableStateOf<DateTarget?>(null) }
    LaunchedEffect(vm.finished) { if (vm.finished) onClose() }
    val accent = if (f.type == TransactionType.INCOME) Moneta.colors.income else Moneta.colors.expense

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEditing) "Edit recurring" else "New recurring") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding().padding(16.dp, 12.dp)) {
                Button(onClick = vm::save, enabled = vm.loaded, modifier = Modifier.fillMaxWidth().height(56.dp), shape = MaterialTheme.shapes.large) {
                    Text("Save", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(TransactionType.EXPENSE to "Expense", TransactionType.INCOME to "Income").forEachIndexed { i, (t, label) ->
                    SegmentedButton(
                        selected = f.type == t,
                        onClick = {
                            vm.update {
                                it.copy(
                                    type = t, categoryId = null,
                                    paymentMethod = if (t == TransactionType.INCOME) PaymentMethod.BANK_TRANSFER else PaymentMethod.DEBIT_CARD,
                                )
                            }
                        },
                        enabled = !f.locked || f.type == t,
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }
            AmountField(f.amountText, vm::setAmount, accent, f.amountError, autoFocus = false)

            FieldLabel("Category", trailing = {
                TextButton(onClick = { onManageCategories(f.type) }) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Manage")
                }
            })
            CategoryPicker(categories.filter { it.type == f.type }, f.categoryId) { id -> vm.update { it.copy(categoryId = id) } }

            FieldLabel("Repeats")
            if (f.locked) {
                Text(
                    "${f.frequency.label}, starting ${Dates.medium(f.start)}. The schedule can't change after entries were recorded; delete this rule and create a new one instead.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            } else {
                ChipRow(Frequency.entries, f.frequency, { it.label }) { fr -> vm.update { it.copy(frequency = fr) } }
                FieldLabel("First date")
                FilterChip(selected = true, onClick = { picking = DateTarget.START }, label = { Text(Dates.medium(f.start)) })
            }
            FieldLabel("End date")
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = f.end == null, onClick = { vm.update { it.copy(end = null) } }, label = { Text("Never") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = f.end != null, onClick = { picking = DateTarget.END }, label = { Text(f.end?.let { Dates.medium(it) } ?: "Pick end date") })
            }

            FieldLabel("Payment method")
            ChipRow(PaymentMethod.entries, f.paymentMethod, { it.label }) { m -> vm.update { it.copy(paymentMethod = m) } }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = f.description, onValueChange = { v -> vm.update { it.copy(description = v.take(120)) } },
                label = { Text("Description") }, placeholder = { Text("e.g. Rent, Netflix, Salary") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = f.notes, onValueChange = { v -> vm.update { it.copy(notes = v.take(1000)) } },
                label = { Text("Notes (optional)") }, minLines = 2, maxLines = 4,
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            )
            if (vm.isEditing) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Active", style = MaterialTheme.typography.bodyLarge)
                        Text("Paused rules record nothing until resumed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = f.active, onCheckedChange = { a -> vm.update { it.copy(active = a) } })
                }
            }
            f.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    picking?.let { target ->
        val initial = if (target == DateTarget.START) f.start else (f.end ?: f.start)
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = utcDate(ms)
                        vm.update { if (target == DateTarget.START) it.copy(start = d) else it.copy(end = d) }
                    }
                    picking = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

