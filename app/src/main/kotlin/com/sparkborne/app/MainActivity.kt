package com.sparkborne.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sparkborne.app.ui.AnimatedSplashScreen
import com.sparkborne.app.ui.SparkborneRootScreen
import com.sparkborne.app.ui.SparkborneViewModel
import com.sparkborne.app.ui.theme.SparkborneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: SparkborneViewModel = viewModel()
            val state by vm.uiState.collectAsState()

            SparkborneTheme(themeId = state.selectedTheme) {
                AnimatedContent(
                    targetState = state.showSplash,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(420)) togetherWith fadeOut(animationSpec = tween(380))
                    },
                    label = "splash_to_app",
                ) { showingSplash ->
                    if (showingSplash) {
                        AnimatedSplashScreen(
                            themeId = state.selectedTheme,
                            splashStyle = state.selectedSplash,
                            versionLabel = state.installedVersionLabel,
                            onFinished = vm::dismissSplash,
                        )
                    } else {
                        SparkborneRootScreen(viewModel = vm)
                    }
                }
            }
        }
    }
}
