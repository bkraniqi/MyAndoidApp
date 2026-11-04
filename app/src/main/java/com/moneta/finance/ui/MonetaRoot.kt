package com.moneta.finance.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneta.finance.AppContainer
import com.moneta.finance.data.AppSettings
import com.moneta.finance.domain.MoneyFormatter
import com.moneta.finance.ui.components.LocalMoneyFormatter
import com.moneta.finance.ui.lock.LockScreen
import com.moneta.finance.ui.theme.MonetaTheme

@Composable
fun MonetaRoot(container: AppContainer, activity: FragmentActivity, openBudgetsRequest: Int) {
    val settings: AppSettings? by container.repository.settingsFlow.collectAsStateWithLifecycle(initialValue = null)
    val locked by container.appLock.locked.collectAsStateWithLifecycle()
    val current = settings ?: AppSettings()

    MonetaTheme(themeMode = current.themeMode) {
        val formatter = remember(current.currencyCode) { MoneyFormatter(current.currencyCode) }
        CompositionLocalProvider(
            LocalMoneyFormatter provides formatter,
            LocalAppContainer provides container,
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                // Financial content is not composed at all while the app is locked.
                if (settings != null && !locked) {
                    MonetaNavigation(openBudgetsRequest = openBudgetsRequest)
                }
                AnimatedVisibility(visible = locked, enter = fadeIn(), exit = fadeOut()) {
                    LockScreen(lock = container.appLock, activity = activity)
                }
            }
        }
    }
}
