package org.nxy.hasstools.objects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.nxy.hasstools.data.LocationDataStore

class LocationViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore: LocationDataStore = LocationDataStore()
    private val _locationConfig: MutableStateFlow<LocationConfig> = MutableStateFlow(LocationConfig())
    val locationConfig: StateFlow<LocationConfig> = _locationConfig

    init {
        _locationConfig.value = dataStore.readData()
        viewModelScope.launch {
            dataStore.dataFlow.collectLatest { config ->
                _locationConfig.value = config
            }
        }
    }

    // 更新配置
    private fun updateLocationConfig(newConfig: LocationConfig) {
        _locationConfig.value = newConfig
        viewModelScope.launch {
            dataStore.saveData(newConfig)
        }
    }

    // 是否启用定位服务
    fun setEnabled(enabled: Boolean) {
        val updatedConfig = _locationConfig.value.copy(enabled = enabled)
        updateLocationConfig(updatedConfig)
    }

    // 是否启用网络触发
    fun setNetworkTriggerEnabled(enabled: Boolean) {
        val updatedConfig = _locationConfig.value.copy(networkTriggerEnabled = enabled)
        updateLocationConfig(updatedConfig)
    }

    // 设置融合定位提供者
    fun setFusedProvider(provider: FusedProvider) {
        val updatedConfig = _locationConfig.value.copy(fusedProvider = provider)
        updateLocationConfig(updatedConfig)
    }
}
