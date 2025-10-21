package org.nxy.hasstools.objects

import android.net.NetworkCapabilities
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

enum class TransportType(val type: Int, val label: String) {
    CELLULAR(NetworkCapabilities.TRANSPORT_CELLULAR, "移动数据"),
    WIFI(NetworkCapabilities.TRANSPORT_WIFI, "无线网络"),
    BLUETOOTH(NetworkCapabilities.TRANSPORT_BLUETOOTH, "蓝牙网络"),
    ETHERNET(NetworkCapabilities.TRANSPORT_ETHERNET, "有线网络"),
    VPN(NetworkCapabilities.TRANSPORT_VPN, "VPN");
}

// 网络要求选项
@Serializable
enum class NetworkRequirement(
    val label: String,
    val description: String,
    val capabilities: Set<Int>
) {
    NONE("没有要求", "", emptySet()),
    INTERNET(
        "允许联网",
        "允许访问互联网但未经验证",
        setOf(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    ),
    VALIDATED(
        "确保联网",
        "允许访问互联网并确保可用",
        setOf(NetworkCapabilities.NET_CAPABILITY_INTERNET, NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    )
}

// 代理要求选项
@Serializable
enum class ProxyRequirement(
    val label: String,
    val description: String
) {
    NONE("没有要求", ""),
    REQUIRED("需要代理", "必须通过代理连接"),
    FORBIDDEN("禁止代理", "禁止使用代理连接")
}

/** 网络优先级整体配置 */
@Serializable
data class NetworkConfig(
    val enabled: Boolean = false,
    val items: List<NetworkPreference> = listOf(DefaultNetworkPreference())
)

/** 网络偏好项 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SerialName("NetworkPreference")
sealed class NetworkPreference(
    val networkId: String = "${System.nanoTime()}-${UUID.randomUUID()}"
)

/** 通用网络偏好项 */
@Serializable
@SerialName("Common")
data class CommonNetworkPreference(
    val transportType: TransportType? = null,
    val networkRequirement: NetworkRequirement = NetworkRequirement.VALIDATED,
    val proxyRequirement: ProxyRequirement = ProxyRequirement.NONE,
    val isNotMetered: Boolean = false
) : NetworkPreference()

/** VPN网络偏好项 */
@Serializable
@SerialName("Vpn")
data class VpnNetworkPreference(
    val networkRequirement: NetworkRequirement = NetworkRequirement.VALIDATED,
    val allowTransportTypes: Set<TransportType> = setOf(TransportType.WIFI, TransportType.CELLULAR),
    val isNotMetered: Boolean = false
) : NetworkPreference()

/** 默认网络偏好项 */
@Serializable
@SerialName("Default")
data class DefaultNetworkPreference(
    val enabled: Boolean = true
) : NetworkPreference()
