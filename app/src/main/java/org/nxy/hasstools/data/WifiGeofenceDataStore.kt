package org.nxy.hasstools.data

import androidx.datastore.preferences.core.stringPreferencesKey
import org.nxy.hasstools.objects.WifiGeofenceConfig

class WifiGeofenceDataStore : BaseDataStore<WifiGeofenceConfig>(
    stringPreferencesKey("wifi_geofence_config"),
    WifiGeofenceConfig.serializer()
) {
    override fun getDefault(): WifiGeofenceConfig = WifiGeofenceConfig()
}
