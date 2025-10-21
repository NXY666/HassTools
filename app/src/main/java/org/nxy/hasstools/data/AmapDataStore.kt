package org.nxy.hasstools.data

import androidx.datastore.preferences.core.stringPreferencesKey
import org.nxy.hasstools.objects.AmapConfig

class AmapDataStore : BaseDataStore<AmapConfig>(
    stringPreferencesKey("amap_config"),
    AmapConfig.serializer()
) {
    override fun getDefault(): AmapConfig = AmapConfig()
}
