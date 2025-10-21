package org.nxy.hasstools.data

import androidx.datastore.preferences.core.stringPreferencesKey
import org.nxy.hasstools.objects.UserConfig

class UserDataStore() : BaseDataStore<UserConfig>(
    stringPreferencesKey("user_config"),
    UserConfig.serializer()
) {
    override fun getDefault(): UserConfig = UserConfig()
}
