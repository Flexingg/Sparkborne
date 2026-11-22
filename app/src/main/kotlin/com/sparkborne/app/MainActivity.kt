package com.sparkborne.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.sparkborne.app.ui.AnimatedSplashScreen
import com.sparkborne.app.ui.SparkborneRootScreen
import com.sparkborne.app.ui.SparkborneViewModel
import com.sparkborne.app.ui.theme.SparkborneTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: SparkborneViewModel

    private val activityRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && ::viewModel.isInitialized) {
            viewModel.refreshLiveStepTracker()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[SparkborneViewModel::class.java]

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasPerm = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        setContent {
            val state by viewModel.uiState.collectAsState()

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
                            onFinished = viewModel::dismissSplash,
                        )
                    } else {
                        SparkborneRootScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
