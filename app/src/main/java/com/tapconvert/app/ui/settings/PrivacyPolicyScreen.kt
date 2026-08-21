package com.tapconvert.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Last Updated: August 2026",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PolicySection(
                title = "1. Zero Cloud Uploads",
                body = "TapConvert operates 100% locally on your device. When you convert, compress, or extract media, all processing occurs directly in your phone's memory. Your files are never transmitted across the internet to any server."
            )

            PolicySection(
                title = "2. No Data Collection or Tracking",
                body = "We do not track your activity, scan your file contents, or log personal identifiers. We have no user accounts, no analytics tracking on your personal data, and no access to your media files."
            )

            PolicySection(
                title = "3. Device Storage Permissions",
                body = "The app utilizes Android Scoped Storage and the Photo Picker to access only the specific files you select. Generated files are saved to your public media collections (Pictures, Movies, Music, Documents) or a custom folder of your choice."
            )

            PolicySection(
                title = "4. Advertisements",
                body = "TapConvert utilizes Google AdMob to display optional rewarded ads for features like Fast Pass batch unlocks. No personally identifiable media information is shared with ad providers."
            )

            PolicySection(
                title = "5. Contact & Support",
                body = "For any questions or feedback regarding TapConvert, contact our developer team. We are committed to complete transparency and user privacy."
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PolicySection(
    title: String,
    body: String
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
