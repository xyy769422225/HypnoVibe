package com.hypno.hypnovibe.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hypno.hypnovibe.ui.theme.*

enum class ButtonVariant { PRIMARY, SECONDARY, DANGER }

@Composable
fun DungeonButton(
    text: String,
    onClick: () -> Unit,
    variant: ButtonVariant = ButtonVariant.PRIMARY,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    when (variant) {
        ButtonVariant.PRIMARY -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BloodRed,
                contentColor = SilverGray,
                disabledContainerColor = BloodRed.copy(alpha = 0.3f),
                disabledContentColor = DarkGray
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(text = text, fontWeight = FontWeight.SemiBold)
        }

        ButtonVariant.SECONDARY -> OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = SilverGray,
                disabledContentColor = DarkGray
            ),
            border = BorderStroke(1.dp, DarkCopper),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(text = text, fontWeight = FontWeight.SemiBold)
        }

        ButtonVariant.DANGER -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AlertRed,
                contentColor = SilverGray,
                disabledContainerColor = AlertRed.copy(alpha = 0.3f),
                disabledContentColor = DarkGray
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(text = text, fontWeight = FontWeight.SemiBold)
        }
    }
}
