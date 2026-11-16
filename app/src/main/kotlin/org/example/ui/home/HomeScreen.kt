package org.example.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            .padding(top = 24.dp, bottom = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = stringResource(R.string.prototype_badge),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(20.dp))

        when {
            uiState.isLoading -> LoadingContent(Modifier.weight(1f))
            uiState.errorMessage != null || uiState.hasLoadError -> ErrorContent(
                message = uiState.errorMessage ?: stringResource(R.string.document_list_load_failed),
                onRetryClick = onRetryClick,
                modifier = Modifier.weight(1f)
            )
            uiState.documents.isEmpty() -> EmptyContent(
                onScanClick = onScanClick,
                enabled = !uiState.isScanning && !uiState.isOperationInProgress,
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
                .defaultMinSize(minHeight = 56.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(stringResource(R.string.scan_action), style = MaterialTheme.typography.titleMedium)
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
    onScanClick: () -> Unit,
    enabled: Boolean,
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
            OutlinedButton(onClick = onScanClick, enabled = enabled) {
                Text(stringResource(R.string.scan_action))
            }
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
