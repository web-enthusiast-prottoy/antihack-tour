package bd.arena.prototype

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

@Composable
internal fun LocalizedTypography(bangla: Boolean, content: @Composable () -> Unit) {
    val font = if(bangla) androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.hind_siliguri)) else androidx.compose.ui.text.font.FontFamily.Default
    val base=MaterialTheme.typography
    fun localized(style:androidx.compose.ui.text.TextStyle)=style.copy(fontFamily=font,fontSize=if(bangla)style.fontSize*0.90f else style.fontSize,lineHeight=if(bangla)style.lineHeight*0.92f else style.lineHeight)
    MaterialTheme(typography=Typography(displayLarge=localized(base.displayLarge),displayMedium=localized(base.displayMedium),displaySmall=localized(base.displaySmall),headlineLarge=localized(base.headlineLarge),headlineMedium=localized(base.headlineMedium),headlineSmall=localized(base.headlineSmall),titleLarge=localized(base.titleLarge),titleMedium=localized(base.titleMedium),titleSmall=localized(base.titleSmall),bodyLarge=localized(base.bodyLarge),bodyMedium=localized(base.bodyMedium),bodySmall=localized(base.bodySmall),labelLarge=localized(base.labelLarge),labelMedium=localized(base.labelMedium),labelSmall=localized(base.labelSmall))) {
        content()
    }
}
