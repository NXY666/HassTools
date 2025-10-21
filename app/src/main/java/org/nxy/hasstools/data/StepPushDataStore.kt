package org.nxy.hasstools.data

import androidx.datastore.preferences.core.stringPreferencesKey
import org.nxy.hasstools.objects.StepPushConfig

class StepPushDataStore() : BaseDataStore<StepPushConfig>(
    stringPreferencesKey("step_config"),
    StepPushConfig.serializer()
) {
    override fun getDefault(): StepPushConfig = StepPushConfig()
}
