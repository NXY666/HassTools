package org.nxy.hasstools.data

import androidx.datastore.preferences.core.stringPreferencesKey
import org.nxy.hasstools.objects.LocationConfig

class LocationDataStore() : BaseDataStore<LocationConfig>(
    stringPreferencesKey("location_config"),
    LocationConfig.serializer()
) {
    override fun getDefault(): LocationConfig = LocationConfig()
}
