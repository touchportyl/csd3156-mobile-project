package com.example.tiltmaster.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tiltmaster.data.DatabaseProvider
import com.example.tiltmaster.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(
        DatabaseProvider.get(app).settingsDao()
    )

    val settings = repo.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        initialValue = com.example.tiltmaster.data.SettingsEntity()
    )

    init {
        viewModelScope.launch { repo.ensureDefaults() }
    }

    fun updateSensitivity(v: Float) = viewModelScope.launch { repo.updateSensitivity(v) }
    fun setVibration(enabled: Boolean) = viewModelScope.launch { repo.setVibration(enabled) }
    fun setSound(enabled: Boolean) = viewModelScope.launch { repo.setSound(enabled) }
}