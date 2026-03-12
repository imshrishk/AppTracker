package com.apptracker.ui.screens.appcompare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apptracker.data.model.AppInfo
import com.apptracker.domain.usecase.GetInstalledAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppCompareUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val appA: AppInfo? = null,
    val appB: AppInfo? = null
)

@HiltViewModel
class AppCompareViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getInstalledApps: GetInstalledAppsUseCase
) : ViewModel() {

    private val packageA: String = checkNotNull(savedStateHandle["packageA"])
    private val packageB: String = checkNotNull(savedStateHandle["packageB"])

    private val _uiState = MutableStateFlow(AppCompareUiState())
    val uiState: StateFlow<AppCompareUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch {
            _uiState.value = AppCompareUiState(isLoading = true)
            try {
                val apps = getInstalledApps(includeSystem = true)
                val a = apps.find { it.packageName == packageA }
                val b = apps.find { it.packageName == packageB }
                _uiState.value = AppCompareUiState(isLoading = false, appA = a, appB = b)
            } catch (e: Exception) {
                _uiState.value = AppCompareUiState(
                    isLoading = false,
                    error = e.message ?: "Failed to load app data"
                )
            }
        }
    }
}
