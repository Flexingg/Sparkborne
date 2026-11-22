package com.sparkborne.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sparkborne.app.ui.TelemetryInspectorScreen
import com.sparkborne.app.ui.TelemetryInspectorViewModel
import com.sparkborne.app.ui.theme.SparkborneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SparkborneTheme {
                val vm: TelemetryInspectorViewModel = viewModel()
                TelemetryInspectorScreen(viewModel = vm)
            }
        }
    }
}
