package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.BrowserViewModel
import com.example.ui.screens.BrowserMainScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val browserViewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure Chromium cache directories exist to prevent disk index reconstruction errors
        try {
            val codeCacheJs = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            if (!codeCacheJs.exists()) codeCacheJs.mkdirs()
            val codeCacheWasm = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
            if (!codeCacheWasm.exists()) codeCacheWasm.mkdirs()
        } catch (e: Exception) {
            // safe fallback
        }

        setContent {
            val state by browserViewModel.uiState.collectAsStateWithLifecycle()

            // Protect Recent-Apps Overview preview and prevent screenshots when in Private Mode or Locked
            LaunchedEffect(state.isIncognitoMode, state.isPrivateSessionLocked) {
                if (state.isIncognitoMode || state.isPrivateSessionLocked) {
                    window.setFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                        android.view.WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            MyApplicationTheme(themeMode = state.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BrowserMainScreen(viewModel = browserViewModel)
                }
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            try {
                // Clear all web storage and caches on low-memory triggers to prevent OutOfMemory force-closes
                android.webkit.WebStorage.getInstance().deleteAllData()
            } catch (e: Exception) {
                // safe graceful ignore
            }
        }
    }
}
