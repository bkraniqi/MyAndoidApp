package com.moneta.finance.domain

data class CsvRow(
    val date: String,
    val type: TransactionType,
    val category: String,
    val description: String,
    val amountMinor: Long,
    val currency: String,
    val paymentMethod: String,
    val notes: String,
)

object CsvWriter {
    val header = listOf("Date", "Type", "Category", "Description", "Amount", "Signed amount", "Currency", "Payment method", "Notes")

    fun build(rows: List<CsvRow>): String = buildString {
        append('\uFEFF') // UTF-8 BOM so spreadsheet apps detect the encoding
        appendLine(header.joinToString(",") { escape(it) })
        for (r in rows) {
            val signed = if (r.type == TransactionType.INCOME) r.amountMinor else -r.amountMinor
            val fields = listOf(
                r.date,
                if (r.type == TransactionType.INCOME) "Income" else "Expense",
                r.category,
                r.description,
                Money.toPlainString(r.amountMinor),
                Money.toPlainString(signed),
                r.currency,
                r.paymentMethod,
                r.notes,
            )
            appendLine(fields.joinToString(",") { escape(it) })
        }
    }

    /** RFC 4180 quoting plus protection against spreadsheet formula injection in text fields. */
    fun escape(value: String): String {
        val guarded = if (value.isNotEmpty() && value[0] in "=+@\t\r" || (value.startsWith("-") && value.toBigDecimalOrNull() == null)) {
            "'$value"
        } else value
        val needsQuotes = guarded.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || guarded != guarded.trim()
        return if (needsQuotes) "\"" + guarded.replace("\"", "\"\"") + "\"" else guarded
    }
}
