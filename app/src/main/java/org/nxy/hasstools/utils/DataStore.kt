package org.nxy.hasstools.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// 定义 DataStore
private val Context.dataStore by preferencesDataStore(name = "store")

suspend fun saveData(store: DataStore<Preferences>, data: Map<String, String>) {
    store.edit { preferences ->
        for ((key, value) in data) {
            preferences[stringPreferencesKey(key)] = value
        }
    }
}

suspend fun saveData(context: Context, data: Map<String, String>) {
    saveData(context.dataStore, data)
}

suspend fun saveData(context: Context, key: String, value: String) {
    saveData(context, mapOf(key to value))
}

suspend fun saveJsonData(context: Context, key: String, value: Any) {
    val jsonString = Json.encodeToString(value)
    saveData(context, key, jsonString)
}

fun readStringData(context: Context, key: Preferences.Key<String>): String {
    val dataStore = context.dataStore
    var value: String?
    runBlocking {
        val preferences = dataStore.data.first()
        value = preferences[key]
    }
    return value ?: ""
}

fun readStringData(context: Context, key: String): String {
    return readStringData(context, stringPreferencesKey(key))
}

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
