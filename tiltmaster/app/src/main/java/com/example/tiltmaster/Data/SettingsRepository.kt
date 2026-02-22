package com.example.tiltmaster.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dao: SettingsDao) {

    val settings: Flow<SettingsEntity> =
        dao.observeSettings().map { it ?: SettingsEntity() } // default if empty

    suspend fun ensureDefaults() {
        if (dao.getSettings() == null) {
            dao.upsert(SettingsEntity()) // insert default row id=1
        }
    }

    suspend fun updateSensitivity(value: Float) {
        val current = dao.getSettings() ?: SettingsEntity()
        dao.upsert(current.copy(sensitivity = value))
    }

    suspend fun setVibration(enabled: Boolean) {
        val current = dao.getSettings() ?: SettingsEntity()
        dao.upsert(current.copy(vibrationEnabled = enabled))
    }

    suspend fun setSound(enabled: Boolean) {
        val current = dao.getSettings() ?: SettingsEntity()
        dao.upsert(current.copy(soundEnabled = enabled))
    }
}