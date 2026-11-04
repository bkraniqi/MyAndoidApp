package com.moneta.finance.ui.categories

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneta.finance.data.FinanceRepository
import com.moneta.finance.data.db.CategoryEntity
import com.moneta.finance.domain.TransactionType
import com.moneta.finance.ui.LocalAppContainer
import com.moneta.finance.ui.components.CategoryBadge
import com.moneta.finance.ui.components.CategoryColors
import com.moneta.finance.ui.components.CategoryIcons
import com.moneta.finance.ui.components.ConfirmDialog
import com.moneta.finance.ui.components.GroupCard
import com.moneta.finance.ui.components.ScreenScaffold
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(private val repository: FinanceRepository) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> =
        repository.categoriesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var error by mutableStateOf<String?>(null)

    fun save(category: CategoryEntity) = viewModelScope.launch {
        error = runCatching { repository.saveCategory(category) }.exceptionOrNull()?.message
    }

    fun delete(category: CategoryEntity) = viewModelScope.launch {
        error = runCatching { repository.deleteCategory(category.id) }.exceptionOrNull()?.message
    }

    suspend fun usage(category: CategoryEntity): Int = repository.categoryUsage(category.id)
}

private data class DeleteRequest(val category: CategoryEntity, val usage: Int)

@Composable
fun CategoriesScreen(initialType: TransactionType, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: CategoriesViewModel = viewModel { CategoriesViewModel(container.repository) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    var type by rememberSaveable { mutableStateOf(initialType) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var deleting by remember { mutableStateOf<DeleteRequest?>(null) }
    val scope = rememberCoroutineScope()

    ScreenScaffold(
        title = "Categories",
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                editing = CategoryEntity(name = "", type = type, iconKey = "more", color = CategoryColors.palette.first())
            }) { Icon(Icons.Filled.Add, contentDescription = "Add category") }
        },
    ) {
        item(key = "type") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(TransactionType.EXPENSE to "Expenses", TransactionType.INCOME to "Income").forEachIndexed { i, (t, label) ->
                    SegmentedButton(selected = type == t, onClick = { type = t }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                }
            }
        }
        vm.error?.let { message ->
            item(key = "error") { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp)) }
        }
        item(key = "list") {
            val list = categories.filter { it.type == type }
            GroupCard {
                list.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { editing = c }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryBadge(c.iconKey, c.color)
                        Spacer(Modifier.width(14.dp))
                        Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (c.isSystem) {
                            Icon(Icons.Filled.Lock, contentDescription = "Default category, cannot be deleted",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp).padding(end = 2.dp))
                        } else {
                            IconButton(onClick = {
                                scope.launch { deleting = DeleteRequest(c, vm.usage(c)) }
                            }) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete ${c.name}") }
                        }
                    }
                }
            }
        }
        item(key = "hint") {
            Text(
                "Deleting a category moves its transactions to \"Other\", so no amount is ever lost.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    editing?.let { c ->
        CategoryEditorDialog(
            initial = c,
            onSave = { vm.save(it); editing = null },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { req ->
        val other = if (req.category.type == TransactionType.EXPENSE) "Other" else "Other income"
        ConfirmDialog(
            title = "Delete \"${req.category.name}\"?",
            text = if (req.usage == 0) "This category has no transactions." else
                "${req.usage} transaction${if (req.usage == 1) "" else "s"} will be moved to \"$other\". Any budget for this category is removed.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(req.category) },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorDialog(initial: CategoryEntity, onSave: (CategoryEntity) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var icon by remember { mutableStateOf(initial.iconKey) }
    var color by remember { mutableStateOf(initial.color) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New category" else "Edit category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(icon, color, size = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40); error = null },
                        label = { Text("Name") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("Colour", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryColors.palette.forEach { c ->
                        val selected = c == color
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { color = c }
                                .semantics { contentDescription = if (selected) "Selected colour" else "Colour" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Icon", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryIcons.all.forEach { (key, vector) ->
                        val selected = key == icon
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (selected) Color(color).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .then(if (selected) Modifier.border(2.dp, Color(color), CircleShape) else Modifier)
                                .clickable { icon = key }
                                .semantics { contentDescription = "Icon $key" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(vector, contentDescription = null, tint = if (selected) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) error = "Enter a name" else onSave(initial.copy(name = name.trim(), iconKey = icon, color = color))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
