package com.moneta.finance.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneta.finance.data.AppSettings
import com.moneta.finance.data.BackupFormatException
import com.moneta.finance.data.FinanceRepository
import com.moneta.finance.data.ReportExporter
import com.moneta.finance.domain.DateRange
import com.moneta.finance.domain.MoneyFormatter
import com.moneta.finance.domain.ThemeMode
import com.moneta.finance.security.AppLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

enum class ExportRange(val label: String) {
    ALL("All time"), THIS_MONTH("This month"), LAST_MONTH("Last month"), THIS_YEAR("This year"), LAST_YEAR("Last year");

    fun range(today: LocalDate): DateRange? = when (this) {
        ALL -> null
        THIS_MONTH -> DateRange.month(YearMonth.from(today))
        LAST_MONTH -> DateRange.month(YearMonth.from(today).minusMonths(1))
        THIS_YEAR -> DateRange.year(today.year)
        LAST_YEAR -> DateRange.year(today.year - 1)
    }
}

enum class ExportFormat { CSV, PDF }

class SettingsViewModel(
    private val appContext: Context,
    private val repository: FinanceRepository,
    val lock: AppLockManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    var pendingExportRange: ExportRange = ExportRange.ALL

    fun consumeMessage() { _message.value = null }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode) }
    fun setCurrency(code: String) = viewModelScope.launch { repository.setCurrency(code) }
    fun setOpeningBalance(minor: Long) = viewModelScope.launch { repository.setOpeningBalance(minor) }
    fun setBudgetAlerts(enabled: Boolean) = viewModelScope.launch { repository.setBudgetAlerts(enabled) }

    fun setPin(pin: String) = viewModelScope.launch {
        lock.setPin(pin)
        lock.unlock()
        _message.value = "App lock enabled"
    }

    fun disableLock() {
        lock.disable()
        _message.value = "App lock turned off"
    }

    fun export(uri: Uri, format: ExportFormat) = viewModelScope.launch {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val range = pendingExportRange.range(LocalDate.now())
                val transactions = repository.transactionsInRange(range)
                val categories = repository.allCategories().associateBy { it.id }
                val settings = repository.settings()
                val stream = appContext.contentResolver.openOutputStream(uri, "wt") ?: error("Cannot open file")
                stream.use { out ->
                    when (format) {
                        ExportFormat.CSV -> out.write(ReportExporter.csv(transactions, categories, settings.currencyCode).toByteArray(Charsets.UTF_8))
                        ExportFormat.PDF -> ReportExporter.pdf(
                            out = out,
                            title = "Moneta report",
                            range = range,
                            transactions = transactions,
                            categories = categories,
                            openingBalanceMinor = settings.openingBalanceMinor,
                            formatter = MoneyFormatter(settings.currencyCode),
                        )
                    }
                }
                transactions.size
            }
        }
        _message.value = result.fold(
            onSuccess = { "Exported $it transactions" },
            onFailure = { "Export failed: ${it.message}" },
        )
    }

    fun backup(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            val json = repository.exportBackup()
            withContext(Dispatchers.IO) {
                appContext.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    ?: error("Cannot open file")
            }
        }
        _message.value = result.fold({ "Backup saved" }, { "Backup failed: ${it.message}" })
    }

    fun restore(uri: Uri) = viewModelScope.launch {
        val result = runCatching {
            val json = withContext(Dispatchers.IO) {
                appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: error("Cannot open file")
            }
            repository.restoreBackup(json)
        }
        _message.value = result.fold(
            onSuccess = { "Restored ${it.transactions.size} transactions" },
            onFailure = { if (it is BackupFormatException) it.message else "Restore failed: ${it.message}" },
        )
    }
}
