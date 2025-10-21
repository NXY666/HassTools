package org.nxy.hasstools.objects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.nxy.hasstools.data.AppDataStore

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore: AppDataStore = AppDataStore()
    private val _appConfig: MutableStateFlow<AppConfig> = MutableStateFlow(AppConfig())
    val appConfig: StateFlow<AppConfig> = _appConfig

    init {
        _appConfig.value = dataStore.readData()
        viewModelScope.launch {
            dataStore.dataFlow.collectLatest { config ->
                _appConfig.value = config
            }
        }
    }

    // 更新配置
    private fun updateAppConfig(newConfig: AppConfig) {
        _appConfig.value = newConfig
        viewModelScope.launch {
            dataStore.saveData(newConfig)
        }
    }

    // 是否启用
    fun setTheme(theme: String) {
        val newConfig = _appConfig.value.copy(theme = theme)
        updateAppConfig(newConfig)
    }
}
