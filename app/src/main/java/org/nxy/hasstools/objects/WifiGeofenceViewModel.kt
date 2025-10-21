package org.nxy.hasstools.objects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.nxy.hasstools.data.WifiGeofenceDataStore

class WifiGeofenceViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore: WifiGeofenceDataStore = WifiGeofenceDataStore()
    private val _wifiGeofenceConfig: MutableStateFlow<WifiGeofenceConfig> = MutableStateFlow(WifiGeofenceConfig())
    val wifiGeofenceConfig: StateFlow<WifiGeofenceConfig> = _wifiGeofenceConfig

    init {
        _wifiGeofenceConfig.value = dataStore.readData()
        viewModelScope.launch {
            dataStore.dataFlow.collectLatest { config ->
                _wifiGeofenceConfig.value = config
            }
        }
    }

    private fun updateConfig(newConfig: WifiGeofenceConfig) {
        _wifiGeofenceConfig.value = newConfig
        viewModelScope.launch {
            dataStore.saveData(newConfig)
        }
    }

    fun setEnabled(enabled: Boolean) {
        val updatedConfig = _wifiGeofenceConfig.value.copy(enabled = enabled)
        updateConfig(updatedConfig)
    }

    fun addGeofenceItem(newItem: WifiGeofence) {
        val updatedItems = _wifiGeofenceConfig.value.items + newItem
        updateConfig(_wifiGeofenceConfig.value.copy(items = updatedItems))
    }

    fun removeGeofenceItem(item: WifiGeofence) {
        val updatedItems = _wifiGeofenceConfig.value.items - item
        updateConfig(_wifiGeofenceConfig.value.copy(items = updatedItems))
    }

    fun updateGeofenceItem(zoneId: String, updatedItem: WifiGeofence) {
        val updatedItems = _wifiGeofenceConfig.value.items.map {
            if (it.zoneId == zoneId) updatedItem else it
        }
        updateConfig(_wifiGeofenceConfig.value.copy(items = updatedItems))
    }

    fun setGeofenceItemEnabled(geofenceId: String, enabled: Boolean) {
        val updatedItems = _wifiGeofenceConfig.value.items.map {
            if (it.geofenceId == geofenceId) it.copy(enabled = enabled) else it
        }
        updateConfig(_wifiGeofenceConfig.value.copy(items = updatedItems))
    }
}
