package com.box.app.data.repo

import android.content.Context
import com.box.app.AppApplication
import com.box.app.data.backend.BoxApi
import com.box.app.data.backend.EnvironmentChecker
import com.box.app.data.backend.HomeMetricsApi
import com.box.app.data.backend.ProcSampler
import com.box.app.data.backend.ShellExecutor
import com.box.app.data.model.EnvironmentState
import com.box.app.data.model.HomeServiceState
import com.box.app.data.model.HomeMetricsState
import com.box.app.data.model.IpMode
import com.box.app.data.model.LatencyResult
import com.box.app.data.model.ServiceStatus
import com.box.app.data.model.SubscriptionItem
import com.box.app.utils.LatencyTargetsManager
import com.box.app.service.BoxForegroundService
import com.box.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.math.max
import java.util.regex.Pattern
import java.text.SimpleDateFormat
import java.util.Locale

object HomeRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _serviceState = MutableStateFlow(HomeServiceState())
    val serviceState: StateFlow<HomeServiceState> = _serviceState.asStateFlow()

    private val _metricsState = MutableStateFlow(HomeMetricsState())
    val metricsState: StateFlow<HomeMetricsState> = _metricsState.asStateFlow()

    private val _showSubStoreEntry = MutableStateFlow(true)
    val showSubStoreEntry: StateFlow<Boolean> = _showSubStoreEntry.asStateFlow()

    private val _useClashApiForSubscription = MutableStateFlow(false)
    val useClashApiForSubscription: StateFlow<Boolean> = _useClashApiForSubscription.asStateFlow()

    private val _useClashApiForNetSpeed = MutableStateFlow(false)
    val useClashApiForNetSpeed: StateFlow<Boolean> = _useClashApiForNetSpeed.asStateFlow()

    @Volatile
    private var lastEnv: EnvironmentState = EnvironmentState()

    @Volatile
    private var pollingStarted = false

    @Volatile
    private var metricsStarted = false

    @Volatile
    private var shellWarmed = false

    private var statusJob: Job? = null
    private var cpuMemJob: Job? = null
    private var netJob: Job? = null
    private var subsJob: Job? = null

    @Volatile
    private var useClashApiForSubscriptionEnabled: Boolean = false

    @Volatile
    private var useClashApiForNetSpeedEnabled: Boolean = false

    private var lastNetSample: ProcSampler.NetSample? = null
    private var lastCpuTimes: ProcSampler.CpuTimes? = null
    private var lastProcessCpuTimeJiffies: Long = 0L
    private var lastProcessCpuSampleTimeMs: Long = 0L

    private const val NET_HISTORY_MAX_POINTS = 60

    // The first computed delta right after app start can be noisy.
    // We only start recording history from the second delta sample.
    private var netDeltaSampleCount: Int = 0

    private val ipMutex = Mutex()

    private val latencyMutex = Mutex()
    private var lastLatencyAtMs: Long = 0L
    private const val LATENCY_REFRESH_MS = 60_000L
    private var latencyWarmupDone: Boolean = false

    private val refreshMutex = Mutex()

    @Volatile
    private var lastActionAtMs: Long = 0L

    private const val ACTION_GRACE_MS = 4_000L

    private var lastSettingsAtMs: Long = 0L
    private var cachedCoreDisplayName: String = "-"
    private var cachedNetworkMode: String = "-"
    private var cachedIpv6Text: String = "-"

    @Volatile
    private var lastNotifiedServiceStatus: ServiceStatus? = null

    @Volatile
    private var lastNotifiedAtMs: Long = 0L

    private const val SETTINGS_REFRESH_MS = 1_200L

    private const val PREFS_NAME = "home_cache"
    private const val KEY_SUBSCRIPTION_CACHE = "subscription_cache_v1"
    private const val KEY_IP_MODE = "ip_mode_v1"

    private fun loadSavedIpModeIfAny() {
        val ctx = AppApplication.appContext
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_IP_MODE, null)?.trim().orEmpty()
        val mode = when (raw.uppercase()) {
            "PUBLIC" -> IpMode.PUBLIC
            "LAN" -> IpMode.LAN
            else -> null
        } ?: return

        val cur = _metricsState.value
        if (cur.ipMode != mode) {
            _metricsState.value = cur.copy(ipMode = mode)
        }
    }

    private fun persistIpMode(mode: IpMode) {
        val ctx = AppApplication.appContext
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_IP_MODE, mode.name).apply()
    }

    private suspend fun refreshShowSubStoreEntryOnce() {
        val result = runCatching {
            ShellExecutor.execute("if [ -d /data/adb/modules/sub_store ]; then echo 1; else echo 0; fi")
        }.getOrNull()

        val show = if (result == null) {
            true
        } else if (result.exitCode != 0 && result.stdout.isBlank()) {
            true
        } else {
            result.stdout.trim() == "1"
        }

        if (_showSubStoreEntry.value != show) _showSubStoreEntry.value = show
    }

    private suspend fun readCurrentSubscriptionUrls(): List<String> {
        val subsRes = HomeMetricsApi.getSubscriptionUrlsRaw()
        return if (subsRes.exitCode == 0) HomeMetricsApi.parseBashArray(subsRes.stdout) else emptyList()
    }

    private fun parseClashUpdatedAtMs(updatedAt: String): Long {
        if (updatedAt.isBlank() || updatedAt == "0001-01-01T00:00:00Z") return 0L
        return try {
            val simplified = if (updatedAt.contains(".")) {
                val parts = updatedAt.split(".")
                if (parts.size == 2) {
                    val millisPart = parts[1].take(3)
                    val timezonePart = parts[1].dropWhile { it.isDigit() }
                    parts[0] + "." + millisPart + timezonePart
                } else {
                    updatedAt
                }
            } else {
                updatedAt
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
            sdf.parse(simplified)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private suspend fun refreshSubscriptionIfUrlsChangedInternal() {
        // For Clash API mode, the concept of "subscription link" may not apply the same way.
        // Keep it manual for now.
        if (useClashApiForSubscriptionEnabled) return

        val currentUrls = readCurrentSubscriptionUrls()
        val cachedUrls = _metricsState.value.subscriptionUrls

        if (currentUrls == cachedUrls) return
        refreshSubscriptionOnce()
    }

    fun refreshSubscriptionIfUrlsChanged() {
        scope.launch {
            runCatching { refreshSubscriptionIfUrlsChangedInternal() }
        }
    }

    private fun loadSubscriptionCacheIfAny() {
        if (useClashApiForSubscriptionEnabled) return
        val ctx = AppApplication.appContext
        val prefs = ctx.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SUBSCRIPTION_CACHE, null)?.trim().orEmpty()
        if (raw.isBlank()) return

        runCatching {
            val obj = JSONObject(raw)
            val count = obj.optString("count", "-")
            val used = obj.optLong("used", 0L)
            val total = obj.optLong("total", 0L)
            val remain = obj.optLong("remain", 0L)
            val progress = obj.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f)

            val subtitle = if (total > 0L) {
                ctx.getString(
                    com.box.app.R.string.home_subscription_total,
                    HomeMetricsApi.formatBytes(total)
                )
            } else {
                "-"
            }

            val itemsJson = obj.optJSONArray("items") ?: JSONArray()
            val items = buildList {
                for (i in 0 until itemsJson.length()) {
                    val itObj = itemsJson.optJSONObject(i) ?: continue
                    val url = itObj.optString("url", "").trim()
                    if (url.isBlank()) continue
                    add(
                        SubscriptionItem(
                            name = itObj.optString("name", "-"),
                            url = url,
                            expiryDate = itObj.optString("expiryDate", "-"),
                            uploadBytes = itObj.optLong("uploadBytes", 0L),
                            downloadBytes = itObj.optLong("downloadBytes", 0L),
                            totalBytes = itObj.optLong("totalBytes", 0L),
                            lastUpdatedAtMs = itObj.optLong("lastUpdatedAtMs", 0L),
                            loading = false
                        )
                    )
                }
            }

            scope.launch {
                updateMetrics {
                    it.copy(
                        subscriptionCount = count,
                        subscriptionSubtitle = subtitle,
                        subscriptionUrls = items.map { s -> s.url },
                        subscriptionItems = items,
                        subscriptionUsedBytes = used,
                        subscriptionTotalBytes = total,
                        subscriptionRemainBytes = remain,
                        subscriptionProgress = progress
                    )
                }
            }
        }
    }

    private fun persistSubscriptionCache(state: HomeMetricsState) {
        if (useClashApiForSubscriptionEnabled) return
        val ctx = AppApplication.appContext
        val prefs = ctx.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

        runCatching {
            val obj = JSONObject()
            obj.put("count", state.subscriptionCount)
            obj.put("subtitle", state.subscriptionSubtitle)
            obj.put("used", state.subscriptionUsedBytes)
            obj.put("total", state.subscriptionTotalBytes)
            obj.put("remain", state.subscriptionRemainBytes)
            obj.put("progress", state.subscriptionProgress.toDouble())

            val items = JSONArray()
            state.subscriptionItems.forEach { item ->
                val itObj = JSONObject()
                itObj.put("name", item.name)
                itObj.put("url", item.url)
                itObj.put("expiryDate", item.expiryDate)
                itObj.put("uploadBytes", item.uploadBytes)
                itObj.put("downloadBytes", item.downloadBytes)
                itObj.put("totalBytes", item.totalBytes)
                itObj.put("lastUpdatedAtMs", item.lastUpdatedAtMs)
                items.put(itObj)
            }
            obj.put("items", items)

            prefs.edit().putString(KEY_SUBSCRIPTION_CACHE, obj.toString()).apply()
        }
    }

    fun startPolling() {
        if (pollingStarted) return
        pollingStarted = true

        lastSettingsAtMs = 0L

        scope.launch {
            warmUpShellOnce()
            runCatching { refreshShowSubStoreEntryOnce() }
            refreshStatusInternal()

            // Restore last selected IP mode (LAN/PUBLIC) before the first refresh.
            runCatching { loadSavedIpModeIfAny() }

            // Load cached subscription state (do not auto-refresh on cold start).
            if (!useClashApiForSubscriptionEnabled) {
                loadSubscriptionCacheIfAny()
            }

            // Refresh subscription:
            // - Clash API mode: fetch directly (no URL comparison, no cache)
            // - URL mode: only refresh when URL list differs from cache.
            if (useClashApiForSubscriptionEnabled) {
                refreshSubscriptionOnce()
            } else {
                refreshSubscriptionIfUrlsChangedInternal()
            }

            // Start independent collectors (box.app style):
            // - Process stats (CPU/RAM): 5s interval + fast 500ms second sample
            // - Network speed: fixed 2s interval
            // - Subscription: low frequency, depends on env readiness
            startCpuMemMonitoring()
            startNetMonitoring()
            startSubscriptionMonitoring()

            // IP should be refreshed once on entering Home.
            refreshIpInternal()

            // Latency: box.app triggers once on init, then manual refresh.
            refreshLatencyInternal(force = true)

            startStatusMonitoring()
        }
    }

    fun refreshSubscriptionNow() {
        scope.launch {
            runCatching { refreshSubscriptionOnce() }
        }
    }

    fun setUseClashApiForNetSpeed(enabled: Boolean) {
        if (useClashApiForNetSpeedEnabled == enabled && _useClashApiForNetSpeed.value == enabled) return

        useClashApiForNetSpeedEnabled = enabled
        if (_useClashApiForNetSpeed.value != enabled) _useClashApiForNetSpeed.value = enabled
        lastNetSample = null
        netDeltaSampleCount = 0
        scope.launch {
            updateMetrics {
                it.copy(
                    useClashApiForNetSpeed = enabled,
                    netFastestDownSpeed = "-",
                    netFastestDownHost = "-",
                    netFastestDownChains = "-",
                    netFastestUpSpeed = "-",
                    netFastestUpHost = "-",
                    netFastestUpChains = "-"
                )
            }
        }
    }

    private suspend fun refreshSubscriptionFromClashApi() {
        val providersJson = BoxApi.getProvidersFromApi()
        if (providersJson == null) {
            updateMetrics {
                it.copy(
                    subscriptionCount = "-",
                    subscriptionSubtitle = "-",
                    subscriptionUrls = emptyList(),
                    subscriptionItems = emptyList(),
                    subscriptionUsedBytes = 0L,
                    subscriptionTotalBytes = 0L,
                    subscriptionRemainBytes = 0L,
                    subscriptionProgress = 0f
                )
            }
            return
        }

        val providers = providersJson.optJSONObject("providers")
        if (providers == null) {
            updateMetrics {
                it.copy(
                    subscriptionCount = "0",
                    subscriptionSubtitle = "-",
                    subscriptionUrls = emptyList(),
                    subscriptionItems = emptyList(),
                    subscriptionUsedBytes = 0L,
                    subscriptionTotalBytes = 0L,
                    subscriptionRemainBytes = 0L,
                    subscriptionProgress = 0f
                )
            }
            return
        }

        val items = mutableListOf<SubscriptionItem>()
        val keys = providers.keys()
        while (keys.hasNext()) {
            val providerName = keys.next()
            val provider = providers.optJSONObject(providerName) ?: continue

            val vehicleType = provider.optString("vehicleType", "")
            if (!vehicleType.equals("HTTP", ignoreCase = true) &&
                !vehicleType.equals("Remote", ignoreCase = true) &&
                !vehicleType.equals("File", ignoreCase = true)
            ) {
                continue
            }

            val subInfo = provider.optJSONObject("subscriptionInfo")
            val upload = subInfo?.run { optLong("Upload", optLong("upload", 0L)) } ?: 0L
            val download = subInfo?.run { optLong("Download", optLong("download", 0L)) } ?: 0L
            val total = subInfo?.run { optLong("Total", optLong("total", 0L)) } ?: 0L
            val expire = subInfo?.run { optLong("Expire", optLong("expire", 0L)) } ?: 0L

            val updatedAtMs = parseClashUpdatedAtMs(provider.optString("updatedAt", ""))

            val expiryDate = HomeMetricsApi.formatExpireDate(expire)
            items.add(
                SubscriptionItem(
                    name = providerName,
                    url = "clash-api://$providerName",
                    expiryDate = expiryDate,
                    uploadBytes = upload,
                    downloadBytes = download,
                    totalBytes = total,
                    lastUpdatedAtMs = updatedAtMs,
                    loading = false
                )
            )
        }

        val meteredItems = items.filter { it.totalBytes > 0L }
        val usedSum = meteredItems.sumOf { it.uploadBytes + it.downloadBytes }
        val totalSum = meteredItems.sumOf { it.totalBytes }
        val remainSum = (totalSum - usedSum).coerceAtLeast(0L)
        val progress = if (totalSum > 0L) (usedSum.toDouble() / totalSum.toDouble()).toFloat().coerceIn(0f, 1f) else 0f

        updateMetrics {
            val next = it.copy(
                subscriptionCount = if (totalSum > 0L) HomeMetricsApi.formatBytes(remainSum) else "-",
                subscriptionSubtitle = if (totalSum > 0L) {
                    AppApplication.appContext.getString(
                        com.box.app.R.string.home_subscription_total,
                        HomeMetricsApi.formatBytes(totalSum)
                    )
                } else {
                    "-"
                },
                subscriptionUrls = emptyList(),
                subscriptionItems = items,
                subscriptionUsedBytes = usedSum,
                subscriptionTotalBytes = totalSum,
                subscriptionRemainBytes = remainSum,
                subscriptionProgress = progress
            )
            persistSubscriptionCache(next)
            next
        }
    }

    private fun startStatusMonitoring() {
        if (statusJob?.isActive == true) return
        statusJob = scope.launch {
            while (isActive) {
                refreshStatusInternal()
                delay(5_000)
            }
        }
    }

    private fun startCpuMemMonitoring() {
        if (cpuMemJob?.isActive == true) return
        cpuMemJob = scope.launch {
            // Fast initial sampling (500ms interval for first two samples)
            runCatching { refreshCpuMemOnce() }
            delay(500)
            runCatching { refreshCpuMemOnce() }

            while (isActive) {
                runCatching { refreshCpuMemOnce() }
                delay(5_000)
            }
        }
    }

    private fun startNetMonitoring() {
        if (netJob?.isActive == true) return
        netJob = scope.launch {
            // For speed delta, first sample seeds lastNetSample.
            runCatching { refreshNetSpeedOnce() }
            delay(500)
            runCatching { refreshNetSpeedOnce() }

            while (isActive) {
                runCatching { refreshNetSpeedOnce() }
                delay(2_000)
            }
        }
    }

    private fun startSubscriptionMonitoring() {
        if (subsJob?.isActive == true) return
        subsJob = scope.launch {
            // Subscription refresh is manual. Keep the job placeholder to avoid duplicate launches.
        }
    }

    private suspend fun updateMetrics(transform: (HomeMetricsState) -> HomeMetricsState) {
        val cur = _metricsState.value
        val next = transform(cur)
        if (next != cur) _metricsState.value = next
    }

    private suspend fun refreshCpuMemOnce() {
        val pidRes = BoxApi.getPid()
        val pid = pidRes.stdout.trim().takeIf { it.isNotBlank() && it.all(Char::isDigit) }

        if (pid == null) {
            lastProcessCpuTimeJiffies = 0L
            lastProcessCpuSampleTimeMs = 0L
            updateMetrics { it.copy(cpu = "CPU -", ram = "RAM -") }
            return
        }

        val sep = "\n---SEP---\n"
        val cmd = """
            cat /proc/$pid/stat 2>/dev/null | head -n 1
            echo '$sep'
            grep '^VmRSS:' /proc/$pid/status 2>/dev/null | head -n 1
            echo '$sep'
            cat /proc/$pid/smaps_rollup 2>/dev/null | grep -E '^(Pss|Private_Clean|Private_Dirty):' || true
        """.trimIndent()
        val res = ShellExecutor.execute(cmd)
        if (res.exitCode != 0 || res.stdout.isBlank()) {
            lastProcessCpuTimeJiffies = 0L
            lastProcessCpuSampleTimeMs = 0L
            updateMetrics { it.copy(cpu = "CPU -", ram = "RAM -") }
            return
        }

        val parts = res.stdout.split(sep)
        val statLine = parts.getOrNull(0)?.trim().orEmpty()
        val rssLine = parts.getOrNull(1)?.trim().orEmpty()
        val ussLines = parts.getOrNull(2)?.trim().orEmpty()

        var cpuText = "CPU -"
        run {
            val sp = statLine.split(Regex("\\s+"))
            if (sp.size > 16) {
                val utime = sp[13].toLongOrNull() ?: 0L
                val stime = sp[14].toLongOrNull() ?: 0L
                val cutime = sp[15].toLongOrNull() ?: 0L
                val cstime = sp[16].toLongOrNull() ?: 0L
                val total = utime + stime + cutime + cstime

                val now = System.currentTimeMillis()
                if (lastProcessCpuTimeJiffies != 0L && lastProcessCpuSampleTimeMs != 0L) {
                    val cpuDiff = (total - lastProcessCpuTimeJiffies).coerceAtLeast(0L)
                    val timeDiffMs = (now - lastProcessCpuSampleTimeMs).coerceAtLeast(1L)
                    val usage = 100.0 * (cpuDiff / 100.0) / (timeDiffMs / 1000.0)
                    cpuText = String.format("CPU %.1f%%", usage.coerceIn(0.0, 100.0))
                }
                lastProcessCpuTimeJiffies = total
                lastProcessCpuSampleTimeMs = now
            }
        }

        var ramText = "RAM -"
        run {
            val sp = rssLine.split(Regex("\\s+"))
            val rssKb = sp.getOrNull(1)?.toLongOrNull()
            var pssKb: Long? = null
            var ussKb: Long? = null
            if (ussLines.isNotBlank()) {
                val pssMatch = Regex("^Pss:\\s+(\\d+)\\s+kB$", RegexOption.MULTILINE)
                    .find(ussLines)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull()
                if (pssMatch != null && pssMatch > 0L) pssKb = pssMatch

                val privateCleanKb = Regex("^Private_Clean:\\s+(\\d+)\\s+kB$", RegexOption.MULTILINE)
                    .find(ussLines)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull()
                val privateDirtyKb = Regex("^Private_Dirty:\\s+(\\d+)\\s+kB$", RegexOption.MULTILINE)
                    .find(ussLines)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull()
                val totalPrivate = (privateCleanKb ?: 0L) + (privateDirtyKb ?: 0L)
                if (totalPrivate > 0L) ussKb = totalPrivate
            }

            // Home card RAM should represent PSS; fall back to RSS if PSS isn't available.
            val displayKb = pssKb ?: rssKb
            if (displayKb != null) {
                ramText = "RAM ${displayKb / 1024}MB"
            }
        }

        updateMetrics { it.copy(cpu = cpuText, ram = ramText) }
    }

    private suspend fun refreshNetSpeedOnce() {
        if (useClashApiForNetSpeedEnabled) {
            val traffic = BoxApi.getProxyTrafficFromApi()
            val downBps = traffic?.downBytesPerSec?.toDouble()
            val upBps = traffic?.upBytesPerSec?.toDouble()

            val downText = if (downBps != null) ProcSampler.formatSpeed(downBps) else "-"
            val upText = if (upBps != null) ProcSampler.formatSpeed(upBps) else "-"

            netDeltaSampleCount += 1
            val downBpsValue = if (netDeltaSampleCount >= 2) downBps else null
            val upBpsValue = if (netDeltaSampleCount >= 2) upBps else null

            updateMetrics { cur ->
                val nextDownHistory = downBpsValue?.let { bps ->
                    (cur.netDownHistory + bps.toFloat()).takeLast(NET_HISTORY_MAX_POINTS)
                } ?: cur.netDownHistory
                val nextUpHistory = upBpsValue?.let { bps ->
                    (cur.netUpHistory + bps.toFloat()).takeLast(NET_HISTORY_MAX_POINTS)
                } ?: cur.netUpHistory

                val fastestDown = traffic?.fastestDownload
                val fastestUp = traffic?.fastestUpload
                cur.copy(
                    useClashApiForNetSpeed = true,
                    netDown = downText,
                    netUp = upText,
                    netDownHistory = nextDownHistory,
                    netUpHistory = nextUpHistory,
                    netFastestDownSpeed = fastestDown?.let { ProcSampler.formatSpeed(it.downloadBytesPerSec.toDouble()) } ?: "-",
                    netFastestDownHost = fastestDown?.host ?: "-",
                    netFastestDownChains = fastestDown?.chains?.joinToString(" → ")?.takeIf { it.isNotBlank() } ?: "-",
                    netFastestUpSpeed = fastestUp?.let { ProcSampler.formatSpeed(it.uploadBytesPerSec.toDouble()) } ?: "-",
                    netFastestUpHost = fastestUp?.host ?: "-",
                    netFastestUpChains = fastestUp?.chains?.joinToString(" → ")?.takeIf { it.isNotBlank() } ?: "-"
                )
            }
            return
        }

        val netNow = ProcSampler.readTotalNetBytes() ?: run {
            val res = ShellExecutor.execute("cat /proc/net/dev 2>/dev/null")
            if (res.exitCode == 0) parseTotalNetBytes(res.stdout) else null
        }
        var downText = "-"
        var upText = "-"
        var downBpsValue: Double? = null
        var upBpsValue: Double? = null
        if (netNow != null) {
            val prev = lastNetSample
            if (prev != null) {
                val dt = (netNow.timeMs - prev.timeMs).coerceAtLeast(1)
                val downBps = (netNow.rxBytes - prev.rxBytes).coerceAtLeast(0) * 1000.0 / dt
                val upBps = (netNow.txBytes - prev.txBytes).coerceAtLeast(0) * 1000.0 / dt
                downText = ProcSampler.formatSpeed(downBps)
                upText = ProcSampler.formatSpeed(upBps)
                netDeltaSampleCount += 1
                if (netDeltaSampleCount >= 2) {
                    downBpsValue = downBps
                    upBpsValue = upBps
                }
            }
            lastNetSample = netNow
        }
        updateMetrics { cur ->
            val nextDownHistory = downBpsValue?.let { bps ->
                (cur.netDownHistory + bps.toFloat()).takeLast(NET_HISTORY_MAX_POINTS)
            } ?: cur.netDownHistory
            val nextUpHistory = upBpsValue?.let { bps ->
                (cur.netUpHistory + bps.toFloat()).takeLast(NET_HISTORY_MAX_POINTS)
            } ?: cur.netUpHistory
            cur.copy(
                useClashApiForNetSpeed = false,
                netDown = downText,
                netUp = upText,
                netDownHistory = nextDownHistory,
                netUpHistory = nextUpHistory,
                netFastestDownSpeed = "-",
                netFastestDownHost = "-",
                netFastestDownChains = "-",
                netFastestUpSpeed = "-",
                netFastestUpHost = "-",
                netFastestUpChains = "-"
            )
        }
    }

    private fun parseTotalNetBytes(content: String): ProcSampler.NetSample? {
        if (content.isBlank()) return null
        var rx: Long = 0
        var tx: Long = 0
        content.lines().forEach { line ->
            if (!line.contains(":")) return@forEach
            val trimmed = line.trim()
            if (trimmed.startsWith("lo:")) return@forEach
            val parts = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (parts.size >= 10) {
                rx += parts[1].toLongOrNull() ?: 0L
                tx += parts[9].toLongOrNull() ?: 0L
            }
        }
        return ProcSampler.NetSample(rxBytes = rx, txBytes = tx, timeMs = System.currentTimeMillis())
    }

    private fun parseCpuTimes(content: String): ProcSampler.CpuTimes? {
        if (content.isBlank()) return null
        val first = content.lineSequence().firstOrNull()?.trim().orEmpty()
        if (first.isBlank()) return null
        val parts = first.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size < 5) return null
        val nums = parts.drop(1).mapNotNull { it.toLongOrNull() }
        if (nums.size < 4) return null
        val idle = nums.getOrElse(3) { 0L } + nums.getOrElse(4) { 0L }
        val total = nums.sum()
        return ProcSampler.CpuTimes(idle = idle, total = total)
    }

    private fun parseMemUsedMb(content: String): Long? {
        if (content.isBlank()) return null
        fun findKb(key: String): Long? {
            val line = content.lineSequence().firstOrNull { it.startsWith(key) } ?: return null
            return line.split(Regex("\\s+")).getOrNull(1)?.toLongOrNull()
        }
        val memTotal = findKb("MemTotal:") ?: return null
        val memAvailable = findKb("MemAvailable:") ?: return null
        val usedKb = max(0L, memTotal - memAvailable)
        return usedKb / 1024
    }

    private suspend fun refreshSubscriptionOnce() {
        val env = lastEnv
        if (!env.isReady) {
            updateMetrics {
                it.copy(
                    subscriptionCount = "-",
                    subscriptionSubtitle = "-",
                    subscriptionUrls = emptyList(),
                    subscriptionItems = emptyList(),
                    subscriptionUsedBytes = 0L,
                    subscriptionTotalBytes = 0L,
                    subscriptionRemainBytes = 0L,
                    subscriptionProgress = 0f
                )
            }
            return
        }

        if (useClashApiForSubscriptionEnabled) {
            refreshSubscriptionFromClashApi()
            return
        }

        val subsRes = HomeMetricsApi.getSubscriptionUrlsRaw()
        val subs = if (subsRes.exitCode == 0) HomeMetricsApi.parseBashArray(subsRes.stdout) else emptyList()

        if (subs.isEmpty()) {
            updateMetrics {
                it.copy(
                    subscriptionCount = "0",
                    subscriptionSubtitle = "-",
                    subscriptionUrls = emptyList(),
                    subscriptionItems = emptyList(),
                    subscriptionUsedBytes = 0L,
                    subscriptionTotalBytes = 0L,
                    subscriptionRemainBytes = 0L,
                    subscriptionProgress = 0f
                )
            }
            return
        }

        // Mark list as loading quickly but keep previous values to avoid flicker.
        updateMetrics { cur ->
            val existing = cur.subscriptionItems.associateBy { it.url }
            val items = subs.mapIndexed { idx, url ->
                val old = existing[url]
                SubscriptionItem(
                    name = old?.name ?: "Subscription ${idx + 1}",
                    url = url,
                    expiryDate = old?.expiryDate ?: "-",
                    uploadBytes = old?.uploadBytes ?: 0L,
                    downloadBytes = old?.downloadBytes ?: 0L,
                    totalBytes = old?.totalBytes ?: 0L,
                    lastUpdatedAtMs = old?.lastUpdatedAtMs ?: 0L,
                    loading = true
                )
            }
            cur.copy(subscriptionUrls = subs, subscriptionItems = items)
        }

        val items = coroutineScope {
            subs.mapIndexed { idx, url ->
                async {
                    val info = HomeMetricsApi.getSubscriptionFlowInfo(url)
                    val fallbackName = "Subscription ${idx + 1}"
                    val headerName = info?.title?.trim().orEmpty().takeIf { it.isNotBlank() }
                    val name = headerName ?: fallbackName
                    if (info == null) {
                        SubscriptionItem(
                            name = name,
                            url = url,
                            expiryDate = "-",
                            uploadBytes = 0L,
                            downloadBytes = 0L,
                            totalBytes = 0L,
                            lastUpdatedAtMs = System.currentTimeMillis(),
                            loading = false
                        )
                    } else {
                        SubscriptionItem(
                            name = name,
                            url = url,
                            expiryDate = HomeMetricsApi.formatExpireDate(info.expireEpochSec),
                            uploadBytes = info.uploadBytes,
                            downloadBytes = info.downloadBytes,
                            totalBytes = info.totalBytes,
                            lastUpdatedAtMs = System.currentTimeMillis(),
                            loading = false
                        )
                    }
                }
            }.map { it.await() }
        }

        val meteredItems = items.filter { it.totalBytes > 0L }
        val usedSum = meteredItems.sumOf { it.uploadBytes + it.downloadBytes }
        val totalSum = meteredItems.sumOf { it.totalBytes }
        val remainSum = (totalSum - usedSum).coerceAtLeast(0L)
        val progress = if (totalSum > 0L) (usedSum.toDouble() / totalSum.toDouble()).toFloat().coerceIn(0f, 1f) else 0f

        updateMetrics {
            val next = it.copy(
                subscriptionCount = if (totalSum > 0L) HomeMetricsApi.formatBytes(remainSum) else "-",
                subscriptionSubtitle = if (totalSum > 0L) {
                    AppApplication.appContext.getString(
                        com.box.app.R.string.home_subscription_total,
                        HomeMetricsApi.formatBytes(totalSum)
                    )
                } else {
                    "-"
                },
                subscriptionUrls = subs,
                subscriptionItems = items,
                subscriptionUsedBytes = usedSum,
                subscriptionTotalBytes = totalSum,
                subscriptionRemainBytes = remainSum,
                subscriptionProgress = progress
            )
            persistSubscriptionCache(next)
            next
        }
    }

    private suspend fun refreshSubscriptionItemInternal(url: String) {
        val env = lastEnv
        if (!env.isReady) return

        if (url.startsWith("clash-api://")) {
            val providerName = url.removePrefix("clash-api://")
            updateMetrics { cur ->
                cur.copy(subscriptionItems = cur.subscriptionItems.map { if (it.url == url) it.copy(loading = true) else it })
            }
            runCatching { BoxApi.refreshClashProvider(providerName) }
            refreshSubscriptionFromClashApi()
            return
        }

        updateMetrics { cur ->
            cur.copy(
                subscriptionItems = cur.subscriptionItems.map {
                    if (it.url == url) it.copy(loading = true) else it
                }
            )
        }

        val info = HomeMetricsApi.getSubscriptionFlowInfo(url)
        val updatedAt = System.currentTimeMillis()

        updateMetrics { cur ->
            val updated = cur.subscriptionItems.map {
                if (it.url != url) return@map it
                if (info == null) {
                    it.copy(expiryDate = "-", uploadBytes = 0L, downloadBytes = 0L, totalBytes = 0L, lastUpdatedAtMs = updatedAt, loading = false)
                } else {
                    val headerName = info.title?.trim().orEmpty().takeIf { it.isNotBlank() }
                    it.copy(
                        name = headerName ?: it.name,
                        expiryDate = HomeMetricsApi.formatExpireDate(info.expireEpochSec),
                        uploadBytes = info.uploadBytes,
                        downloadBytes = info.downloadBytes,
                        totalBytes = info.totalBytes,
                        lastUpdatedAtMs = updatedAt,
                        loading = false
                    )
                }
            }
            val meteredItems = updated.filter { it.totalBytes > 0L }
            val usedSum = meteredItems.sumOf { it.uploadBytes + it.downloadBytes }
            val totalSum = meteredItems.sumOf { it.totalBytes }
            val remainSum = (totalSum - usedSum).coerceAtLeast(0L)
            val progress = if (totalSum > 0L) (usedSum.toDouble() / totalSum.toDouble()).toFloat().coerceIn(0f, 1f) else 0f
            cur.copy(
                subscriptionItems = updated,
                subscriptionUsedBytes = usedSum,
                subscriptionTotalBytes = totalSum,
                subscriptionRemainBytes = remainSum,
                subscriptionProgress = progress,
                subscriptionCount = if (totalSum > 0L) HomeMetricsApi.formatBytes(remainSum) else "-",
                subscriptionSubtitle = if (totalSum > 0L) {
                    AppApplication.appContext.getString(
                        com.box.app.R.string.home_subscription_total,
                        HomeMetricsApi.formatBytes(totalSum)
                    )
                } else {
                    "-"
                }
            )
        }

        persistSubscriptionCache(_metricsState.value)
    }

    private suspend fun warmUpShellOnce() {
        if (shellWarmed) return
        shellWarmed = true
        runCatching {
            // First `su` spawn is the slowest part; create multiple sessions early for concurrency.
            ShellExecutor.warmUpRootShell(minSessions = 1)
            ShellExecutor.execute("id -u 2>/dev/null")
        }
    }

    fun toggleIpMode() {
        scope.launch {
            ipMutex.withLock {
                val cur = _metricsState.value
                val nextMode = if (cur.ipMode == IpMode.LAN) IpMode.PUBLIC else IpMode.LAN

                val lanInfo = HomeMetricsApi.getLanIpInfo()
                val lan = lanInfo.ip
                val lanIface = lanInfo.iface
                var pubIp = cur.publicIp
                var publicCountry = cur.publicCountry
                var cc = cur.publicCountryCode
                if (nextMode == IpMode.PUBLIC) {
                    val summary = HomeMetricsApi.getPublicIpSummary()
                    pubIp = summary.ip
                    publicCountry = summary.country
                    cc = summary.countryCode
                }

                val shown = if (nextMode == IpMode.LAN) lan else pubIp
                _metricsState.value = cur.copy(
                    ipMode = nextMode,
                    ip = shown,
                    lanIp = lan,
                    lanInterface = lanIface,
                    publicIp = pubIp,
                    publicCountry = publicCountry,
                    publicCountryCode = cc
                )

                persistIpMode(nextMode)
            }
        }
    }

    fun refreshIpNow() {
        scope.launch {
            refreshIpInternal()
        }
    }

    fun refreshLatencyNow() {
        scope.launch {
            refreshLatencyInternal(force = true)
        }
    }

    fun refreshSubscriptionItemNow(url: String) {
        scope.launch {
            refreshSubscriptionItemInternal(url)
        }
    }

    fun setUseClashApiForSubscription(enabled: Boolean) {
        useClashApiForSubscriptionEnabled = enabled
        if (_useClashApiForSubscription.value != enabled) _useClashApiForSubscription.value = enabled

        scope.launch {
            if (enabled) {
                runCatching { refreshSubscriptionOnce() }
            } else {
                runCatching { refreshSubscriptionIfUrlsChangedInternal() }
            }
        }
    }

    fun startService() {
        scope.launch {
            if (!ensureEnvReady()) return@launch
            lastActionAtMs = System.currentTimeMillis()
            lastSettingsAtMs = 0L
            _serviceState.value = _serviceState.value.copy(status = ServiceStatus.Starting)
            runCatching {
                BoxForegroundService.ensureRunning(AppApplication.appContext)
                BoxForegroundService.updateStatus(AppApplication.appContext, ServiceStatus.Starting)
            }
            BoxApi.startService()
            refreshStatusInternal()

            // Update Panel URL cache on every service start (ip:port may change)
            runCatching {
                val url = BoxApi.getPanelUrl()?.takeIf { it.isNotBlank() }
                if (url != null) {
                    AppApplication.appContext
                        .getSharedPreferences("panel_cache", Context.MODE_PRIVATE)
                        .edit()
                        .putString("panel_url_v1", url)
                        .apply()
                }
            }
        }
    }

    fun stopService() {
        scope.launch {
            if (!ensureEnvReady()) return@launch
            lastActionAtMs = System.currentTimeMillis()
            lastSettingsAtMs = 0L
            _serviceState.value = _serviceState.value.copy(status = ServiceStatus.Stopping)
            runCatching {
                BoxForegroundService.ensureRunning(AppApplication.appContext)
                BoxForegroundService.updateStatus(AppApplication.appContext, ServiceStatus.Stopping)
            }
            BoxApi.stopService()
            refreshStatusInternal()
        }
    }

    fun restartService() {
        scope.launch {
            if (!ensureEnvReady()) return@launch
            lastActionAtMs = System.currentTimeMillis()
            lastSettingsAtMs = 0L
            _serviceState.value = _serviceState.value.copy(status = ServiceStatus.Restarting)
            runCatching {
                BoxForegroundService.ensureRunning(AppApplication.appContext)
                BoxForegroundService.updateStatus(AppApplication.appContext, ServiceStatus.Restarting)
            }
            BoxApi.restartService()
            refreshStatusInternal()

            // Update Panel URL cache on every service restart
            runCatching {
                val url = BoxApi.getPanelUrl()?.takeIf { it.isNotBlank() }
                if (url != null) {
                    AppApplication.appContext
                        .getSharedPreferences("panel_cache", Context.MODE_PRIVATE)
                        .edit()
                        .putString("panel_url_v1", url)
                        .apply()
                }
            }
        }
    }

    private suspend fun refreshStatusInternal() {
        refreshMutex.withLock {
            val current = _serviceState.value

            val env = try {
                EnvironmentChecker.check()
            } catch (e: Exception) {
                EnvironmentState(checked = true, hasRoot = false, hasModule = false, hasScripts = false, message = "")
            }
            lastEnv = env

            if (!env.isReady) {
                cachedCoreDisplayName = "-"
                cachedNetworkMode = "-"
                cachedIpv6Text = "-"
                val next = HomeServiceState(
                    env = env,
                    status = ServiceStatus.Stopped,
                    pid = "-",
                    uptimeText = "-",
                    coreDisplayName = "-",
                    networkMode = "-",
                    ipv6Text = "-"
                )
                if (next != current) _serviceState.value = next
                return@withLock
            }

            val pidRes = BoxApi.getPid()
            val pid = pidRes.stdout.trim().takeIf { it.isNotBlank() && it.all(Char::isDigit) }
            val uptime = if (pid != null) computeUptimeText() else "-"

            refreshSettingsIfNeeded()

            val now = System.currentTimeMillis()
            val inGrace = (now - lastActionAtMs) in 0..ACTION_GRACE_MS
            val isBusy = current.status is ServiceStatus.Starting ||
                current.status is ServiceStatus.Stopping ||
                current.status is ServiceStatus.Restarting

            if (pidRes.exitCode != 0 && isBusy && inGrace) {
                return@withLock
            }

            val nextStatus = when {
                pid != null -> ServiceStatus.Running
                isBusy && inGrace -> current.status
                else -> ServiceStatus.Stopped
            }

            runCatching {
                val nowMs = System.currentTimeMillis()
                val shouldNotify = lastNotifiedServiceStatus != nextStatus || (nowMs - lastNotifiedAtMs) >= 15_000L

                if (nextStatus is ServiceStatus.Stopped) {
                    BoxForegroundService.stop(AppApplication.appContext)
                    lastNotifiedServiceStatus = nextStatus
                    lastNotifiedAtMs = nowMs
                } else if (shouldNotify) {
                    BoxForegroundService.ensureRunning(AppApplication.appContext)
                    BoxForegroundService.updateStatus(AppApplication.appContext, nextStatus)
                    lastNotifiedServiceStatus = nextStatus
                    lastNotifiedAtMs = nowMs
                }
            }

            val next = HomeServiceState(
                env = env,
                status = nextStatus,
                pid = pid ?: "-",
                uptimeText = if (pid != null) uptime else "-",
                coreDisplayName = if (pid != null) cachedCoreDisplayName else "—",
                networkMode = if (pid != null) cachedNetworkMode else "—",
                ipv6Text = if (pid != null) cachedIpv6Text else "—"
            )
            if (next != current) _serviceState.value = next
        }
    }

    private suspend fun refreshSettingsIfNeeded(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastSettingsAtMs) < SETTINGS_REFRESH_MS) return

        val settings = BoxApi.getSettings()
        if (settings.isBlank()) {
            cachedCoreDisplayName = "-"
            cachedNetworkMode = "-"
            cachedIpv6Text = "-"
            return
        }

        suspend fun writeSetting(key: String, value: String) {
            val file = "/data/adb/box/settings.ini"
            val escaped = value.replace("\"", "\\\"")
            val cmd = "if [ -f '$file' ]; then " +
                "if grep -q '^${key}=' '$file'; then " +
                "sed -i 's/^${key}=.*/${key}=\\\"${escaped}\\\"/' '$file'; " +
                "else echo '${key}=\\\"${escaped}\\\"' >> '$file'; fi; " +
                "else echo '${key}=\\\"${escaped}\\\"' > '$file'; fi"
            ShellExecutor.execute(cmd)
        }

        if (BuildConfig.FLAVOR == "bfr") {
            val binExisting = findSetting(settings, "bin_name")
            if (binExisting.isNullOrBlank()) {
                runCatching {
                    writeSetting("bin_name", "clash")
                    writeSetting("xclash_option", "mihomo")
                }
            }
        }

        val rawCore = findSetting(settings, "bin_name")
        val xclashOption = findSetting(settings, "xclash_option")
        cachedCoreDisplayName = when {
            BuildConfig.FLAVOR == "bfr" && rawCore == "clash" && !xclashOption.isNullOrBlank() -> "clash-$xclashOption"
            else -> rawCore?.takeIf { it.isNotBlank() } ?: "-"
        }

        val mode = findSetting(settings, "network_mode")
        cachedNetworkMode = mode?.trim()?.takeIf { it.isNotBlank() } ?: "-"

        val ipv6 = findSetting(settings, "ipv6")
        cachedIpv6Text = when (ipv6?.toBooleanStrictOrNull()) {
            true -> "true"
            false -> "false"
            else -> "-"
        }

        lastSettingsAtMs = now
    }

    private fun findSetting(settings: String, key: String): String? {
        val pattern = Pattern.compile("^${key}=\"?(.*?)\"?$", Pattern.MULTILINE)
        val matcher = pattern.matcher(settings)
        return if (matcher.find()) matcher.group(1) else null
    }

    private suspend fun refreshMetricsInternal() {
        val env = lastEnv
        // Fast metrics should not wait for env/root readiness.
        // Only subscription (reads /data/adb) depends on root/module/scripts.

        coroutineScope {
            val ipJob = async { refreshIpInternal() }
            val latencyJob = async { refreshLatencyInternal(force = false) }
            val netJob = async { ProcSampler.readTotalNetBytes() }
            val subsJob = async {
                if (env.isReady) HomeMetricsApi.getSubscriptionUrlsRaw() else null
            }

            ipJob.await()
            latencyJob.await()

            val currentMetrics = _metricsState.value
            val ip = currentMetrics.ip

            val netNow = netJob.await()
            var downText = "-"
            var upText = "-"
            if (netNow != null) {
                val prev = lastNetSample
                if (prev != null) {
                    val dt = (netNow.timeMs - prev.timeMs).coerceAtLeast(1)
                    val downBps = (netNow.rxBytes - prev.rxBytes).coerceAtLeast(0) * 1000.0 / dt
                    val upBps = (netNow.txBytes - prev.txBytes).coerceAtLeast(0) * 1000.0 / dt
                    downText = ProcSampler.formatSpeed(downBps)
                    upText = ProcSampler.formatSpeed(upBps)
                }
                lastNetSample = netNow
            }

            val subsRes = subsJob.await()
            val subs = if (subsRes != null && subsRes.exitCode == 0) HomeMetricsApi.parseBashArray(subsRes.stdout) else emptyList()
            val subCount = if (subs.isEmpty()) "0" else subs.size.toString()

            // CPU/RAM shown on Home "System" card should represent the service process only.
            // Values are sampled by refreshCpuMemOnce() (pid-based /proc/$pid parsing).
            val cpuText = currentMetrics.cpu
            val memUsed = currentMetrics.ram

            val next = HomeMetricsState(
                ipMode = currentMetrics.ipMode,
                ip = ip,
                lanIp = currentMetrics.lanIp,
                lanInterface = currentMetrics.lanInterface,
                publicIp = currentMetrics.publicIp,
                publicCountry = currentMetrics.publicCountry,
                publicCountryCode = currentMetrics.publicCountryCode,
                netDown = downText,
                netUp = upText,
                netDownHistory = currentMetrics.netDownHistory,
                netUpHistory = currentMetrics.netUpHistory,
                latencyLoading = currentMetrics.latencyLoading,
                latencyBaiduMs = currentMetrics.latencyBaiduMs,
                latencyCloudflareMs = currentMetrics.latencyCloudflareMs,
                latencyGoogleMs = currentMetrics.latencyGoogleMs,
                latencyMs = currentMetrics.latencyMs,
                latencyLabel = currentMetrics.latencyLabel,
                subscriptionCount = if (!env.isReady) "-" else "$subCount Active",
                subscriptionSubtitle = if (!env.isReady) "-" else if (subs.isEmpty()) "No subscriptions" else "Tap to manage",
                subscriptionUrls = subs,
                cpu = cpuText,
                ram = memUsed
            )
            if (next != _metricsState.value) _metricsState.value = next
        }
    }

    private suspend fun refreshLatencyInternal(force: Boolean) {
        val env = lastEnv
        if (!env.isReady) return

        latencyMutex.withLock {
            val now = System.currentTimeMillis()
            if (!force && (now - lastLatencyAtMs) < LATENCY_REFRESH_MS) return
            lastLatencyAtMs = now

            val cur = _metricsState.value
            _metricsState.value = cur.copy(
                latencyLoading = true,
                latencyMs = "... ms",
                latencyLabel = "Testing…"
            )

            val targets = LatencyTargetsManager.targets.value
            val t1 = targets.getOrNull(0)
            val t2 = targets.getOrNull(1)
            val t3 = targets.getOrNull(2)

            if (!latencyWarmupDone) {
                latencyWarmupDone = true
                runCatching {
                    coroutineScope {
                        val j1 = async { HomeMetricsApi.measureLatency(t1?.url ?: "https://baidu.com") }
                        val j2 = async { HomeMetricsApi.measureLatency(t2?.url ?: "https://cloudflare.com") }
                        val j3 = async { HomeMetricsApi.measureLatency(t3?.url ?: "https://google.com") }
                        j1.await(); j2.await(); j3.await()
                    }
                }
            }

            val (r1, r2, r3) = coroutineScope {
                val j1 = async { HomeMetricsApi.measureLatency(t1?.url ?: "https://baidu.com") }
                val j2 = async { HomeMetricsApi.measureLatency(t2?.url ?: "https://cloudflare.com") }
                val j3 = async { HomeMetricsApi.measureLatency(t3?.url ?: "https://google.com") }
                Triple(j1.await(), j2.await(), j3.await())
            }

            fun toText(r: LatencyResult): String = when (r) {
                is LatencyResult.Success -> "${r.latencyMs} ms"
                is LatencyResult.Loading -> "..."
                else -> "N/A"
            }

            fun toValue(r: LatencyResult): Long? = when (r) {
                is LatencyResult.Success -> r.latencyMs
                else -> null
            }

            val v1Text = toText(r1)
            val v2Text = toText(r2)
            val v3Text = toText(r3)

            val n1 = t1?.name?.trim().takeIf { !it.isNullOrBlank() } ?: "Baidu"
            val n2 = t2?.name?.trim().takeIf { !it.isNullOrBlank() } ?: "Cloudflare"
            val n3 = t3?.name?.trim().takeIf { !it.isNullOrBlank() } ?: "Google"

            val best = listOfNotNull(
                toValue(r1)?.let { it to n1 },
                toValue(r2)?.let { it to n2 },
                toValue(r3)?.let { it to n3 }
            ).minByOrNull { it.first }

            val bestValue = best?.let { "${it.first} ms" } ?: "N/A"
            val details = "$n1 $v1Text · $n2 $v2Text · $n3 $v3Text"

            _metricsState.value = _metricsState.value.copy(
                latencyLoading = false,
                latencyBaiduMs = v1Text,
                latencyCloudflareMs = v2Text,
                latencyGoogleMs = v3Text,
                latencyMs = bestValue,
                latencyLabel = details
            )
        }
    }

    private suspend fun refreshIpInternal() {
        ipMutex.withLock {
            val cur = _metricsState.value
            val lanInfo = HomeMetricsApi.getLanIpInfo()
            val lan = lanInfo.ip
            val lanIface = lanInfo.iface
            var pubIp = cur.publicIp
            var publicCountry = cur.publicCountry
            var cc = cur.publicCountryCode
            if (cur.ipMode == IpMode.PUBLIC) {
                val summary = HomeMetricsApi.getPublicIpSummary()
                pubIp = summary.ip
                publicCountry = summary.country
                cc = summary.countryCode
            }
            val shownRaw = if (cur.ipMode == IpMode.LAN) lan else pubIp
            val shown = shownRaw

            _metricsState.value = cur.copy(
                ip = shown,
                lanIp = lan,
                lanInterface = lanIface,
                publicIp = pubIp,
                publicCountry = publicCountry,
                publicCountryCode = cc
            )
        }
    }

    private fun formatIpForDisplay(ip: String): String {
        val s = ip.trim()
        if (s.isBlank()) return "-"
        if (s == "N/A") return s
        if (s.contains(":")) {
            // Likely IPv6; keep it readable within the card.
            if (s.length <= 18) return s
            val head = s.take(8)
            val tail = s.takeLast(6)
            return "$head…$tail"
        }
        return s
    }

    private suspend fun ensureEnvReady(): Boolean {
        val env = lastEnv
        if (!env.isReady) {
            _serviceState.value = _serviceState.value.copy(env = env, status = ServiceStatus.Stopped)
            return false
        }
        return true
    }

    private suspend fun computeUptimeText(): String {
        val tsRes = BoxApi.getPidTimestamp()
        val startedSec = tsRes.stdout.trim().toLongOrNull() ?: return "-"
        val nowSec = System.currentTimeMillis() / 1000
        val delta = max(0, (nowSec - startedSec).toInt())

        val hours = delta / 3600
        val minutes = (delta % 3600) / 60

        return when {
            hours > 0 -> AppApplication.appContext.getString(
                com.box.app.R.string.home_uptime_hours_minutes,
                hours,
                minutes
            )
            minutes > 0 -> AppApplication.appContext.getString(
                com.box.app.R.string.home_uptime_minutes,
                minutes
            )
            else -> AppApplication.appContext.getString(com.box.app.R.string.home_uptime_less_than_minute)
        }
    }
}
