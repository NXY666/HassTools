package org.nxy.hasstools.objects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.nxy.hasstools.data.NetworkDataStore
import org.nxy.hasstools.utils.NetworkMonitor

class NetworkViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore: NetworkDataStore = NetworkDataStore()
    private val _networkConfig: MutableStateFlow<NetworkConfig> = MutableStateFlow(NetworkConfig())
    val networkConfig: StateFlow<NetworkConfig> = _networkConfig

    init {
        // 读取数据并保证默认网络偏好项存在且位于末尾
        val savedConfig = dataStore.readData()
        val normalizedConfig = normalizeConfig(savedConfig)
        if (normalizedConfig != savedConfig) {
            updateNetworkConfig(normalizedConfig)
        } else {
            _networkConfig.value = normalizedConfig
        }

        viewModelScope.launch {
            dataStore.dataFlow.collectLatest { config ->
                _networkConfig.value = normalizeConfig(config)
            }
        }
    }

    private fun updateNetworkConfig(newConfig: NetworkConfig) {
        val normalizedConfig = normalizeConfig(newConfig)
        _networkConfig.value = normalizedConfig
        viewModelScope.launch {
            dataStore.saveData(normalizedConfig)

            NetworkMonitor.reload()
            println("NetworkViewModel: Network config updated: $normalizedConfig")
        }
    }

    private fun normalizeConfig(config: NetworkConfig): NetworkConfig {
        val (customItems, defaultPreference) = splitPreferences(config.items)
        return config.copy(items = buildList {
            addAll(customItems)
            add(defaultPreference)
        })
    }

    private fun splitPreferences(items: List<NetworkPreference>): Pair<List<NetworkPreference>, DefaultNetworkPreference> {
        val defaultPreference = items.filterIsInstance<DefaultNetworkPreference>().lastOrNull()
        val customItems = items.filterNot { it is DefaultNetworkPreference }
        return customItems to (defaultPreference ?: DefaultNetworkPreference())
    }

    fun setEnabled(enabled: Boolean) {
        updateNetworkConfig(_networkConfig.value.copy(enabled = enabled))
    }

    fun addPreference(item: NetworkPreference) {
        val (customItems, defaultPreference) = splitPreferences(_networkConfig.value.items)
        val updatedItems = customItems + item
        updateNetworkConfig(_networkConfig.value.copy(items = updatedItems + defaultPreference))
    }

    fun updatePreference(id: String, item: NetworkPreference) {
        val (customItems, defaultPreference) = splitPreferences(_networkConfig.value.items)
        val updatedItems = customItems.map { if (it.networkId == id) item else it }
        updateNetworkConfig(_networkConfig.value.copy(items = updatedItems + defaultPreference))
    }

    fun removePreference(id: String) {
        val (customItems, defaultPreference) = splitPreferences(_networkConfig.value.items)
        val updatedItems = customItems.filterNot { it.networkId == id }
        if (updatedItems.size == customItems.size) {
            return
        }
        updateNetworkConfig(_networkConfig.value.copy(items = updatedItems + defaultPreference))
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        val (customItems, defaultPreference) = splitPreferences(_networkConfig.value.items)
        if (customItems.isEmpty() || fromIndex !in customItems.indices || toIndex !in customItems.indices) {
            return
        }
        if (fromIndex == toIndex) {
            return
        }
        val reordered = customItems.toMutableList().apply {
            val moved = removeAt(fromIndex)
            add(toIndex, moved)
        }
        updateNetworkConfig(_networkConfig.value.copy(items = reordered + defaultPreference))
    }

    fun setDefaultFallbackEnabled(enabled: Boolean) {
        val (customItems, defaultPreference) = splitPreferences(_networkConfig.value.items)
        if (defaultPreference.enabled == enabled) {
            return
        }
        val updatedDefault = DefaultNetworkPreference(enabled = enabled)
        updateNetworkConfig(_networkConfig.value.copy(items = customItems + updatedDefault))
    }
}
