package org.nxy.hasstools.objects

import kotlinx.serialization.Serializable

@Serializable
data class AmapConfig(
    val runtimeApiKey: String = ""
)
