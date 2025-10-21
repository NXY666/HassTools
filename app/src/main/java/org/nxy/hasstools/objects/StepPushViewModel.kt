package org.nxy.hasstools.objects

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.nxy.hasstools.data.StepPushDataStore
import java.time.Instant
import java.time.ZoneId

class StepPushViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore: StepPushDataStore = StepPushDataStore()
    private val _stepPushConfig: MutableStateFlow<StepPushConfig> = MutableStateFlow(StepPushConfig())
    val stepPushConfig: StateFlow<StepPushConfig> = _stepPushConfig

    init {
        _stepPushConfig.value = dataStore.readData()
        viewModelScope.launch {
            dataStore.dataFlow.collectLatest { config ->
                _stepPushConfig.value = config
            }
        }
    }

    // 更新配置
    private fun updateStepPushConfig(newConfig: StepPushConfig) {
        _stepPushConfig.value = newConfig
        viewModelScope.launch {
            dataStore.saveData(newConfig)
        }
    }

    // 是否启用
    fun setEnabled(enabled: Boolean) {
        val updatedConfig = _stepPushConfig.value.copy(enabled = enabled)
        updateStepPushConfig(updatedConfig)
    }

    // 设置上次步数和时间
    fun setLastStepRecord(lastCount: Long, lastTime: Instant, lastZoneId: ZoneId) {
        val updatedConfig = _stepPushConfig.value.copy(
            lastStepCount = lastCount,
            lastRecordTimestamp = lastTime.toEpochMilli(),
            lastRecordZoneId = lastZoneId.id
        )
        updateStepPushConfig(updatedConfig)
    }
}
