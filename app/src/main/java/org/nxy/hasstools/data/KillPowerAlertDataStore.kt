package org.nxy.hasstools.data

import androidx.datastore.preferences.core.stringPreferencesKey
import org.nxy.hasstools.objects.KillPowerAlertConfig

class KillPowerAlertDataStore() : BaseDataStore<KillPowerAlertConfig>(
    stringPreferencesKey("kill_power_alert_config"),
    KillPowerAlertConfig.serializer()
) {
    override fun getDefault(): KillPowerAlertConfig = KillPowerAlertConfig()
}
