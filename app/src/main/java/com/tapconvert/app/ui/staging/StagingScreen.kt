package com.tapconvert.app.ui.staging

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapconvert.app.ui.components.QuantumApertureVisualizer
import com.tapconvert.app.ui.theme.AccentAmber
import com.tapconvert.app.ui.theme.PrimaryTeal
import com.tapconvert.app.ui.theme.SavingsGreen
import com.tapconvert.core.common.status.ProcessingStatusResolver
import com.tapconvert.core.model.MediaCategory

@Composable
fun StagingScreen(
    fileCount: Int = 1,
    category: MediaCategory? = null,
    customMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val headline = customMessage ?: ProcessingStatusResolver.resolveIntakeTitle(fileCount)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val availableHeight = maxHeight
        val apertureSize = (availableHeight * 0.24f).coerceIn(110.dp, 160.dp)
        val spacing = (availableHeight * 0.025f).coerceIn(8.dp, 24.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. Quantum Aperture in Intake Mode
            QuantumApertureVisualizer(
                percentage = null,
                sizeDp = apertureSize
            )

            Spacer(modifier = Modifier.height(spacing))

            // 2. Clear, Reassuring Human-First Headline
            Text(
                text = headline,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Compact Meta Badge
            val categoryLabel = when (category) {
                MediaCategory.IMAGE -> if (fileCount > 1) "$fileCount Photos" else "Photo"
                MediaCategory.VIDEO -> if (fileCount > 1) "$fileCount Videos" else "Video"
                MediaCategory.DOCUMENT -> if (fileCount > 1) "$fileCount Documents" else "Document"
                MediaCategory.AUDIO -> if (fileCount > 1) "$fileCount Audio Files" else "Audio"
                null -> if (fileCount > 1) "$fileCount Files" else "Media File"
            }

            Surface(
                shape = RoundedCornerShape(99.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ) {
                Text(
                    text = "$categoryLabel • Local Staging",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(spacing * 1.5f))

            // 4. Privacy & Security Assurance Footer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = SavingsGreen,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "100% On-Device • Fast & Private",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
