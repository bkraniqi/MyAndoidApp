package com.moneta.finance.ui.edit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneta.finance.data.db.CategoryEntity
import com.moneta.finance.domain.Frequency
import com.moneta.finance.domain.PaymentMethod
import com.moneta.finance.domain.TransactionType
import com.moneta.finance.ui.Dates
import com.moneta.finance.ui.LocalAppContainer
import com.moneta.finance.ui.components.CategoryBadge
import com.moneta.finance.ui.components.ConfirmDialog
import com.moneta.finance.ui.components.LocalMoneyFormatter
import com.moneta.finance.ui.theme.Moneta
import com.moneta.finance.ui.theme.money
import com.moneta.finance.ui.transactions.utcDate
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionScreen(
    initialType: TransactionType,
    transactionId: Long?,
    onClose: () -> Unit,
    onManageCategories: (TransactionType) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: EditTransactionViewModel = viewModel { EditTransactionViewModel(container.repository, initialType, transactionId) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val form = vm.form
    var confirmDelete by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(vm.finished) { if (vm.finished) onClose() }

    val accent = if (form.type == TransactionType.INCOME) Moneta.colors.income else Moneta.colors.expense

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isEditing) "Edit transaction" else "New transaction") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") } },
                actions = {
                    if (vm.isEditing) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete transaction")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = vm::save,
                    enabled = vm.loaded && !vm.saving,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        when {
                            vm.isEditing -> "Save changes"
                            form.type == TransactionType.INCOME -> "Add income"
                            else -> "Add expense"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(TransactionType.EXPENSE to "Expense", TransactionType.INCOME to "Income").forEachIndexed { i, (t, label) ->
                    SegmentedButton(
                        selected = form.type == t,
                        onClick = { vm.setType(t) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        enabled = !form.isRecurringOccurrence || form.type == t,
                    ) { Text(label) }
                }
            }

            AmountField(
                text = form.amountText,
                onChange = vm::setAmount,
                accent = accent,
                error = form.amountError,
                autoFocus = !vm.isEditing,
            )

            FieldLabel("Category", trailing = {
                TextButton(onClick = { onManageCategories(form.type) }) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Manage")
                }
            })
            CategoryPicker(
                categories = categories.filter { it.type == form.type },
                selectedId = form.categoryId,
                onSelect = vm::setCategory,
            )
            form.categoryError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
            }

            FieldLabel("Date")
            DateRow(date = form.date, onSelect = vm::setDate, onPick = { showDatePicker = true })

            FieldLabel("Payment method")
            ChipRow(PaymentMethod.entries, selected = form.paymentMethod, label = { it.label }, onSelect = vm::setPaymentMethod)

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = form.description,
                onValueChange = vm::setDescription,
                label = { Text(if (form.type == TransactionType.INCOME) "Description (optional)" else "Description") },
                placeholder = { Text(if (form.type == TransactionType.INCOME) "e.g. November salary" else "e.g. Weekly groceries") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = form.notes,
                onValueChange = vm::setNotes,
                label = { Text("Notes (optional)") },
                minLines = 2,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.save() }),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )

            if (!vm.isEditing) {
                FieldLabel("Repeat")
                ChipRow(
                    listOf<Frequency?>(null) + Frequency.entries,
                    selected = form.repeat,
                    label = { it?.label ?: "Never" },
                    onSelect = vm::setRepeat,
                )
                AnimatedVisibility(form.repeat != null) {
                    Row(Modifier.padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Recorded every ${form.repeat?.adjective} from ${Dates.medium(form.date)}. Manage it in Settings › Recurring.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (form.isRecurringOccurrence) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "This entry was created by a recurring transaction. Changes apply to this entry only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            form.generalError?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = form.date.toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.setDate(utcDate(it)) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete transaction?",
            text = "This transaction will be permanently removed and all balances and reports updated.",
            confirmLabel = "Delete",
            onConfirm = vm::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun AmountField(text: String, onChange: (String) -> Unit, accent: Color, error: String?, autoFocus: Boolean) {
    val formatter = LocalMoneyFormatter.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focus.requestFocus() } }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(formatter.symbol, style = MaterialTheme.typography.displaySmall, color = accent.copy(alpha = 0.8f))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = text,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.displayMedium.money().copy(
                    color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Start, fontSize = 48.sp,
                ),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier
                    .widthIn(min = 40.dp, max = 280.dp)
                    .focusRequester(focus)
                    .semantics { contentDescription = "Amount" },
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) {
                            Text("0.00", style = MaterialTheme.typography.displayMedium.money().copy(fontSize = 48.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        }
                        inner()
                    }
                },
            )
        }
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun FieldLabel(text: String, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPicker(categories: List<CategoryEntity>, selectedId: Long?, onSelect: (Long) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { c ->
            val selected = c.id == selectedId
            val color = Color(c.color)
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer)
                    .border(
                        BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant),
                        RoundedCornerShape(50),
                    )
                    .clickable { onSelect(c.id) }
                    .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp)
                    .semantics { contentDescription = c.name + if (selected) ", selected" else "" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryBadge(c.iconKey, c.color, size = 30.dp)
                Spacer(Modifier.width(8.dp))
                Text(c.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DateRow(date: LocalDate, onSelect: (LocalDate) -> Unit, onPick: () -> Unit) {
    val today = LocalDate.now()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = date == today, onClick = { onSelect(today) }, label = { Text("Today") })
        FilterChip(selected = date == today.minusDays(1), onClick = { onSelect(today.minusDays(1)) }, label = { Text("Yesterday") })
        val other = date != today && date != today.minusDays(1)
        FilterChip(
            selected = other,
            onClick = onPick,
            label = { Text(if (other) Dates.medium(date) else "Pick date") },
            leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
}
