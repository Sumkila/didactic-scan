package org.example.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import org.example.R
import org.example.ui.theme.DocScannerTheme

@Composable
fun DocumentNameDialog(
    title: String,
    initialName: String,
    cancelLabel: String,
    confirmLabel: String,
    isBusy: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    val trimmedName = name.trim()
    val nameIsValid = trimmedName.isNotEmpty()

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy,
                label = { Text(stringResource(R.string.document_name_label)) },
                singleLine = true,
                isError = !nameIsValid,
                supportingText = {
                    if (!nameIsValid) {
                        Text(stringResource(R.string.name_required))
                    }
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (nameIsValid && !isBusy) onConfirm(trimmedName) }
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmedName) },
                enabled = nameIsValid && !isBusy
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(cancelLabel)
            }
        }
    )
}

@Preview(showBackground = true, name = "Name dialog")
@Composable
private fun DocumentNameDialogPreview() {
    DocScannerTheme {
        DocumentNameDialog(
            title = stringResource(R.string.name_new_document_title),
            initialName = stringResource(R.string.suggested_document_name),
            cancelLabel = stringResource(R.string.cancel_action),
            confirmLabel = stringResource(R.string.save_action),
            onDismiss = {},
            onConfirm = {}
        )
    }
}
