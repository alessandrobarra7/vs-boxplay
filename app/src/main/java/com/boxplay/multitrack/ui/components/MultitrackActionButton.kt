package com.boxplay.multitrack.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boxplay.ui.theme.BoxPlayBackground
import com.boxplay.ui.theme.BoxPlayCardBorder
import com.boxplay.ui.theme.BoxPlayCoral
import com.boxplay.ui.theme.BoxPlayElectricBlue
import com.boxplay.ui.theme.BoxPlayPrimaryText
import com.boxplay.ui.theme.BoxPlaySecondaryText
import com.boxplay.ui.theme.BoxPlaySurfaceRaised
import com.boxplay.ui.theme.BoxPlaySurfaceSoft

@Composable
internal fun MultitrackActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, when {
            !enabled -> BoxPlaySecondaryText.copy(alpha = 0.22f)
            emphasized -> BoxPlayCoral
            else -> BoxPlayCardBorder
        }),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (emphasized) BoxPlayCoral else BoxPlaySurfaceRaised,
            contentColor = if (emphasized) BoxPlayBackground else BoxPlayPrimaryText,
            disabledContainerColor = BoxPlaySurfaceSoft,
            disabledContentColor = BoxPlaySecondaryText,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                !enabled -> BoxPlaySecondaryText
                emphasized -> BoxPlayBackground
                else -> BoxPlayElectricBlue
            },
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f, fill = false),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            textAlign = TextAlign.Center,
        )
    }
}

