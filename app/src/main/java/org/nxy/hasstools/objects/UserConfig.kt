package org.nxy.hasstools.objects

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UserConfig(
    val items: List<User> = emptyList(),
) {
    fun hasEnabledUser(): Boolean {
        return items.any { it.enabled }
    }
}

@Serializable
data class User(
    val enabled: Boolean = true,
    val userId: String = "${System.nanoTime()}-${UUID.randomUUID()}",
    val userType: String,
    val userName: String = "",
    val url: String = "http://homeassistant.local:8123",
    val token: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val webhookId: String = "",
) {
    companion object {
        const val REGISTER_USER_TYPE = "register"

        const val BIND_USER_TYPE = "bind"
    }
}
