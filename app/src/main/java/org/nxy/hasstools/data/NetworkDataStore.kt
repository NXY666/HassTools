package org.nxy.hasstools.data

import androidx.datastore.preferences.core.stringPreferencesKey
import org.nxy.hasstools.objects.NetworkConfig

class NetworkDataStore() : BaseDataStore<NetworkConfig>(
    stringPreferencesKey("network_config"),
    NetworkConfig.serializer()
) {
    override fun getDefault(): NetworkConfig = NetworkConfig()
}
