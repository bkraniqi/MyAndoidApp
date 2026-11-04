package com.moneta.finance.data

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.moneta.finance.data.db.CategoryEntity
import com.moneta.finance.data.db.TransactionEntity
import com.moneta.finance.domain.CsvRow
import com.moneta.finance.domain.CsvWriter
import com.moneta.finance.domain.DateRange
import com.moneta.finance.domain.FinanceCalculator
import com.moneta.finance.domain.MoneyFormatter
import com.moneta.finance.domain.TransactionType
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Builds CSV and PDF reports from stored transactions. */
object ReportExporter {
    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE

    fun csv(transactions: List<TransactionEntity>, categories: Map<Long, CategoryEntity>, currency: String): String =
        CsvWriter.build(
            transactions.sortedWith(compareBy({ it.epochDay }, { it.id })).map {
                CsvRow(
                    date = LocalDate.ofEpochDay(it.epochDay).format(isoDate),
                    type = it.type,
                    category = categories[it.categoryId]?.name ?: "",
                    description = it.description,
                    amountMinor = it.amountMinor,
                    currency = currency,
                    paymentMethod = it.paymentMethod.label,
                    notes = it.notes,
                )
            }
        )

    fun pdf(
        out: OutputStream,
        title: String,
        range: DateRange?,
        transactions: List<TransactionEntity>,
        categories: Map<Long, CategoryEntity>,
        openingBalanceMinor: Long,
        formatter: MoneyFormatter,
    ) {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val doc = PdfDocument()
        val emerald = Color.rgb(11, 107, 79)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = Color.rgb(30, 34, 32) }
        val mutedPaint = Paint(textPaint).apply { color = Color.rgb(110, 118, 114) }
        val boldPaint = Paint(textPaint).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f; color = emerald; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val incomePaint = Paint(boldPaint).apply { color = Color.rgb(20, 135, 90); textAlign = Paint.Align.RIGHT }
        val expensePaint = Paint(boldPaint).apply { color = Color.rgb(196, 64, 60); textAlign = Paint.Align.RIGHT }
        val linePaint = Paint().apply { color = Color.rgb(225, 229, 227); strokeWidth = 0.8f }
        val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

        val sorted = transactions.sortedWith(compareBy({ it.epochDay }, { it.id }))
        val totals = FinanceCalculator.totals(sorted)

        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun finishPage() {
            page?.let { p ->
                p.canvas.drawText("Page $pageNumber", pageWidth - margin, pageHeight - 20f, Paint(mutedPaint).apply { textAlign = Paint.Align.RIGHT })
                doc.finishPage(p)
            }
        }

        fun drawTableHeader() {
            val c = page!!.canvas
            c.drawText("Date", margin, y, boldPaint)
            c.drawText("Description", margin + 75f, y, boldPaint)
            c.drawText("Category", margin + 250f, y, boldPaint)
            c.drawText("Method", margin + 365f, y, boldPaint)
            c.drawText("Amount", pageWidth - margin, y, Paint(boldPaint).apply { textAlign = Paint.Align.RIGHT })
            y += 6f
            c.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 14f
        }

        fun newPage() {
            finishPage()
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            y = margin + 10f
            if (pageNumber > 1) drawTableHeader()
        }

        fun ellipsize(text: String, paint: Paint, width: Float): String {
            if (paint.measureText(text) <= width) return text
            var end = text.length
            while (end > 0 && paint.measureText(text.substring(0, end) + "…") > width) end--
            return text.substring(0, end) + "…"
        }

        newPage()
        val canvas = page!!.canvas
        canvas.drawText(title, margin, y + 12f, titlePaint)
        y += 34f
        val period = if (range == null) "All transactions" else "${range.start.format(dateFmt)} – ${range.endInclusive.format(dateFmt)}"
        canvas.drawText(period, margin, y, mutedPaint)
        y += 14f
        canvas.drawText("Generated ${LocalDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))}", margin, y, mutedPaint)
        y += 24f

        val summary = buildList {
            add("Total income" to formatter.format(totals.incomeMinor))
            add("Total expenses" to formatter.format(totals.expenseMinor))
            add("Net" to formatter.formatSigned(totals.netMinor))
            if (range == null) add("Balance (incl. opening ${formatter.format(openingBalanceMinor)})" to formatter.format(Math.addExact(openingBalanceMinor, totals.netMinor)))
            add("Transactions" to sorted.size.toString())
        }
        for ((label, value) in summary) {
            canvas.drawText(label, margin, y, textPaint)
            canvas.drawText(value, pageWidth - margin, y, Paint(boldPaint).apply { textAlign = Paint.Align.RIGHT })
            y += 15f
        }
        y += 14f
        drawTableHeader()

        for (t in sorted) {
            if (y > pageHeight - 50f) newPage()
            val c = page!!.canvas
            val category = categories[t.categoryId]?.name ?: ""
            c.drawText(LocalDate.ofEpochDay(t.epochDay).format(dateFmt), margin, y, textPaint)
            c.drawText(ellipsize(t.description.ifEmpty { category }, textPaint, 170f), margin + 75f, y, textPaint)
            c.drawText(ellipsize(category, mutedPaint, 110f), margin + 250f, y, mutedPaint)
            c.drawText(ellipsize(t.paymentMethod.label, mutedPaint, 80f), margin + 365f, y, mutedPaint)
            val amount = if (t.type == TransactionType.INCOME) formatter.formatSigned(t.amountMinor) else formatter.formatSigned(-t.amountMinor)
            c.drawText(amount, pageWidth - margin, y, if (t.type == TransactionType.INCOME) incomePaint else expensePaint)
            y += 6f
            c.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 13f
        }
        if (sorted.isEmpty()) page!!.canvas.drawText("No transactions in this period.", margin, y, mutedPaint)
        finishPage()
        try {
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }
}
