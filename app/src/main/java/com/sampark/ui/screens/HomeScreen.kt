package com.sampark.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import com.sampark.data.status.Direction
import com.sampark.ui.components.PrimaryButton
import com.sampark.ui.theme.BackgroundColor
import com.sampark.ui.theme.BodyTextColor
import com.sampark.ui.theme.RollbackRose
import com.sampark.ui.theme.TranslateAmber

@Composable
fun HomeScreen(direction: Direction, onRollbackOrTranslateAgain: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (direction == Direction.TRANSLATE) {
            Text(
                "सर्व नावं मराठीत बदलली आहेत",
                style = MaterialTheme.typography.bodyLarge,
                color = BodyTextColor,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.padding(24.dp))
            PrimaryButton(
                text = "इंग्रजी नावं परत आणा",
                color = RollbackRose,
                onClick = onRollbackOrTranslateAgain
            )
        } else {
            Text(
                "नावं आता इंग्रजीत आहेत",
                style = MaterialTheme.typography.bodyLarge,
                color = BodyTextColor,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.padding(24.dp))
            PrimaryButton(
                text = "पुन्हा मराठीत बदला",
                color = TranslateAmber,
                onClick = onRollbackOrTranslateAgain
            )
        }
    }
}
