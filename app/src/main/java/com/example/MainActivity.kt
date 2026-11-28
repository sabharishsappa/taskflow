package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.AppThemeMode
import com.example.data.SettingsRepository
import com.example.data.TaskRepository
import com.example.data.local.PrismTaskDatabase
import com.example.reminder.ReminderScheduler
import com.example.ui.TaskDashboardScreen
import com.example.ui.TaskViewModel
import com.example.ui.theme.PrismTaskTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderScheduler(applicationContext).ensureNotificationChannel()

        setContent {
            PrismTaskApp()
        }
    }
}

@Composable
fun PrismTaskApp() {
    val taskViewModel: TaskViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val database = PrismTaskDatabase.getInstance(app)
                TaskViewModel(
                    repository = TaskRepository(database.taskDao()),
                    settingsRepository = SettingsRepository(app),
                    reminderScheduler = ReminderScheduler(app)
                )
            }
        }
    )

    val settings by taskViewModel.settings.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (settings.themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    PrismTaskTheme(
        darkTheme = useDarkTheme,
        textScale = settings.textScale.multiplier
    ) {
        TaskDashboardScreen(viewModel = taskViewModel)
    }
}
