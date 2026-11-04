package com.moneta.finance

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.moneta.finance.ui.MonetaRoot

/** Extends FragmentActivity because BiometricPrompt requires it. */
class MainActivity : FragmentActivity() {

    private var openBudgetsRequest by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(EXTRA_OPEN_BUDGETS, false) == true) openBudgetsRequest++
        val container = (application as MonetaApplication).container
        setContent {
            MonetaRoot(
                container = container,
                activity = this,
                openBudgetsRequest = openBudgetsRequest,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_BUDGETS, false)) openBudgetsRequest++
    }

    override fun onStart() {
        super.onStart()
        val container = (application as MonetaApplication).container
        container.appLock.onAppForegrounded()
        // Catch up on recurring transactions that became due while the app was in the background.
        lifecycleScope.launch { runCatching { container.repository.processRecurring(LocalDate.now()) } }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) (application as MonetaApplication).container.appLock.onAppBackgrounded()
    }

    companion object {
        const val EXTRA_OPEN_BUDGETS = "open_budgets"
    }
}
