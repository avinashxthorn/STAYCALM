package com.staycalm.focus

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun pinkTypography():Typography {
    val family=FontFamily(Font(R.font.manrope,FontWeight.Normal),Font(R.font.manrope,FontWeight.Medium),Font(R.font.manrope,FontWeight.SemiBold),Font(R.font.manrope,FontWeight.Bold))
    fun style(size:Int,weight:FontWeight=FontWeight.Normal)=TextStyle(fontFamily=family,fontSize=size.sp,lineHeight=(size*1.4).sp,fontWeight=weight)
    return Typography(displayLarge=style(57,FontWeight.Bold),displayMedium=style(45,FontWeight.Bold),displaySmall=style(36,FontWeight.Bold),headlineLarge=style(32,FontWeight.Bold),headlineMedium=style(28,FontWeight.Bold),headlineSmall=style(24,FontWeight.Bold),titleLarge=style(22,FontWeight.SemiBold),titleMedium=style(16,FontWeight.SemiBold),titleSmall=style(14,FontWeight.SemiBold),bodyLarge=style(16),bodyMedium=style(14),bodySmall=style(12),labelLarge=style(14,FontWeight.SemiBold),labelMedium=style(12,FontWeight.SemiBold),labelSmall=style(11,FontWeight.SemiBold))
}
