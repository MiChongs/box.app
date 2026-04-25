package com.box.app.data.backend

import com.box.app.BuildConfig
import com.box.app.data.model.LatencyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    private data class LanIpCandidate(
        val iface: String,
        val ip: String
    )

    data class LanIpInfo(
        val ip: String,
        val iface: String
    )

    private val ignoredLanIfacePrefixes = listOf(
        "lo", "tun", "tap", "wg", "utun", "tailscale", "docker", "veth",
        "ifb", "dummy", "sit", "ip6tnl", "gre", "gretap", "erspan", "virbr", "zt"
    )

    private val preferredLanIfacePrefixes = listOf(
        "wlan", "wifi", "wl", "eth", "en", "ap", "br", "bond", "usb",
        "rndis", "rmnet", "ccmni", "v4-rmnet"
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
        getLanIpInfoFromShell()?.let { return it }
        return getLanIpInfoFromNetworkInterfaces()
    }

    private suspend fun getLanIpInfoFromShell(): LanIpInfo? {
        return try {
            val preferredIface = ShellExecutor.execute(
                """
                if command -v ip >/dev/null 2>&1; then
                  (ip route get 1.1.1.1 2>/dev/null || ip -4 route show default 2>/dev/null) \
                    | awk '{for (i = 1; i <= NF; i++) if (${ '$' }i == "dev") { print ${ '$' }(i+1); exit }}'
                fi
                """.trimIndent()
            ).stdout.trim()

            val candidatesRes = ShellExecutor.execute(
                """
                if command -v ip >/dev/null 2>&1; then
                  ip -4 -o addr show up 2>/dev/null \
                    | awk '{split(${ '$' }4, a, "/"); if (a[1] != "" && a[1] != "127.0.0.1") print ${ '$' }2 "|" a[1]}'
                elif command -v ifconfig >/dev/null 2>&1; then
                  ifconfig 2>/dev/null \
                    | awk '/^[^ \t]/{iface=${ '$' }1; sub(":", "", iface)} /inet /{for(i=1;i<=NF;i++) if(${ '$' }i=="inet"){ip=${ '$' }(i+1); if(ip != "127.0.0.1") print iface "|" ip; break}}'
                fi
                """.trimIndent()
            )

            val candidates = candidatesRes.stdout
                .lineSequence()
                .mapNotNull(::parseLanIpCandidate)
                .toList()

            val best = chooseBestLanCandidate(candidates, preferredIface)
            best?.let { LanIpInfo(ip = it.ip, iface = it.iface) }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun getLanIpInfoFromNetworkInterfaces(): LanIpInfo {
        return try {
            val ifaces = withContext(Dispatchers.IO) {
                NetworkInterface.getNetworkInterfaces()
            } ?: return LanIpInfo(ip = "-", iface = "-")

            val candidates = buildList {
                for (iface in ifaces.toList()) {
                    if (!iface.isUp || iface.isLoopback) continue
                    val ifaceName = normalizeLanIfaceName(iface.name)
                    val ip = iface.inetAddresses.toList()
                        .asSequence()
                        .filterIsInstance<Inet4Address>()
                        .mapNotNull { it.hostAddress?.trim() }
                        .firstOrNull(::isUsableIpv4)
                        .orEmpty()
                    if (ifaceName.isNotBlank() && ip.isNotBlank()) {
                        add(LanIpCandidate(iface = ifaceName, ip = ip))
                    }
                }
            }

            val best = chooseBestLanCandidate(candidates, preferredIface = null)
            if (best != null) LanIpInfo(ip = best.ip, iface = best.iface) else LanIpInfo(ip = "-", iface = "-")
        } catch (_: Exception) {
            LanIpInfo(ip = "-", iface = "-")
        }
    }

    private fun parseLanIpCandidate(raw: String): LanIpCandidate? {
        val parts = raw.trim().split('|', limit = 2)
        if (parts.size != 2) return null
        val iface = normalizeLanIfaceName(parts[0])
        val ip = parts[1].trim()
        if (iface.isBlank() || !isUsableIpv4(ip)) return null
        return LanIpCandidate(iface = iface, ip = ip)
    }

    private fun chooseBestLanCandidate(
        candidates: List<LanIpCandidate>,
        preferredIface: String?
    ): LanIpCandidate? {
        val preferred = normalizeLanIfaceName(preferredIface)
        return candidates
            .distinctBy { normalizeLanIfaceName(it.iface) to it.ip }
            .sortedWith(
                compareBy<LanIpCandidate>(
                    { if (isIgnoredLanIface(it.iface)) 1 else 0 },
                    {
                        if (preferred.isNotBlank() && normalizeLanIfaceName(it.iface) == preferred && !isIgnoredLanIface(it.iface)) {
                            0
                        } else {
                            1
                        }
                    },
                    { if (isLanLikeIpv4(it.ip)) 0 else 1 },
                    { lanIfacePriority(it.iface) },
                    { normalizeLanIfaceName(it.iface) }
                )
            )
            .firstOrNull()
    }

    private fun normalizeLanIfaceName(name: String?): String {
        return name.orEmpty().trim().substringBefore('@').trim().removeSuffix(":")
    }

    private fun isIgnoredLanIface(name: String): Boolean {
        val normalized = normalizeLanIfaceName(name).lowercase(Locale.US)
        if (normalized.isBlank()) return true
        return ignoredLanIfacePrefixes.any { prefix ->
            normalized == prefix || normalized.startsWith("$prefix-") || normalized.startsWith(prefix)
        }
    }

    private fun lanIfacePriority(name: String): Int {
        val normalized = normalizeLanIfaceName(name).lowercase(Locale.US)
        val preferredIndex = preferredLanIfacePrefixes.indexOfFirst { prefix ->
            normalized == prefix || normalized.startsWith(prefix)
        }
        return if (preferredIndex >= 0) preferredIndex else preferredLanIfacePrefixes.size + 1
    }

    private fun isUsableIpv4(ip: String): Boolean {
        val value = ip.trim()
        if (value.isBlank()) return false
        if (!value.matches(Regex("""^(?:\d{1,3}\.){3}\d{1,3}$"""))) return false
        return value != "0.0.0.0" && value != "127.0.0.1"
    }

    private fun isLanLikeIpv4(ip: String): Boolean {
        val value = ip.trim()
        return value.startsWith("10.") ||
            value.startsWith("192.168.") ||
            value.matches(Regex("""^172\.(1[6-9]|2\d|3[0-1])\..*""")) ||
            value.matches(Regex("""^100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\..*"""))
    }

    suspend fun getSubscriptionUrlsRaw(): ShellExecutor.Result {
        val key = if (BuildConfig.FLAVOR == "bfr") "subscription_url_clash" else "subscription_url_mihomo"
        return ShellExecutor.execute("grep '^${key}=' /data/adb/box/settings.ini | cut -d '=' -f 2-")
    }

    data class SubscriptionFlowInfo(
        val uploadBytes: java.math.BigInteger,
        val downloadBytes: java.math.BigInteger,
        val totalBytes: java.math.BigInteger,
        val expireEpochSec: Long,
        val title: String? = null
    ) {
        val usedBytes: java.math.BigInteger get() = uploadBytes + downloadBytes
        val remainBytes: java.math.BigInteger get() = (totalBytes - usedBytes).max(java.math.BigInteger.ZERO)
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
        val standardPattern = """filename=["]([^"]+)["]""".toRegex(RegexOption.IGNORE_CASE)
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
        var upload = java.math.BigInteger.ZERO
        var download = java.math.BigInteger.ZERO
        var total = java.math.BigInteger.ZERO
        var expire = 0L
        header.split(";").map { it.trim() }.forEach { part ->
            val kv = part.split("=")
            if (kv.size != 2) return@forEach
            val k = kv[0].trim().lowercase(Locale.getDefault())
            val v = kv[1].trim()
            when (k) {
                "upload" -> upload = v.toBigIntegerOrNull() ?: java.math.BigInteger.ZERO
                "download" -> download = v.toBigIntegerOrNull() ?: java.math.BigInteger.ZERO
                "total" -> total = v.toBigIntegerOrNull() ?: java.math.BigInteger.ZERO
                "expire" -> expire = v.toLongOrNull() ?: 0L
            }
        }
        if (total <= java.math.BigInteger.ZERO) return null
        return SubscriptionFlowInfo(uploadBytes = upload, downloadBytes = download, totalBytes = total, expireEpochSec = expire, title = null)
    }

    private val BYTE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
    private val K = java.math.BigDecimal.valueOf(1024L)

    /**
     * 格式化字节数为人类可读字符串。
     * 使用 BigInteger → BigDecimal 精确计算，地址空间无上限，最高支持 YB。
     */
    fun formatBytes(bytes: java.math.BigInteger): String {
        if (bytes <= java.math.BigInteger.ZERO) return "0 B"
        var value = java.math.BigDecimal(bytes)
        var unitIndex = 0
        while (value >= K && unitIndex < BYTE_UNITS.size - 1) {
            value = value.divide(K, 10, java.math.RoundingMode.HALF_UP)
            unitIndex++
        }
        val formatted = when {
            unitIndex <= 1 -> value.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()
            value >= java.math.BigDecimal.TEN -> value.setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
            else -> value.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        }
        return "$formatted ${BYTE_UNITS[unitIndex]}"
    }

    /** Long 兼容重载 */
    fun formatBytes(bytes: Long): String = formatBytes(java.math.BigInteger.valueOf(bytes))

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
                val location = json.optJSONObject("location")
                val asnObj = json.optJSONObject("asn")
                val company = json.optJSONObject("company")
                PublicGeoIpInfo(
                    ip = json.optString("ip", "-").ifBlank { "-" },
                    country = location?.optString("country", "-")?.ifBlank { "-" } ?: "-",
                    countryCode = (location?.optString("country_code", "-")?.ifBlank { "-" } ?: "-")
                        .uppercase(Locale.getDefault()),
                    asn = asnObj?.optLong("asn", 0L)
                        ?.takeIf { it != 0L }?.toString() ?: "-",
                    asnOrganization = asnObj?.optString("org", "-")?.ifBlank { "-" } ?: "-",
                    isp = company?.optString("name", "-")?.ifBlank { "-" } ?: "-"
                )
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 通过 api-ipv6.ip.sb 探测设备 IPv6 地址。
     * 仅返回 IP 字符串；无 IPv6 连通性时返回 null。
     */
    private fun probeIpv6Address(): String? {
        return try {
            val conn = (URL("https://api-ipv6.ip.sb/geoip").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "BoxApp/Android")
            }
            try {
                if (conn.responseCode !in 200..299) return null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val ip = JSONObject(body).optString("ip", "").trim()
                // 必须包含 ':' 才是真正的 IPv6 地址；返回 IPv4 说明无 IPv6 连通性
                ip.takeIf { it.contains(':') }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 获取公网 IP 地理信息。
     * - IPv4：直接请求 ipapi.is（自动返回 IPv4）
     * - IPv6：先通过 ip.sb 探测 IPv6 地址，再用 ipapi.is/?q= 查询详情
     */
    suspend fun getPublicGeoIp(isIpv6: Boolean = false): PublicGeoIpInfo? {
        return if (isIpv6) {
            val ipv6Addr = probeIpv6Address() ?: return null
            fetchPublicGeoIp("https://api.ipapi.is/?q=$ipv6Addr")
        } else {
            fetchPublicGeoIp("https://api.ipapi.is/")
        }
    }

    suspend fun getPublicIp(): Pair<String, String> {
        val s = getPublicIpSummary()
        return s.ip to s.countryCode
    }

    suspend fun getPublicIpSummary(): PublicIpSummary {
        val info = fetchPublicGeoIp("https://api.ipapi.is/")
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

    suspend fun measureLatency(url: String): LatencyResult = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "BoxApp/Latency")
                .header("Range", "bytes=0-0")
                .build()

            val start = System.nanoTime()
            okHttpClient.newCall(req).execute().use { resp ->
                val end = System.nanoTime()
                val ms = (end - start) / 1_000_000
                val code = resp.code
                when {
                    code in 200..399 -> LatencyResult.Success(ms)
                    code in 400..499 -> LatencyResult.HttpError(code)
                    code >= 500 -> LatencyResult.HttpError(code)
                    else -> LatencyResult.Success(ms) // 1xx/3xx 仍算可达
                }
            }
        } catch (_: java.net.SocketTimeoutException) {
            LatencyResult.Timeout
        } catch (_: java.net.UnknownHostException) {
            LatencyResult.DnsError
        } catch (_: java.net.ConnectException) {
            LatencyResult.Unreachable
        } catch (_: java.net.NoRouteToHostException) {
            LatencyResult.Unreachable
        } catch (_: javax.net.ssl.SSLException) {
            LatencyResult.Unreachable
        } catch (_: java.io.InterruptedIOException) {
            LatencyResult.Timeout
        } catch (_: Exception) {
            LatencyResult.NotAvailable
        }
    }
}
