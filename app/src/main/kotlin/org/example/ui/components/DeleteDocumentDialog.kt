package org.example.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.example.R
import org.example.ui.theme.DocScannerTheme

@Composable
fun DeleteDocumentDialog(
    documentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_document_title)) },
        text = { Text(stringResource(R.string.delete_document_message, documentTitle)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_action))
            }
        }
    )
}

@Preview(showBackground = true, name = "Delete confirmation")
@Composable
private fun DeleteDocumentDialogPreview() {
    DocScannerTheme {
        DeleteDocumentDialog(
            documentTitle = "Science Fair Project Notes",
            onDismiss = {},
            onConfirm = {}
        )
    }
}
