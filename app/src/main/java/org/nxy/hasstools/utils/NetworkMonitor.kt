package org.nxy.hasstools.utils

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import okhttp3.OkHttpClient
import org.nxy.hasstools.App
import org.nxy.hasstools.data.NetworkDataStore
import org.nxy.hasstools.objects.CommonNetworkPreference
import org.nxy.hasstools.objects.DefaultNetworkPreference
import org.nxy.hasstools.objects.NetworkConfig
import org.nxy.hasstools.objects.NetworkPreference
import org.nxy.hasstools.objects.ProxyRequirement
import org.nxy.hasstools.objects.VpnNetworkPreference
import java.net.Proxy

class NetworkMonitor(private val config: NetworkConfig) {
    private val connectivityManager = App.context.getSystemService(ConnectivityManager::class.java)

    // 存储每个网络偏好对应的可用网络列表
    private val availableNetworks = mutableMapOf<String, MutableList<Network>>()

    // 存储每个网络偏好的回调
    private val networkCallbacks = mutableMapOf<String, ConnectivityManager.NetworkCallback>()

    init {
        // 在构造时初始化网络监听
        if (config.enabled) {
            // 为自定义网络偏好创建并注册回调，默认网络单独处理
            config.items.filterNot { it is DefaultNetworkPreference }.forEach { preference ->
                registerCallbackForPreference(preference)
            }
            config.items.filterIsInstance<DefaultNetworkPreference>().firstOrNull()?.let { preference ->
                registerCallbackForPreference(preference)
            }
        }
        println("NetworkMonitor initialized with config: $config")
    }

    /**
     * 为指定的网络偏好创建并注册网络回调
     */
    private fun registerCallbackForPreference(preference: NetworkPreference) {
        if (preference is DefaultNetworkPreference && !preference.enabled) {
            println("Skipping callback for disabled DefaultNetworkPreference: ${preference.networkId}")
            return
        }

        val networkList = mutableListOf<Network>()

        // TODO
        println(
            when (preference) {
                is DefaultNetworkPreference -> {
                    "Registering callback for DefaultNetworkPreference: ${preference.networkId}"
                }

                is CommonNetworkPreference -> {
                    "Registering callback for CommonNetworkPreference: ${preference.networkId}, TransportType: ${preference.transportType?.label ?: "Any"}, NetworkRequirement: ${preference.networkRequirement.label}, ProxyRequirement: ${preference.proxyRequirement.label}, NotMetered: ${preference.isNotMetered}"
                }

                is VpnNetworkPreference -> {
                    "Registering callback for VpnNetworkPreference: ${preference.networkId}, NetworkRequirement: ${preference.networkRequirement.label}, AllowTransportTypes: ${if (preference.allowTransportTypes.isEmpty()) "Any" else preference.allowTransportTypes.joinToString { it.label }}, NotMetered: ${preference.isNotMetered}"
                }
            }
        )

        val callback = object : ConnectivityManager.NetworkCallback() {
            @Synchronized
            override fun onAvailable(network: Network) {
                // TODO
                var logMessage = "Network available: ${preference.networkId} $network"
                val caps = connectivityManager.getNetworkCapabilities(network)
                caps?.let {
                    logMessage += if (it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        ", Type: WIFI"
                    } else if (it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                        ", Type: CELLULAR"
                    } else if (it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        ", Type: VPN"
                    } else if (it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                        ", Type: ETHERNET"
                    } else {
                        ""
                    }
                    logMessage += if (it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        ", Has VPN"
                    } else {
                        ""
                    }
                    logMessage += ", Capabilities: ${
                        it.capabilities.joinToString(", ") { cap ->
                            when (cap) {
                                NetworkCapabilities.NET_CAPABILITY_MMS -> "MMS"
                                NetworkCapabilities.NET_CAPABILITY_SUPL -> "SUPL"
                                NetworkCapabilities.NET_CAPABILITY_DUN -> "DUN"
                                NetworkCapabilities.NET_CAPABILITY_FOTA -> "FOTA"
                                NetworkCapabilities.NET_CAPABILITY_IMS -> "IMS"
                                NetworkCapabilities.NET_CAPABILITY_CBS -> "CBS"
                                NetworkCapabilities.NET_CAPABILITY_WIFI_P2P -> "WIFI_P2P"
                                NetworkCapabilities.NET_CAPABILITY_IA -> "IA"
                                NetworkCapabilities.NET_CAPABILITY_RCS -> "RCS"
                                NetworkCapabilities.NET_CAPABILITY_XCAP -> "XCAP"
                                NetworkCapabilities.NET_CAPABILITY_EIMS -> "EIMS"
                                NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED -> "NOT_RESTRICTED"
                                NetworkCapabilities.NET_CAPABILITY_TRUSTED -> "TRUSTED"
                                NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING -> "NOT_ROAMING"
                                NetworkCapabilities.NET_CAPABILITY_INTERNET -> "INTERNET"
                                NetworkCapabilities.NET_CAPABILITY_NOT_METERED -> "NOT_METERED"
                                NetworkCapabilities.NET_CAPABILITY_NOT_VPN -> "NOT_VPN"
                                NetworkCapabilities.NET_CAPABILITY_VALIDATED -> "VALIDATED"
                                NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL -> "CAPTIVE_PORTAL"
                                NetworkCapabilities.NET_CAPABILITY_FOREGROUND -> "FOREGROUND"
                                NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED -> "NOT_CONGESTED"
                                NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED -> "NOT_SUSPENDED"
                                else -> "OTHER($cap)"
                            }
                        }
                    }"
                    println(logMessage)
                }

                if (!networkList.contains(network) && isNetworkValidForPreference(network, preference)) {
                    networkList.add(0, network)
                } else {
                    println("Network available but not valid or already tracked: ${preference.networkId} $network")
                }
            }

            @Synchronized
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)

                if (networkList.contains(network)) {
                    if (!isNetworkValidForPreference(network, preference)) {
                        networkList.remove(network)
                        println("Network capabilities changed and no longer valid: ${preference.networkId} $network")
                    }
                } else {
                    if (isNetworkValidForPreference(network, preference)) {
                        networkList.add(0, network)
                        println("Network capabilities changed and now valid: ${preference.networkId} $network")
                    }
                }
            }

            @Synchronized
            override fun onLost(network: Network) {
                networkList.remove(network)
                println("Network lost: ${preference.networkId} $network")
            }
        }

        availableNetworks[preference.networkId] = networkList
        networkCallbacks[preference.networkId] = callback

        try {
            if (preference is DefaultNetworkPreference) {
                // 系统默认网络：不加任何条件
                connectivityManager.registerDefaultNetworkCallback(callback)
            } else {
                // 自定义网络偏好：根据配置创建 NetworkRequest
                val request = createNetworkRequest(preference)
                connectivityManager.registerNetworkCallback(request, callback)
            }
        } catch (_: Exception) {
            // 处理注册失败的情况
            availableNetworks.remove(preference.networkId)
            networkCallbacks.remove(preference.networkId)
        }
    }

    /**
     * 根据网络偏好创建 NetworkRequest
     */
    private fun createNetworkRequest(preference: NetworkPreference): NetworkRequest {
        val builder = NetworkRequest.Builder()
        when (preference) {
            is DefaultNetworkPreference -> {
                throw IllegalArgumentException("DefaultNetworkPreference does not require a NetworkRequest")
            }

            is CommonNetworkPreference -> {
                // 添加传输类型
                preference.transportType?.let { transportType ->
                    builder.addTransportType(transportType.type)
                }

                // 添加网络要求的能力
                preference.networkRequirement.capabilities.forEach { capability ->
                    builder.addCapability(capability)
                }

                // 处理代理设置：Android 默认会添加 NOT_VPN，如果允许代理则需要显式移除
                when (preference.proxyRequirement) {
                    ProxyRequirement.NONE, ProxyRequirement.REQUIRED -> {
                        builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    }

                    ProxyRequirement.FORBIDDEN -> {
                    }
                }

                // 添加非计费网络要求
                if (preference.isNotMetered) {
                    builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }
            }

            is VpnNetworkPreference -> {
                // 添加传输类型
                preference.allowTransportTypes.forEach { transportType ->
                    builder.addTransportType(transportType.type)
                }

                // VPN 网络默认会有 TRANSPORT_VPN，需要移除 NOT_VPN
                builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)

                // 添加网络要求的能力
                preference.networkRequirement.capabilities.forEach { capability ->
                    builder.addCapability(capability)
                }

                // 添加非计费网络要求
                if (preference.isNotMetered) {
                    builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                }
            }
        }

        return builder.build()
    }

    /**
     * 停止所有网络监听
     */
    fun stop() {
        synchronized(availableNetworks) {
            networkCallbacks.values.forEach { callback ->
                try {
                    connectivityManager.unregisterNetworkCallback(callback)
                } catch (_: Exception) {
                }
            }
            networkCallbacks.clear()
            availableNetworks.clear()
        }
        println("NetworkMonitor stopped")
    }

    /**
     * 检查网络是否满足指定偏好的要求
     */
    private fun isNetworkValidForPreference(network: Network?, preference: NetworkPreference): Boolean {
        network ?: return false

        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        when (preference) {
            is DefaultNetworkPreference -> {
                return preference.enabled
            }

            is CommonNetworkPreference -> {
                // 普通网络不能是 VPN
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return false

                // 包含指定的传输类型
                if (preference.transportType == null || !capabilities.hasTransport(preference.transportType.type)) {
                    return false
                }

                // 检查代理设置
                when (preference.proxyRequirement) {
                    ProxyRequirement.NONE -> {
                        // 不做任何检查
                    }

                    ProxyRequirement.REQUIRED -> {
                        // 需要代理：网络不能有 NOT_VPN 能力
                        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) return false
                    }

                    ProxyRequirement.FORBIDDEN -> {
                        // 禁止代理：网络必须有 NOT_VPN 能力
                        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) return false
                    }
                }

                // 检查非计费网络要求
                if (preference.isNotMetered
                    && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                ) {
                    return false
                }
            }

            is VpnNetworkPreference -> {
                // VPN 网络必须有 TRANSPORT_VPN
                if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return false

                // 检查非计费网络要求
                if (preference.isNotMetered
                    && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                ) {
                    return false
                }

                // 检查网络要求的能力
                preference.networkRequirement.capabilities.forEach { capability ->
                    if (!capabilities.hasCapability(capability)) {
                        return false
                    }
                }

                // 检查包不包含指定的传输类型
                var hasAllowedTransport = preference.allowTransportTypes.isEmpty()
                preference.allowTransportTypes.forEach { transportType ->
                    if (capabilities.hasTransport(transportType.type)) {
                        hasAllowedTransport = true
                    }
                }
                if (!hasAllowedTransport) return false
            }
        }
        return true
    }

    fun getNetwork(): Network? {
        if (!config.enabled) {
            // 如果配置未启用，只返回系统默认网络
            connectivityManager.activeNetwork?.let { return it }
        }

        synchronized(availableNetworks) {
            val defaultPreference = config.items.filterIsInstance<DefaultNetworkPreference>().lastOrNull()
            config.items.filterNot { it is DefaultNetworkPreference }.forEach { preference ->
                availableNetworks[preference.networkId]?.forEach { network ->
                    if (isNetworkValidForPreference(network, preference)) {
                        return network
                    }
                }
            }

            if (defaultPreference?.enabled == true) {
                availableNetworks[defaultPreference.networkId]?.forEach { network ->
                    return network
                }
                connectivityManager.activeNetwork?.let { network ->
                    return network
                }
            }
        }

        return null
    }

    companion object {
        @Volatile
        private var instance: NetworkMonitor? = null

        @Volatile
        private var lastHttpClient: OkHttpClient? = null

        private val defaultHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder().build()
        }

        @Volatile
        private var lastHandle: Long? = null

        fun load() {
            if (instance == null) {
                println("NetworkMonitor reloading with new config: ${NetworkDataStore().readData()}")
                instance = NetworkMonitor(NetworkDataStore().readData())
            } else {
                println("NetworkMonitor already loaded")
            }
        }

        @Synchronized
        fun unload() {
            instance?.stop()
            instance = null

            // 强制下次重建
            lastHttpClient = null
            lastHandle = null

            println("NetworkMonitor unloaded")
        }

        fun reload() {
            unload()

            load()
        }

        fun getNetwork(): Network? {
            return instance?.getNetwork()
        }

        @Synchronized
        fun getHttpClient(): OkHttpClient {
            val pickedNetwork = getNetwork()

            if (pickedNetwork == null) {
                println("HttpClient using default network")
                return defaultHttpClient
            }

            var httpClient = lastHttpClient ?: defaultHttpClient

            val handle = pickedNetwork.networkHandle

            if (handle == lastHandle) {
                println("HttpClient reusing existing client for network: $pickedNetwork")
                return httpClient
            }

            httpClient = httpClient.newBuilder().apply {
                socketFactory(pickedNetwork.socketFactory)
                dns { host -> pickedNetwork.getAllByName(host).toList() }

                // eventListenerFactory { VerboseEventListener() }

                val connectivityManager = App.context.getSystemService(ConnectivityManager::class.java)
                connectivityManager.getNetworkCapabilities(pickedNetwork)?.let {
                    if (it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                        // 普通网络，走直连
                        proxy(Proxy.NO_PROXY)
                        println("HttpClient configured for non-VPN network: $pickedNetwork")
                    }
                }
            }.build()

            lastHttpClient = httpClient
            lastHandle = handle

            println("HttpClient switched to network: $pickedNetwork")

            return httpClient
        }
    }
}
