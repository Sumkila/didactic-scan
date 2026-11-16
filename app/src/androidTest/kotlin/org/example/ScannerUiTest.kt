package org.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.example.ui.components.DeleteDocumentDialog
import org.example.ui.components.DocumentNameDialog
import org.example.ui.home.HomeScreen
import org.example.ui.home.HomeUiState
import org.example.ui.model.DocumentUiModel
import org.example.ui.theme.DocScannerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScannerUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyHomeShowsHelpfulMessageAndScanAction() {
        var scanClicked = false
        composeRule.setContent {
            DocScannerTheme {
                HomeScreen(HomeUiState(), { scanClicked = true }, {}, {})
            }
        }

        composeRule.onNodeWithText("No documents yet").assertIsDisplayed()
        composeRule.onAllNodesWithText("Scan a document")[0].performClick()
        composeRule.runOnIdle { assertTrue(scanClicked) }
    }

    @Test
    fun documentCardReportsItsStableDocumentId() {
        var clickedId: Long? = null
        val document = DocumentUiModel(42L, "A sample document", "Today", 2)

        composeRule.setContent {
            DocScannerTheme {
                HomeScreen(
                    uiState = HomeUiState(listOf(document)),
                    onScanClick = {},
                    onDocumentClick = { clickedId = it },
                    onRetryClick = {}
                )
            }
        }

        composeRule.onNodeWithText("A sample document").performClick()
        composeRule.runOnIdle { assertEquals(42L, clickedId) }
    }

    @Test
    fun blankDocumentNameCannotBeConfirmed() {
        composeRule.setContent {
            DocScannerTheme {
                DocumentNameDialog(
                    title = "Name document",
                    initialName = "Sample",
                    cancelLabel = "Cancel",
                    confirmLabel = "Save",
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }

        composeRule.onNodeWithText("Sample").performTextClearance()
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        composeRule.onNodeWithText("Enter a name to continue.").assertIsDisplayed()
    }

    @Test
    fun deleteCanBeConfirmedOrCancelled() {
        var deleted = false
        var showingDialog by mutableStateOf(true)
        composeRule.setContent {
            DocScannerTheme {
                if (showingDialog) {
                    DeleteDocumentDialog(
                        documentTitle = "Sample",
                        onDismiss = { showingDialog = false },
                        onConfirm = {
                            deleted = true
                            showingDialog = false
                        }
                    )
                }
            }
        }

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { assertFalse(deleted) }
        composeRule.runOnIdle { showingDialog = true }
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.runOnIdle { assertTrue(deleted) }
    }

}
