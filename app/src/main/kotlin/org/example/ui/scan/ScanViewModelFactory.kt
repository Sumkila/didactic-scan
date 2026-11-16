package org.example.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.example.data.DocumentRepository

class ScanViewModelFactory(
    private val documentRepository: DocumentRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(ScanViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return modelClass.cast(ScanViewModel(documentRepository))
            ?: throw IllegalArgumentException("Could not create ViewModel: ${modelClass.name}")
    }
}
