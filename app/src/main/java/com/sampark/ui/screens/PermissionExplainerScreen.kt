package com.sampark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sampark.ui.components.PrimaryButton
import com.sampark.ui.theme.BackgroundColor
import com.sampark.ui.theme.BodyTextColor
import com.sampark.ui.theme.HeadingTextColor
import com.sampark.ui.theme.TranslateAmber

@Composable
fun PermissionExplainerScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "एक पायरी बाकी आहे",
            style = MaterialTheme.typography.headlineMedium,
            color = HeadingTextColor,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        Text(
            "पुढील स्क्रीनवर \"Allow\" या बटणावर दाबा.",
            style = MaterialTheme.typography.bodyLarge,
            color = BodyTextColor,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
        PrimaryButton(text = "पुढे जा", color = TranslateAmber, onClick = onContinue)
    }
}
