package com.sampark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sampark.data.status.Direction
import com.sampark.ui.RunUiState
import com.sampark.ui.components.CircularProgressRing
import com.sampark.ui.components.PrimaryButton
import com.sampark.ui.components.SecondaryButton
import com.sampark.ui.theme.BackgroundColor
import com.sampark.ui.theme.BodyTextColor
import com.sampark.ui.theme.CardBackground
import com.sampark.ui.theme.ResumeAmber
import com.sampark.ui.theme.RollbackRose
import com.sampark.ui.theme.SecondaryTextColor
import com.sampark.ui.theme.TranslateAmber

@Composable
fun RunProgressScreen(
    direction: Direction,
    uiState: RunUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val ringColor = if (direction == Direction.TRANSLATE) TranslateAmber else RollbackRose
    val runningStatusText = if (direction == Direction.TRANSLATE) {
        "तुमची नावं मराठीत बदलली जात आहेत, कृपया थांबा"
    } else {
        "इंग्रजी नावं परत आणली जात आहेत"
    }
    val pausedStatusText = if (direction == Direction.TRANSLATE) {
        "काही काळजी नाही — तुमची नावं जशी आहेत तशीच सुरक्षित आहेत."
    } else {
        "काही काळजी नाही — आतापर्यंत बदललेली नावं तशीच राहतील."
    }
    val progressFraction = if (uiState.total == 0) 0f else uiState.done.toFloat() / uiState.total
    val percentageLabel = "${(progressFraction * 100).toInt()}%"
    val countLabel = "${uiState.done} पैकी ${uiState.total} संपर्क पूर्ण"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressRing(
            progressFraction = progressFraction,
            percentageLabel = percentageLabel,
            ringColor = ringColor
        )
        Spacer(Modifier.padding(18.dp))
        Text(
            if (uiState.isPaused) pausedStatusText else runningStatusText,
            style = MaterialTheme.typography.bodyLarge,
            color = BodyTextColor,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.padding(6.dp))
        Text(countLabel, style = MaterialTheme.typography.bodyMedium, color = SecondaryTextColor)

        uiState.lastProcessed?.let { (original, translated) ->
            Spacer(Modifier.padding(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardBackground, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(original, color = SecondaryTextColor)
                Text(" → ", color = ringColor)
                Text(translated, color = BodyTextColor)
            }
        }

        Spacer(Modifier.padding(18.dp))

        if (!uiState.isPaused) {
            SecondaryButton(text = "थांबवा", onClick = onPause)
        } else {
            PrimaryButton(text = "सुरू ठेवा", color = ResumeAmber, onClick = onResume)
            if (direction == Direction.TRANSLATE) {
                Spacer(Modifier.padding(12.dp))
                SecondaryButton(text = "रद्द करा", onClick = onCancel)
            }
        }
    }
}
