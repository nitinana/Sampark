package com.sampark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.sampark.ui.components.PrimaryButton
import com.sampark.ui.theme.BackgroundColor
import com.sampark.ui.theme.BodyTextColor
import com.sampark.ui.theme.HeadingTextColor
import com.sampark.ui.theme.SecondaryTextColor
import com.sampark.ui.theme.TranslateAmber

@Composable
fun PermissionDeniedScreen(onGoToSettings: () -> Unit, onRetry: () -> Unit) {
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
            "काळजी करू नका",
            style = MaterialTheme.typography.headlineMedium,
            color = HeadingTextColor,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.padding(8.dp))
        Text(
            "या ॲपला काम करण्यासाठी संपर्कांची परवानगी हवी आहे.",
            style = MaterialTheme.typography.bodyLarge,
            color = BodyTextColor,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.padding(24.dp))
        PrimaryButton(text = "सेटिंग्जमध्ये जा", color = TranslateAmber, onClick = onGoToSettings)
        Spacer(Modifier.padding(18.dp))
        Text(
            "पुन्हा विचारा",
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryTextColor,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.padding(8.dp).clickable { onRetry() }
        )
    }
}
