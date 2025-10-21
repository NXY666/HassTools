package org.nxy.hasstools.objects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.nxy.hasstools.data.AmapDataStore
import org.nxy.hasstools.utils.amap.AMap

class AmapViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore: AmapDataStore = AmapDataStore()
    private val _amapConfig: MutableStateFlow<AmapConfig> = MutableStateFlow(AmapConfig())
    val amapConfig: StateFlow<AmapConfig> = _amapConfig

    init {
        _amapConfig.value = dataStore.readData()
        viewModelScope.launch {
            dataStore.dataFlow.collectLatest { config ->
                _amapConfig.value = config
            }
        }
    }

    // 更新配置
    private fun updateAmapConfig(newConfig: AmapConfig) {
        _amapConfig.value = newConfig
        viewModelScope.launch {
            dataStore.saveData(newConfig)
        }
    }

    // 设置运行时高德Key
    fun setRuntimeApiKey(runtimeApiKey: String) {
        val newConfig = _amapConfig.value.copy(runtimeApiKey = runtimeApiKey)
        updateAmapConfig(newConfig)

        AMap.refreshApiKey()
    }
}
