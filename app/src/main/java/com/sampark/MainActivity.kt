package com.sampark

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sampark.ui.navigation.SamparkNavHost
import com.sampark.ui.theme.SamparkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as SamparkApplication).container

        setContent {
            var startDestination by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                startDestination = container.appRouter.resolveStartDestination()
            }

            SamparkTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    startDestination?.let { destination ->
                        SamparkNavHost(container = container, startDestination = destination)
                    }
                }
            }
        }
    }
}
