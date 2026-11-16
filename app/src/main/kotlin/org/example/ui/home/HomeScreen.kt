package org.example.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.example.R
import org.example.ui.model.DocumentUiModel
import org.example.ui.sample.SampleDocuments
import org.example.ui.theme.DocScannerTheme

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onScanClick: () -> Unit,
    onDocumentClick: (Long) -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandMark()
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.app_creator),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        ScanIntroCard()
        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        when {
            uiState.isLoading -> LoadingContent(Modifier.weight(1f))
            uiState.errorMessage != null || uiState.hasLoadError -> ErrorContent(
                message = uiState.errorMessage ?: stringResource(R.string.document_list_load_failed),
                onRetryClick = onRetryClick,
                modifier = Modifier.weight(1f)
            )
            uiState.documents.isEmpty() -> EmptyContent(
                modifier = Modifier.weight(1f)
            )
            else -> DocumentList(
                documents = uiState.documents,
                onDocumentClick = onDocumentClick,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onScanClick,
            enabled = !uiState.isScanning && !uiState.isOperationInProgress,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 60.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                stringResource(R.string.scan_action),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BrandMark() {
    val ink = MaterialTheme.colorScheme.onPrimary
    val accent = MaterialTheme.colorScheme.tertiaryContainer
    val brandColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary
    )
    Surface(
        modifier = Modifier.size(52.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(brandColors)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(25.dp)) {
                val lineWidth = 2.dp.toPx()
                val arm = size.width * 0.3f
                val inset = size.width * 0.12f
                val right = size.width - inset
                val bottom = size.height - inset
                drawLine(ink, Offset(inset, inset), Offset(inset + arm, inset), lineWidth)
                drawLine(ink, Offset(inset, inset), Offset(inset, inset + arm), lineWidth)
                drawLine(ink, Offset(right, inset), Offset(right - arm, inset), lineWidth)
                drawLine(ink, Offset(right, inset), Offset(right, inset + arm), lineWidth)
                drawLine(ink, Offset(inset, bottom), Offset(inset + arm, bottom), lineWidth)
                drawLine(ink, Offset(inset, bottom), Offset(inset, bottom - arm), lineWidth)
                drawLine(ink, Offset(right, bottom), Offset(right - arm, bottom), lineWidth)
                drawLine(ink, Offset(right, bottom), Offset(right, bottom - arm), lineWidth)
                drawCircle(
                    color = accent,
                    radius = 2.dp.toPx(),
                    center = Offset(size.width / 2f, size.height / 2f)
                )
            }
        }
    }
}

@Composable
private fun ScanIntroCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_eyebrow),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.home_hero_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.home_hero_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                modifier = Modifier.size(60.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "PDF",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentList(
    documents: List<DocumentUiModel>,
    onDocumentClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(documents, key = { it.id }) { document ->
            DocumentCard(
                document = document,
                onClick = { onDocumentClick(document.id) }
            )
        }
    }
}

@Composable
private fun EmptyContent(
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.empty_documents_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.empty_documents_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = message, style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onRetryClick) {
                Text(stringResource(R.string.retry_action))
            }
        }
    }
}

@Preview(showBackground = true, name = "Home, light")
@Composable
private fun HomeLightPreview() {
    DocScannerTheme(darkTheme = false) {
        HomeScreen(HomeUiState(SampleDocuments.all), {}, {}, {})
    }
}

@Preview(showBackground = true, name = "Home, dark")
@Composable
private fun HomeDarkPreview() {
    DocScannerTheme(darkTheme = true) {
        HomeScreen(HomeUiState(SampleDocuments.all), {}, {}, {})
    }
}

@Preview(showBackground = true, name = "Home, empty")
@Composable
private fun EmptyHomePreview() {
    DocScannerTheme {
        HomeScreen(HomeUiState(), {}, {}, {})
    }
}

@Preview(showBackground = true, name = "Home, loading")
@Composable
private fun LoadingHomePreview() {
    DocScannerTheme {
        HomeScreen(HomeUiState(isLoading = true), {}, {}, {})
    }
}

@Preview(showBackground = true, name = "Home, error")
@Composable
private fun ErrorHomePreview() {
    DocScannerTheme {
        HomeScreen(
            HomeUiState(errorMessage = stringResource(R.string.sample_error_preview_message)),
            {},
            {},
            {}
        )
    }
}
