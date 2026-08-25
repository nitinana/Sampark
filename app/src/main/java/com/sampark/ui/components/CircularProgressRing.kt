package com.sampark.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sampark.ui.theme.CardBackground
import com.sampark.ui.theme.HeadingTextColor
import com.sampark.ui.theme.SecondaryBorder

@Composable
fun CircularProgressRing(
    progressFraction: Float,
    percentageLabel: String,
    ringColor: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(160.dp)) {
            drawArc(
                color = SecondaryBorder,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 14.dp.toPx())
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * progressFraction.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = 14.dp.toPx())
            )
        }
        Box(
            modifier = Modifier.size(124.dp).background(CardBackground, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(percentageLabel, fontSize = 32.sp, color = HeadingTextColor)
        }
    }
}
