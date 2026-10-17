package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.MainAppScreen
import com.example.ui.WifiViewModel
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val wifiViewModel: WifiViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        wifiViewModel.startForegroundScanning()
    }

    override fun onStop() {
        wifiViewModel.stopForegroundScanning()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CyberBackground
                ) {
                    MainAppScreen(viewModel = wifiViewModel)
                }
            }
        }
    }
}
