package com.box.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.box.app.ui.components.AppScaffold
import com.box.app.ui.theme.AppTheme
import com.box.app.utils.UiScaleManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                val baseDensity = LocalDensity.current
                val uiScale by UiScaleManager.uiScale.collectAsState()

                val scaledDensity = Density(
                    density = baseDensity.density * uiScale,
                    fontScale = baseDensity.fontScale * uiScale
                )

                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    AppScaffold()
                }
            }
        }
    }
}