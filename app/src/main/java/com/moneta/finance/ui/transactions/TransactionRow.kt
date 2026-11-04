package com.moneta.finance.ui.transactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneta.finance.domain.TransactionType
import com.moneta.finance.ui.Dates
import com.moneta.finance.ui.TransactionItem
import com.moneta.finance.ui.components.AmountText
import com.moneta.finance.ui.components.CategoryBadge
import com.moneta.finance.ui.components.LocalMoneyFormatter
import com.moneta.finance.ui.theme.Moneta

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionRow(
    item: TransactionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val isIncome = item.type == TransactionType.INCOME
    val formatter = LocalMoneyFormatter.current
    val signed = if (isIncome) item.amountMinor else -item.amountMinor
    val kind = if (isIncome) "Income" else "Expense"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Delete")
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$kind ${formatter.format(item.amountMinor)}, ${item.title}, ${item.category?.name ?: ""}, ${Dates.medium(item.date)}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(item.category?.iconKey ?: "more", item.category?.color ?: 0xFF7D8A86.toInt())
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.isRecurring) {
                    Icon(
                        Icons.Filled.Autorenew, contentDescription = "Recurring",
                        modifier = Modifier.size(14.dp).padding(end = 2.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val meta = buildList {
                    if (item.tx.description.isNotBlank()) add(item.category?.name ?: "")
                    add(item.paymentMethod.label)
                    if (showDate) add(Dates.short(item.date))
                }.filter { it.isNotBlank() }.joinToString(" · ")
                Text(
                    meta, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        AmountText(
            signed,
            signed = true,
            style = MaterialTheme.typography.titleMedium,
            color = if (isIncome) Moneta.colors.income else MaterialTheme.colorScheme.onSurface,
        )
    }
}
