package org.nxy.hasstools.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "store")

/**
 * 保存数据到 DataStore。
 *
 * @param store DataStore 实例
 * @param data 要保存的键值对数据
 */
suspend fun saveData(store: DataStore<Preferences>, data: Map<String, String>) {
    store.edit { preferences ->
        for ((key, value) in data) {
            preferences[stringPreferencesKey(key)] = value
        }
    }
}

/**
 * 保存数据到 Context 关联的 DataStore。
 *
 * @param context 应用上下文
 * @param data 要保存的键值对数据
 */
suspend fun saveData(context: Context, data: Map<String, String>) {
    saveData(context.dataStore, data)
}

/**
 * 保存单个键值对数据到 DataStore。
 *
 * @param context 应用上下文
 * @param key 数据键
 * @param value 数据值
 */
suspend fun saveData(context: Context, key: String, value: String) {
    saveData(context, mapOf(key to value))
}

/**
 * 将对象序列化为 JSON 并保存到 DataStore。
 *
 * @param context 应用上下文
 * @param key 数据键
 * @param value 要序列化的对象
 */
suspend fun saveJsonData(context: Context, key: String, value: Any) {
    val jsonString = Json.encodeToString(value)
    saveData(context, key, jsonString)
}

/**
 * 从 DataStore 读取字符串数据（同步方式）。
 *
 * @param context 应用上下文
 * @param key 数据键
 * @return 数据值，不存在时返回空字符串
 */
fun readStringData(context: Context, key: Preferences.Key<String>): String {
    val dataStore = context.dataStore
    var value: String?
    runBlocking {
        val preferences = dataStore.data.first()
        value = preferences[key]
    }
    return value ?: ""
}

/**
 * 从 DataStore 读取字符串数据（同步方式）。
 *
 * @param context 应用上下文
 * @param key 数据键字符串
 * @return 数据值，不存在时返回空字符串
 */
fun readStringData(context: Context, key: String): String {
    return readStringData(context, stringPreferencesKey(key))
}

/**
 * 从 DataStore 读取 JSON 数据并反序列化为对象。
 *
 * @param T 目标类型
 * @param context 应用上下文
 * @param key 数据键
 * @param defaultValue 反序列化失败或数据不存在时的默认值提供函数
 * @return 反序列化后的对象或默认值
 */
inline fun <reified T> readJsonData(context: Context, key: String, defaultValue: () -> T): T {
    val data = readStringData(context, key)
    return try {
        if (data.isEmpty()) {
            defaultValue()
        } else {
            Json.decodeFromString<T>(data)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        defaultValue()
    }
}
