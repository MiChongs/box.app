package com.box.app.data.model

enum class IpMode {
    LAN,
    PUBLIC
}

data class SubscriptionItem(
    val name: String,
    val url: String,
    val expiryDate: String,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val totalBytes: Long,
    val lastUpdatedAtMs: Long = 0L,
    val loading: Boolean = false
)

data class HomeMetricsState(
    val ipMode: IpMode = IpMode.LAN,
    val ip: String = "-",
    val lanIp: String = "-",
    val lanInterface: String = "-",
    val publicIp: String = "-",
    val publicCountry: String = "-",
    val publicCountryCode: String = "",
    val useClashApiForNetSpeed: Boolean = false,
    val netDown: String = "-",
    val netUp: String = "-",
    val netDownHistory: List<Float> = emptyList(),
    val netUpHistory: List<Float> = emptyList(),
    val netFastestDownSpeed: String = "-",
    val netFastestDownHost: String = "-",
    val netFastestDownChains: String = "-",
    val netFastestUpSpeed: String = "-",
    val netFastestUpHost: String = "-",
    val netFastestUpChains: String = "-",
    val latencyLoading: Boolean = false,
    val latencyBaiduMs: String = "-",
    val latencyCloudflareMs: String = "-",
    val latencyGoogleMs: String = "-",
    val latencyMs: String = "-",
    val latencyLabel: String = "-",
    val subscriptionCount: String = "-",
    val subscriptionSubtitle: String = "-",
    val subscriptionUrls: List<String> = emptyList(),
    val subscriptionItems: List<SubscriptionItem> = emptyList(),
    val subscriptionUsedBytes: Long = 0L,
    val subscriptionTotalBytes: Long = 0L,
    val subscriptionRemainBytes: Long = 0L,
    val subscriptionProgress: Float = 0f,
    val cpu: String = "-",
    val ram: String = "-"
)
