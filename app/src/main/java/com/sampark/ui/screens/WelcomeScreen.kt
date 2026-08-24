package com.sampark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.size(148.dp).background(TranslateAmber.copy(alpha = 0.15f), CircleShape)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(20.dp))
        Text(
            "मराठी संपर्क",
            style = MaterialTheme.typography.headlineLarge,
            color = HeadingTextColor,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        Text(
            "हे ॲप तुमच्या फोनमधील इंग्रजी नावं आपोआप मराठीत बदलेल, जेणेकरून ती वाचायला सोपी होतील. तुम्ही हे कधीही परत बदलू शकता.",
            style = MaterialTheme.typography.bodyLarge,
            color = BodyTextColor,
            textAlign = TextAlign.Center
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(24.dp))
        PrimaryButton(text = "सुरू करा", color = TranslateAmber, onClick = onStart)
    }
}
