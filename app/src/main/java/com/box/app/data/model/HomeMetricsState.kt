package com.box.app.data.model

import java.math.BigInteger

enum class IpMode {
    LAN,
    PUBLIC
}

data class SubscriptionItem(
    val name: String,
    val url: String,
    val expiryDate: String,
    val uploadBytes: BigInteger,
    val downloadBytes: BigInteger,
    val totalBytes: BigInteger,
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
    val subscriptionUsedBytes: BigInteger = BigInteger.ZERO,
    val subscriptionTotalBytes: BigInteger = BigInteger.ZERO,
    val subscriptionRemainBytes: BigInteger = BigInteger.ZERO,
    val subscriptionProgress: Float = 0f,
    val cpu: String = "-",
    val ram: String = "-"
)
