package org.nxy.hasstools.objects

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val theme: String = "blue",
)
