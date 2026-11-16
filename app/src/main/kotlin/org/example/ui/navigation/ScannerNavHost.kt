package org.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.example.ui.detail.DocumentDetailScreen
import org.example.ui.home.HomeScreen
import org.example.ui.home.HomeUiState
import org.example.ui.model.DocumentUiModel

private const val HOME_ROUTE = "home"
private const val DOCUMENT_ROUTE = "document/{documentId}"

@Composable
fun ScannerNavHost(
    uiState: HomeUiState,
    onScanClick: () -> Unit,
    onRetryClick: () -> Unit,
    onOpenDocument: (Long) -> Unit,
    onShareDocument: (Long) -> Unit,
    onRenameDocument: (DocumentUiModel) -> Unit,
    onDeleteDocument: (DocumentUiModel, () -> Unit) -> Unit
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HOME_ROUTE) {
        composable(HOME_ROUTE) {
            HomeScreen(
                uiState = uiState,
                onScanClick = onScanClick,
                onDocumentClick = { documentId ->
                    navController.navigate("document/$documentId")
                },
                onRetryClick = onRetryClick
            )
        }
        composable(
            route = DOCUMENT_ROUTE,
            arguments = listOf(navArgument("documentId") { type = NavType.LongType })
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getLong("documentId")
            val document = uiState.documents.firstOrNull { it.id == documentId }
            DocumentDetailScreen(
                document = document,
                isOperationInProgress = uiState.isOperationInProgress,
                onBack = { navController.popBackStack() },
                onOpen = { document?.let { onOpenDocument(it.id) } },
                onShare = { document?.let { onShareDocument(it.id) } },
                onRename = { document?.let(onRenameDocument) },
                onDelete = {
                    document?.let { selectedDocument ->
                        onDeleteDocument(selectedDocument) { navController.popBackStack() }
                    }
                }
            )
        }
    }
}
