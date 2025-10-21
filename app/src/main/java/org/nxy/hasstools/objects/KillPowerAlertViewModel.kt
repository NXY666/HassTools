package org.nxy.hasstools.objects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.nxy.hasstools.data.KillPowerAlertDataStore

class KillPowerAlertViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore: KillPowerAlertDataStore = KillPowerAlertDataStore()
    private val _killPowerAlertConfig: MutableStateFlow<KillPowerAlertConfig> = MutableStateFlow(KillPowerAlertConfig())
    val killPowerAlertConfig: StateFlow<KillPowerAlertConfig> = _killPowerAlertConfig

    init {
        _killPowerAlertConfig.value = dataStore.readData()
        viewModelScope.launch {
            dataStore.dataFlow.collectLatest { config ->
                _killPowerAlertConfig.value = config
            }
        }
    }

    // 更新配置
    private fun updateKillPowerAlertConfig(newConfig: KillPowerAlertConfig) {
        _killPowerAlertConfig.value = newConfig
        viewModelScope.launch {
            dataStore.saveData(newConfig)
        }
    }

    // 是否启用
    fun setEnabled(enabled: Boolean) {
        val updatedConfig = _killPowerAlertConfig.value.copy(enabled = enabled)
        updateKillPowerAlertConfig(updatedConfig)
    }
}
