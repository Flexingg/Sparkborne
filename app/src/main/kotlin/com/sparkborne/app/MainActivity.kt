package com.sparkborne.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sparkborne.app.ui.SparkborneRootScreen
import com.sparkborne.app.ui.SparkborneViewModel
import com.sparkborne.app.ui.theme.SparkborneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SparkborneTheme {
                val vm: SparkborneViewModel = viewModel()
                SparkborneRootScreen(viewModel = vm)
            }
        }
    }
}
