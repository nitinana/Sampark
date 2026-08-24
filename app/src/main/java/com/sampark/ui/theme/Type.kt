package com.sampark.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sampark.R

val NotoSansDevanagari = FontFamily(
    Font(R.font.noto_sans_devanagari_regular, FontWeight.Normal),
    Font(R.font.noto_sans_devanagari_medium, FontWeight.Medium),
    Font(R.font.noto_sans_devanagari_semibold, FontWeight.SemiBold),
    Font(R.font.noto_sans_devanagari_bold, FontWeight.Bold)
)

val SamparkTypography = Typography(
    headlineLarge = TextStyle(fontFamily = NotoSansDevanagari, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = NotoSansDevanagari, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    bodyLarge = TextStyle(fontFamily = NotoSansDevanagari, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontFamily = NotoSansDevanagari, fontWeight = FontWeight.Normal, fontSize = 19.sp),
    labelLarge = TextStyle(fontFamily = NotoSansDevanagari, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
)
