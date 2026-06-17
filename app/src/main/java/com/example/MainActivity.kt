package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ui.DocEditorScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.DocViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Permissions are now requested on-demand when features are clicked, making the startup fully professional.

        val viewModel: DocViewModel by viewModels {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DocViewModel(applicationContext) as T
                }
            }
        }

        setContent {
            val settingsSharedPreferences = remember { getSharedPreferences("ai_agent_prefs", MODE_PRIVATE) }
            var appearance by remember {
                mutableStateOf(settingsSharedPreferences.getString("appearance_key", "System") ?: "System")
            }

            DisposableEffect(settingsSharedPreferences) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "appearance_key") {
                        appearance = settingsSharedPreferences.getString("appearance_key", "System") ?: "System"
                    }
                }
                settingsSharedPreferences.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    settingsSharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            val isDark = when (appearance) {
                "Dark Theme" -> true
                "Light Theme" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDark, dynamicColor = false) {
                DocEditorScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
