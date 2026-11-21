package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.notifications.TaskReminderReceiver
import com.example.ui.screens.DayloomApp
import com.example.ui.theme.DayloomTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TaskReminderReceiver.ensureNotificationChannel(this)
        setContent {
            DayloomTheme {
                DayloomApp()
            }
        }
    }
}
