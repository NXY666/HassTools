package org.nxy.hasstools.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import org.nxy.hasstools.App
import org.nxy.hasstools.utils.Json

// 扩展 Context 以创建 DataStore
private val Context.dataStore by preferencesDataStore(name = "app_prefs")

abstract class BaseDataStore<T>(
    private val key: Preferences.Key<String>,
    private val serializer: KSerializer<T>
) {
    private val appContext = App.context // 只存 ApplicationContext，防止泄露

    // 读取数据，转换成对象
    val dataFlow: Flow<T> = appContext.dataStore.data
        .map { preferences ->
            preferences[key]?.let {
                runCatching { Json.decodeFromString(serializer, it) }.getOrNull()
            } ?: getDefault() // 如果为空，返回默认值
        }

    suspend fun readDataSync(): T =
        appContext.dataStore.data.first()[key]
            ?.let { runCatching { Json.decodeFromString(serializer, it) }.getOrNull() }
            ?: getDefault()

    fun readData(): T = runBlocking { readDataSync() }

    // 保存数据
    suspend fun saveData(data: T) {
        appContext.dataStore.edit { preferences ->
            preferences[key] = Json.encodeToString(serializer, data)
        }
    }

    fun saveDataSync(data: T) = runBlocking { saveData(data) }

    // 让子类实现默认值
    abstract fun getDefault(): T
}
