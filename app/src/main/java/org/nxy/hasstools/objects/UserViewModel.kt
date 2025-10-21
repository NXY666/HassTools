package org.nxy.hasstools.objects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.nxy.hasstools.data.UserDataStore

class UserViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore: UserDataStore = UserDataStore()
    private val _userConfig: MutableStateFlow<UserConfig> = MutableStateFlow(UserConfig())
    val userConfig: StateFlow<UserConfig> = _userConfig

    init {
        _userConfig.value = dataStore.readData()
        viewModelScope.launch {
            dataStore.dataFlow.collectLatest { config ->
                _userConfig.value = config
            }
        }
    }

    // 更新配置
    private fun updateUserConfig(newConfig: UserConfig) {
        _userConfig.value = newConfig
        viewModelScope.launch {
            dataStore.saveData(newConfig)
        }
    }

    // 添加 UserItem
    fun addUserItem(newItem: User) {
        val updatedItems = _userConfig.value.items + newItem
        updateUserConfig(_userConfig.value.copy(items = updatedItems))
    }

    // 删除 UserItem
    fun removeUserItem(item: User) {
        val updatedItems = _userConfig.value.items - item
        updateUserConfig(_userConfig.value.copy(items = updatedItems))
    }

    fun updateUserItem(updatedItem: User) {
        val updatedItems = _userConfig.value.items.map {
            if (it.userId == updatedItem.userId) updatedItem else it
        }
        updateUserConfig(_userConfig.value.copy(items = updatedItems))
    }

    fun setEnabled(userId: String, enabled: Boolean) {
        val updatedItems = _userConfig.value.items.map {
            if (it.userId == userId) it.copy(enabled = enabled) else it
        }
        updateUserConfig(_userConfig.value.copy(items = updatedItems))
    }
}
