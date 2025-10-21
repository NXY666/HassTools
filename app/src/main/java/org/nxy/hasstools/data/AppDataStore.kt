package org.nxy.hasstools.data

import androidx.datastore.preferences.core.stringPreferencesKey
import org.nxy.hasstools.objects.AppConfig

class AppDataStore() : BaseDataStore<AppConfig>(
    stringPreferencesKey("app_config"),
    AppConfig.serializer()
) {
    override fun getDefault(): AppConfig = AppConfig()
}
