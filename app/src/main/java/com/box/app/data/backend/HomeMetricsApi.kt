package com.box.app.data.backend

import com.box.app.BuildConfig
import com.box.app.data.model.LatencyResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.concurrent.TimeUnit
import java.net.URLDecoder
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object HomeMetricsApi {

    data class LanIpInfo(
        val ip: String,
        val iface: String
    )

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
    }

    suspend fun getLanIp(): String {
        return getLanIpInfo().ip
    }

    suspend fun getLanIpInfo(): LanIpInfo {
        return try {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return LanIpInfo(ip = "-", iface = "-")
            for (iface in ifaces.toList()) {
                if (!iface.isUp || iface.isLoopback) continue
                val ip = iface.inetAddresses.toList()
                    .asSequence()
                    .filterIsInstance<Inet4Address>()
                    .mapNotNull { it.hostAddress }
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                if (ip.isNotBlank()) {
                    val ifaceName = iface.name?.takeIf { it.isNotBlank() } ?: "-"
                    return LanIpInfo(ip = ip, iface = ifaceName)
                }
            }
            LanIpInfo(ip = "-", iface = "-")
        } catch (_: Exception) {
            LanIpInfo(ip = "-", iface = "-")
        }
    }

    suspend fun getSubscriptionUrlsRaw(): ShellExecutor.Result {
        val key = if (BuildConfig.FLAVOR == "bfr") "subscription_url_clash" else "subscription_url_mihomo"
        return ShellExecutor.execute("grep '^${key}=' /data/adb/box/settings.ini | cut -d '=' -f 2-")
    }

    data class SubscriptionFlowInfo(
        val uploadBytes: Long,
        val downloadBytes: Long,
        val totalBytes: Long,
        val expireEpochSec: Long,
        val title: String? = null
    ) {
        val usedBytes: Long get() = uploadBytes + downloadBytes
        val remainBytes: Long get() = (totalBytes - usedBytes).coerceAtLeast(0L)
    }

    suspend fun getSubscriptionFlowInfo(url: String): SubscriptionFlowInfo? {
        return fetchSubscriptionFlowInfoInternal(url = url, redirectsLeft = 3, bestTitle = null)
    }

    private fun resolveRedirectUrl(base: HttpUrl, location: String): String? {
        val trimmed = location.trim()
        if (trimmed.isBlank()) return null
        // Absolute URL
        trimmed.toHttpUrlOrNull()?.let { return it.toString() }
        // Relative URL
        return base.resolve(trimmed)?.toString()
    }

    private fun fetchSubscriptionFlowInfoInternal(
        url: String,
        redirectsLeft: Int,
        bestTitle: String?
    ): SubscriptionFlowInfo? {
        return try {
            // Prefer HEAD (some providers only return header for HEAD/fast responses)
            val headReq = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "clash")
                .header("Accept", "*/*")
                .header("Cache-Control", "no-cache")
                .build()

            okHttpClient.newCall(headReq).execute().use { resp ->
                val header = resp.headers("subscription-userinfo").lastOrNull().orEmpty()
                val title = parseSubscriptionTitleFromResponse(resp) ?: bestTitle
                val parsed = parseSubscriptionUserInfo(header)
                if (parsed != null) return parsed.copy(title = title)

                val code = resp.code
                val location = resp.header("Location").orEmpty()
                if (redirectsLeft > 0 && code in 300..399 && location.isNotBlank()) {
                    val next = resolveRedirectUrl(resp.request.url, location) ?: return null
                    return fetchSubscriptionFlowInfoInternal(next, redirectsLeft - 1, title)
                }
            }

            // Fallback to GET, but avoid downloading large bodies.
            val getReq = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "clash")
                .header("Accept", "*/*")
                .header("Cache-Control", "no-cache")
                .header("Range", "bytes=0-0")
                .build()

            okHttpClient.newCall(getReq).execute().use { resp ->
                val header = resp.headers("subscription-userinfo").lastOrNull().orEmpty()
                val title = parseSubscriptionTitleFromResponse(resp) ?: bestTitle
                val parsed = parseSubscriptionUserInfo(header)
                if (parsed != null) return parsed.copy(title = title)

                val code = resp.code
                val location = resp.header("Location").orEmpty()
                if (redirectsLeft > 0 && code in 300..399 && location.isNotBlank()) {
                    val next = resolveRedirectUrl(resp.request.url, location) ?: return null
                    return fetchSubscriptionFlowInfoInternal(next, redirectsLeft - 1, title)
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSubscriptionTitleFromResponse(response: okhttp3.Response): String? {
        val profileTitle = response.header("profile-title")?.trim().orEmpty()
        if (profileTitle.isNotBlank()) return profileTitle

        val contentDisposition = response.header("Content-Disposition")?.trim().orEmpty()
        if (contentDisposition.isBlank()) return null

        // Try RFC 5987: filename*=UTF-8''...
        val utf8Pattern = """filename\*=UTF-8''([^;\s]+)""".toRegex(RegexOption.IGNORE_CASE)
        val utf8Match = utf8Pattern.find(contentDisposition)
        if (utf8Match != null) {
            val encoded = utf8Match.groupValues[1].trim()
            return try {
                URLDecoder.decode(encoded, "UTF-8")
            } catch (_: Exception) {
                encoded
            }
        }

        // filename="..."
        val standardPattern = """filename=\"([^\"]+)\""".toRegex(RegexOption.IGNORE_CASE)
        val standardMatch = standardPattern.find(contentDisposition)
        if (standardMatch != null) return standardMatch.groupValues[1].trim().takeIf { it.isNotBlank() }

        // filename=xxx
        val unquotedPattern = """filename=([^;\s]+)""".toRegex(RegexOption.IGNORE_CASE)
        val unquotedMatch = unquotedPattern.find(contentDisposition)
        if (unquotedMatch != null) return unquotedMatch.groupValues[1].trim().takeIf { it.isNotBlank() }

        return null
    }

    fun parseSubscriptionUserInfo(header: String): SubscriptionFlowInfo? {
        if (header.isBlank()) return null
        // upload=123; download=456; total=789; expire=1693728000
        var upload = 0L
        var download = 0L
        var total = 0L
        var expire = 0L
        header.split(";").map { it.trim() }.forEach { part ->
            val kv = part.split("=")
            if (kv.size != 2) return@forEach
            val k = kv[0].trim().lowercase(Locale.getDefault())
            val v = kv[1].trim().toLongOrNull() ?: 0L
            when (k) {
                "upload" -> upload = v
                "download" -> download = v
                "total" -> total = v
                "expire" -> expire = v
            }
        }
        if (total <= 0L) return null
        return SubscriptionFlowInfo(uploadBytes = upload, downloadBytes = download, totalBytes = total, expireEpochSec = expire, title = null)
    }

    fun formatBytes(bytes: Long): String {
        val b = bytes.coerceAtLeast(0L).toDouble()
        val kb = b / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        val tb = gb / 1024.0
        val pb = tb / 1024.0
        return when {
            pb >= 1.0 -> String.format("%.2f PB", pb)
            tb >= 1.0 -> String.format("%.2f TB", tb)
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.0f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> String.format("%d B", bytes.coerceAtLeast(0L))
        }
    }

    fun formatExpireDate(epochSec: Long): String {
        if (epochSec <= 0L) return "-"
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.format(Date(epochSec * 1000))
        } catch (_: Exception) {
            "-"
        }
    }

    data class PublicGeoIpInfo(
        val ip: String,
        val country: String,
        val countryCode: String,
        val asn: String,
        val asnOrganization: String,
        val isp: String
    )

    data class PublicIpSummary(
        val ip: String,
        val country: String,
        val countryCode: String
    )

    private fun fetchPublicGeoIp(endpoint: String): PublicGeoIpInfo? {
        return try {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "BoxApp/Android")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) return null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val asnValue = json.opt("asn")
                PublicGeoIpInfo(
                    ip = json.optString("ip", "-").ifBlank { "-" },
                    country = json.optString("country", "-").ifBlank { "-" },
                    countryCode = json.optString("country_code", "-").ifBlank { "-" }.uppercase(Locale.getDefault()),
                    asn = when (asnValue) {
                        null -> "-"
                        is Number -> asnValue.toLong().toString()
                        else -> asnValue.toString().ifBlank { "-" }
                    },
                    asnOrganization = json.optString("asn_organization", "-").ifBlank { "-" },
                    isp = json.optString("isp", "-").ifBlank { "-" }
                )
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getPublicGeoIp(isIpv6: Boolean): PublicGeoIpInfo? {
        val endpoint = if (isIpv6) "https://api-ipv6.ip.sb/geoip" else "https://api-ipv4.ip.sb/geoip"
        return fetchPublicGeoIp(endpoint)
    }

    suspend fun getPublicIp(): Pair<String, String> {
        val s = getPublicIpSummary()
        return s.ip to s.countryCode
    }

    suspend fun getPublicIpSummary(): PublicIpSummary {
        val info = fetchPublicGeoIp("https://api-ipv4.ip.sb/geoip")
        return if (info != null) {
            PublicIpSummary(
                ip = info.ip,
                country = info.country,
                countryCode = info.countryCode
            )
        } else {
            PublicIpSummary(
                ip = "N/A",
                country = "-",
                countryCode = ""
            )
        }
    }

    fun parseBashArray(raw: String): List<String> {
        val s = raw.trim()
        if (s.isBlank()) return emptyList()

        // Examples:
        // ("a" "b")
        // "a"
        // a
        val cleaned = s.removePrefix("(").removeSuffix(")").trim()
        if (cleaned.isBlank()) return emptyList()

        val out = mutableListOf<String>()
        val r = "\"([^\"]+)\"".toRegex()
        val matches = r.findAll(cleaned).toList()
        if (matches.isNotEmpty()) {
            matches.forEach { out.add(it.groupValues[1]) }
            return out
        }

        cleaned.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotBlank() }.forEach { out.add(it) }
        return out
    }

    suspend fun measureLatency(url: String): LatencyResult {
        return try {
            val req = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "BoxApp/Latency")
                .header("Range", "bytes=0-0")
                .build()

            val start = System.nanoTime()
            okHttpClient.newCall(req).execute().use { resp ->
                val code = resp.code
                if (code > 0) {
                    val end = System.nanoTime()
                    LatencyResult.Success((end - start) / 1_000_000)
                } else {
                    LatencyResult.NotAvailable
                }
            }
        } catch (_: Exception) {
            LatencyResult.NotAvailable
        }
    }
}
