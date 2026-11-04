package com.moneta.finance.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moneta.finance.BuildConfig
import com.moneta.finance.domain.Currencies
import com.moneta.finance.domain.ThemeMode
import com.moneta.finance.ui.LocalAppContainer
import com.moneta.finance.ui.components.AmountInputDialog
import com.moneta.finance.ui.components.ConfirmDialog
import com.moneta.finance.ui.components.GroupCard
import com.moneta.finance.ui.components.LocalMoneyFormatter
import com.moneta.finance.ui.components.ScreenScaffold
import com.moneta.finance.ui.components.SectionTitle
import com.moneta.finance.ui.components.SettingsRow
import java.time.LocalDate

private enum class SettingsDialog { THEME, CURRENCY, OPENING, PIN, DISABLE_LOCK, EXPORT_CSV, EXPORT_PDF, RESTORE_CONFIRM }

@Composable
fun SettingsScreen(onOpenCategories: () -> Unit, onOpenRecurring: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val vm: SettingsViewModel = viewModel {
        SettingsViewModel(context.applicationContext, container.repository, container.appLock)
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lock by vm.lock.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val formatter = LocalMoneyFormatter.current
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.export(it, ExportFormat.CSV) }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { vm.export(it, ExportFormat.PDF) }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.backup(it) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { restoreUri = uri; dialog = SettingsDialog.RESTORE_CONFIRM }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Box(Modifier.fillMaxSize()) {
        ScreenScaffold(title = "Settings") {
            item(key = "general") { SectionTitle("General") }
            item(key = "generalCard") {
                GroupCard {
                    SettingsRow(
                        "Theme", subtitle = settings.themeMode.label(), icon = Icons.Filled.DarkMode,
                        onClick = { dialog = SettingsDialog.THEME },
                    )
                    Divider()
                    SettingsRow(
                        "Currency", subtitle = "${settings.currencyCode} · ${Currencies.displayName(settings.currencyCode)}",
                        icon = Icons.Filled.CurrencyExchange, onClick = { dialog = SettingsDialog.CURRENCY },
                    )
                    Divider()
                    SettingsRow(
                        "Opening balance", subtitle = formatter.format(settings.openingBalanceMinor),
                        icon = Icons.Filled.AccountBalanceWallet, onClick = { dialog = SettingsDialog.OPENING },
                    )
                }
            }
            item(key = "organize") { SectionTitle("Organize") }
            item(key = "organizeCard") {
                GroupCard {
                    SettingsRow("Categories", subtitle = "Create, edit and delete categories", icon = Icons.Filled.Category,
                        onClick = onOpenCategories, trailing = { Chevron() })
                    Divider()
                    SettingsRow("Recurring transactions", subtitle = "Salary, rent, subscriptions…", icon = Icons.Filled.Autorenew,
                        onClick = onOpenRecurring, trailing = { Chevron() })
                    Divider()
                    SettingsRow(
                        "Budget alerts", subtitle = "Notify at 80 % and when a budget is exceeded", icon = Icons.Filled.Notifications,
                        onClick = { toggleAlerts(!settings.budgetAlertsEnabled, vm, { p -> notificationPermission.launch(p) }) },
                        trailing = {
                            Switch(checked = settings.budgetAlertsEnabled, onCheckedChange = { toggleAlerts(it, vm, { p -> notificationPermission.launch(p) }) })
                        },
                    )
                }
            }
            item(key = "security") { SectionTitle("Security") }
            item(key = "securityCard") {
                GroupCard {
                    SettingsRow(
                        "App lock", subtitle = if (lock.pinEnabled) "PIN required to open Moneta" else "Off",
                        icon = Icons.Filled.Lock,
                        onClick = { dialog = if (lock.pinEnabled) SettingsDialog.DISABLE_LOCK else SettingsDialog.PIN },
                        trailing = {
                            Switch(checked = lock.pinEnabled, onCheckedChange = { on ->
                                dialog = if (on) SettingsDialog.PIN else SettingsDialog.DISABLE_LOCK
                            })
                        },
                    )
                    if (lock.pinEnabled) {
                        Divider()
                        SettingsRow("Change PIN", icon = Icons.Filled.Password, onClick = { dialog = SettingsDialog.PIN })
                        Divider()
                        SettingsRow(
                            "Unlock with biometrics",
                            subtitle = if (biometricAvailable) "Fingerprint or face recognition" else "No biometrics enrolled on this device",
                            icon = Icons.Filled.Fingerprint,
                            onClick = if (biometricAvailable) ({ vm.lock.setBiometricEnabled(!lock.biometricEnabled) }) else null,
                            trailing = {
                                Switch(
                                    checked = lock.biometricEnabled && biometricAvailable,
                                    enabled = biometricAvailable,
                                    onCheckedChange = { vm.lock.setBiometricEnabled(it) },
                                )
                            },
                        )
                    }
                }
            }
            item(key = "data") { SectionTitle("Data") }
            item(key = "dataCard") {
                GroupCard {
                    SettingsRow("Export to CSV", subtitle = "Spreadsheet of your transactions", icon = Icons.Filled.TableChart,
                        onClick = { dialog = SettingsDialog.EXPORT_CSV })
                    Divider()
                    SettingsRow("Export to PDF", subtitle = "Printable report with totals", icon = Icons.Filled.PictureAsPdf,
                        onClick = { dialog = SettingsDialog.EXPORT_PDF })
                    Divider()
                    SettingsRow("Back up", subtitle = "Save all data to a file you choose", icon = Icons.Filled.Backup,
                        onClick = { backupLauncher.launch("moneta-backup-${LocalDate.now()}.json") })
                    Divider()
                    SettingsRow("Restore", subtitle = "Replace all data with a backup file", icon = Icons.Filled.Restore,
                        onClick = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) })
                }
            }
            item(key = "about") { SectionTitle("About") }
            item(key = "aboutCard") {
                GroupCard {
                    SettingsRow(
                        "Private by design",
                        subtitle = "Moneta works fully offline and has no internet permission. Your data only leaves this phone when you export or back it up yourself.",
                        icon = Icons.Filled.Shield,
                    )
                    Divider()
                    SettingsRow("Version", subtitle = BuildConfig.VERSION_NAME, icon = Icons.Filled.Description)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    when (dialog) {
        SettingsDialog.THEME -> ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { it.label() },
            onSelect = { vm.setTheme(it) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.CURRENCY -> ChoiceDialog(
            title = "Currency",
            options = Currencies.supported,
            selected = settings.currencyCode,
            label = { "$it · ${Currencies.displayName(it)}" },
            onSelect = { vm.setCurrency(it) },
            onDismiss = { dialog = null },
            note = "Changing the currency changes how amounts are displayed. Stored amounts are not converted.",
        )
        SettingsDialog.OPENING -> AmountInputDialog(
            title = "Opening balance",
            message = "Money you had before your first recorded transaction. Total balance = opening balance + income \u2212 expenses.",
            initialMinor = settings.openingBalanceMinor,
            allowNegative = true,
            allowZero = true,
            onConfirm = { vm.setOpeningBalance(it ?: 0L) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.PIN -> PinSetupDialog(onConfirm = { vm.setPin(it) }, onDismiss = { dialog = null })
        SettingsDialog.DISABLE_LOCK -> ConfirmDialog(
            title = "Turn off app lock?",
            text = "Anyone with access to this phone will be able to open Moneta.",
            confirmLabel = "Turn off",
            onConfirm = { vm.disableLock() },
            onDismiss = { dialog = null },
        )
        SettingsDialog.EXPORT_CSV, SettingsDialog.EXPORT_PDF -> {
            val isCsv = dialog == SettingsDialog.EXPORT_CSV
            ChoiceDialog(
                title = if (isCsv) "Export CSV" else "Export PDF",
                options = ExportRange.entries,
                selected = null,
                label = { it.label },
                onSelect = { range ->
                    vm.pendingExportRange = range
                    val name = "moneta-${range.name.lowercase().replace('_', '-')}-${LocalDate.now()}"
                    if (isCsv) csvLauncher.launch("$name.csv") else pdfLauncher.launch("$name.pdf")
                },
                onDismiss = { dialog = null },
            )
        }
        SettingsDialog.RESTORE_CONFIRM -> ConfirmDialog(
            title = "Restore backup?",
            text = "All current transactions, categories, budgets and settings will be replaced by the backup's contents. This cannot be undone.",
            confirmLabel = "Restore",
            onConfirm = { restoreUri?.let { vm.restore(it) } },
            onDismiss = { dialog = null; restoreUri = null },
        )
        null -> Unit
    }
}

private fun toggleAlerts(enabled: Boolean, vm: SettingsViewModel, requestPermission: (String) -> Unit) {
    vm.setBudgetAlerts(enabled)
    if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) requestPermission(Manifest.permission.POST_NOTIFICATIONS)
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> "System default"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

@Composable
private fun Divider() {
    HorizontalDivider(Modifier.padding(start = 74.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun Chevron() {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(options) { o ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(o); onDismiss() }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selected != null) {
                                RadioButton(selected = o == selected, onClick = { onSelect(o); onDismiss() })
                            } else {
                                Spacer(Modifier.width(12.dp).height(44.dp))
                            }
                            Text(label(o), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PinSetupDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set PIN") },
        text = {
            Column {
                Text("Choose a 4–8 digit PIN. It is stored only as a secure hash on this device.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pin, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) { pin = it; error = null } },
                    label = { Text("PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm, onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) { confirm = it; error = null } },
                    label = { Text("Confirm PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    pin.length < 4 -> "PIN must have at least 4 digits"
                    pin != confirm -> "PINs do not match"
                    else -> null
                }
                if (error == null) { onConfirm(pin); onDismiss() }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
