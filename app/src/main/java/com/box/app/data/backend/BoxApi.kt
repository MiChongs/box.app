package com.box.app.data.backend

import com.box.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

internal object BoxApi {

    @Volatile
    private var cachedPid: String? = null

    private suspend fun execute(command: String): ShellExecutor.Result = ShellExecutor.execute(command)

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    data class ApiConfig(val port: Int, val secret: String?)

    data class FastestConnection(
        val host: String,
        val chains: List<String>,
        val downloadBytesPerSec: Long,
        val uploadBytesPerSec: Long
    )

    data class TrafficStats(
        val downBytesPerSec: Long,
        val upBytesPerSec: Long,
        val fastestDownload: FastestConnection?,
        val fastestUpload: FastestConnection?
    )

    data class ConnectionInfo(
        val id: String,
        val host: String,
        val destinationIP: String,
        val destinationPort: String,
        val sourceIP: String = "",
        val sourcePort: String = "",
        val network: String,
        val type: String,
        val chains: List<String>,
        val rule: String,
        val rulePayload: String,
        val download: Long,
        val upload: Long,
        val start: String,
        val process: String = "",
        val processPath: String = "",
        val sniffHost: String = "",
        val destinationGeoIP: String = "",
        val isAlive: Boolean
    )

    /** GET /connections 完整快照 */
    data class ConnectionsSnapshot(
        val downloadTotal: Long,
        val uploadTotal: Long,
        val memory: Long,
        val connections: List<ConnectionInfo>
    )

    data class ClashVersionInfo(
        val name: String,
        val version: String,
        val premium: Boolean,
        val meta: Boolean
    )

    data class ClashConfigInfo(
        val mode: String,
        val logLevel: String,
        val allowLan: Boolean,
        val ipv6: Boolean,
        val mixedPort: Int?,
        val socksPort: Int?,
        val httpPort: Int?,
        val redirPort: Int?,
        val tproxyPort: Int?,
        val externalController: String,
        val secretEnabled: Boolean
    )

    data class ClashProxyGroup(
        val name: String,
        val type: String,
        val now: String,
        val options: List<String>,
        val alive: Boolean
    )

    data class ClashRuleInfo(
        val type: String,
        val payload: String,
        val proxy: String
    )

    data class ClashProviderInfo(
        val name: String,
        val type: String,
        val vehicleType: String,
        val updatedAt: String,
        val proxyCount: Int
    )

    @Volatile
    private var cachedApiConfig: ApiConfig? = null

    private var lastProxyTrafficDown: Long = 0L
    private var lastProxyTrafficUp: Long = 0L
    private var lastProxyTrafficTime: Long = 0L

    private val previousConnectionsMap = mutableMapOf<String, Pair<Long, Long>>() // id -> (download, upload)

    @Volatile
    private var proxyTrafficFilterChains: Set<String> = setOf("DIRECT", "REJECT")

    fun setProxyTrafficFilterChains(chains: Collection<String>) {
        proxyTrafficFilterChains = chains
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.uppercase() }
            .toSet()
    }

    fun resetProxyTrafficSampler() {
        lastProxyTrafficDown = 0L
        lastProxyTrafficUp = 0L
        lastProxyTrafficTime = 0L
    }

    fun clearApiConfigCache() {
        cachedApiConfig = null
    }

    private fun clashRequestBody(jsonBody: JSONObject?): okhttp3.RequestBody {
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        return (jsonBody?.toString() ?: "{}").toRequestBody(mediaType)
    }

    private fun encodePathSegment(value: String): String {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

    private suspend fun executeClashTextRequest(
        path: String,
        method: String = "GET",
        jsonBody: JSONObject? = null
    ): String? = withContext(Dispatchers.IO) {
        val apiConfig = cachedApiConfig ?: getExternalControllerConfig()?.also { cachedApiConfig = it } ?: return@withContext null
        return@withContext try {
            val url = "http://127.0.0.1:${apiConfig.port}$path"
            val requestBuilder = Request.Builder().url(url)
            if (!apiConfig.secret.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer ${apiConfig.secret}")
            }

            when (method.uppercase()) {
                "GET" -> requestBuilder.get()
                "PUT" -> requestBuilder.put(clashRequestBody(jsonBody))
                "PATCH" -> requestBuilder.patch(clashRequestBody(jsonBody))
                "DELETE" -> requestBuilder.delete(clashRequestBody(jsonBody))
                else -> requestBuilder.method(method.uppercase(), clashRequestBody(jsonBody))
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body.string().orEmpty()
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun executeClashJsonRequest(
        path: String,
        method: String = "GET",
        jsonBody: JSONObject? = null
    ): JSONObject? {
        val body = executeClashTextRequest(path = path, method = method, jsonBody = jsonBody)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return try {
            JSONObject(body)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getPid(): ShellExecutor.Result {
        val result = execute("cat /data/adb/box/run/box.pid 2>/dev/null")

        val pid = result.stdout.trim()
        if (result.exitCode != 0 || pid.isBlank() || !pid.all { it.isDigit() }) {
            cachedPid = null
            return result
        }

        // pid file may be stale if service is stopped externally.
        val alive = execute("[ -d /proc/$pid ] && echo 1 || echo 0").stdout.trim() == "1"
        if (!alive) {
            cachedPid = null
            return ShellExecutor.Result("", "stale pid", 1)
        }

        if (cachedPid != pid) cachedPid = pid
        return result
    }

    suspend fun getPidTimestamp(): ShellExecutor.Result {
        return execute("stat -c %Y /data/adb/box/run/box.pid 2>/dev/null")
    }

    suspend fun getModuleProp(): String {
        val result = execute("cat /data/adb/modules/box_for_root/module.prop 2>/dev/null")
        return if (result.exitCode == 0) result.stdout else ""
    }

    suspend fun reloadConfig(): ShellExecutor.Result {
        return withContext(Dispatchers.IO) {
            try {
                val apiConfig = cachedApiConfig
                    ?: getExternalControllerConfig()?.also { cachedApiConfig = it }
                    ?: return@withContext ShellExecutor.Result("", "Unsupported core or missing external-controller", 1)

                val url = "http://127.0.0.1:${apiConfig.port}/configs?force=true"
                val body = "{}".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val requestBuilder = Request.Builder().url(url).put(body)
                if (!apiConfig.secret.isNullOrEmpty()) {
                    requestBuilder.header("Authorization", "Bearer ${apiConfig.secret}")
                }

                okHttpClient.newCall(requestBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val errBody = resp.body.string().take(300)
                        return@withContext ShellExecutor.Result(
                            "",
                            "HTTP ${resp.code}: ${resp.message} ${errBody}".trim(),
                            1
                        )
                    }
                    val text = resp.body.string()
                    ShellExecutor.Result(text, "", 0)
                }
            } catch (e: Exception) {
                ShellExecutor.Result("", e.message ?: "Reload request failed", 1)
            }
        }
    }

    fun parseModuleVersion(modulePropText: String): String? {
        // module.prop usually contains: version=xxx
        val line = modulePropText.lineSequence().firstOrNull { it.trim().startsWith("version=") } ?: return null
        return line.substringAfter("=").trim().takeIf { it.isNotEmpty() }
    }

    private fun repoApiBase(): String {
        return if (BuildConfig.FLAVOR == "bfr") {
            "https://api.github.com/repos/taamarin/box_for_magisk"
        } else {
            "https://api.github.com/repos/boxproxy/box"
        }
    }

    private fun appRepoApiBase(): String = "https://api.github.com/repos/boxproxy/app"

    private fun appAssetFlavorToken(): String = if (BuildConfig.FLAVOR == "bfr") "bfr" else "box"

    private fun appBuildTypeToken(): String = BuildConfig.BUILD_TYPE.lowercase()

    private val appAssetRegex = Regex(
        pattern = """^app-(\d{8}-\d{4})-(box|bfr)-(debug|release)\.apk$""",
        option = RegexOption.IGNORE_CASE
    )

    suspend fun fetchLatestReleaseInfo(repoApi: String = repoApiBase()): com.box.app.data.model.ReleaseInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = repoApi.trimEnd('/') + "/releases"
                val req = Request.Builder().get().url(url).build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val body = resp.body.string().orEmpty()
                    if (body.isBlank()) return@withContext null
                    val arr = JSONArray(body)
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        if (o.optBoolean("draft", false)) continue
                        val tag = o.optString("tag_name").orEmpty()
                        val name = o.optString("name").orEmpty()
                        val html = o.optString("html_url").orEmpty()
                        if (tag.isBlank() && name.isBlank()) continue
                        return@withContext com.box.app.data.model.ReleaseInfo(
                            tag = tag,
                            name = name,
                            url = html,
                            isPrerelease = o.optBoolean("prerelease", false)
                        )
                    }
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun checkForUpdates(): com.box.app.data.model.UpdateCheckResult {
        return checkForModuleUpdates()
    }

    suspend fun checkForModuleUpdates(): com.box.app.data.model.UpdateCheckResult {
        return withContext(Dispatchers.IO) {
            try {
                val currentVersion = getCurrentModuleVersion()
                val releases = fetchAllReleases()
                
                if (releases.isEmpty()) {
                    return@withContext com.box.app.data.model.UpdateCheckResult(
                        hasUpdate = false,
                        stableRelease = null,
                        prereleaseRelease = null,
                        currentVersion = currentVersion,
                        recommendedRelease = null
                    )
                }
                
                // 分离正式版和预发布版
                val stableReleases = releases.filter { !it.isPrerelease }
                val prereleases = releases.filter { it.isPrerelease }
                
                val latestStable = stableReleases.firstOrNull()
                val latestPrerelease = prereleases.firstOrNull()
                
                // 改进的版本逻辑
                val (shouldShowStable, shouldShowPrerelease, recommendedRelease) = determineVersionsToShow(
                    currentVersion, latestStable, latestPrerelease
                )
                
                val hasUpdate = recommendedRelease != null && 
                    !isVersionSame(currentVersion, recommendedRelease.tag)
                
                com.box.app.data.model.UpdateCheckResult(
                    hasUpdate = hasUpdate,
                    stableRelease = if (shouldShowStable) latestStable else null,
                    prereleaseRelease = if (shouldShowPrerelease) latestPrerelease else null,
                    currentVersion = currentVersion,
                    recommendedRelease = recommendedRelease
                )
            } catch (e: Exception) {
                com.box.app.data.model.UpdateCheckResult(
                    hasUpdate = false,
                    stableRelease = null,
                    prereleaseRelease = null,
                    currentVersion = "Unknown",
                    recommendedRelease = null
                )
            }
        }
    }

    private data class AppAssetInfo(
        val fileName: String,
        val version: String,
        val flavor: String,
        val buildType: String,
        val downloadUrl: String
    )

    suspend fun checkForAppUpdates(currentAppVersion: String): com.box.app.data.model.UpdateCheckResult {
        return withContext(Dispatchers.IO) {
            try {
                val flavor = appAssetFlavorToken()
                val buildType = appBuildTypeToken()
                val currentVersion = normalizeAppVersion(currentAppVersion)
                val currentFileName = buildCurrentAppFileName(currentVersion, flavor, buildType)

                val release = fetchAppLatestRelease()
                    ?: return@withContext com.box.app.data.model.UpdateCheckResult(
                        hasUpdate = false,
                        stableRelease = null,
                        prereleaseRelease = null,
                        currentVersion = currentVersion,
                        recommendedRelease = null
                    )

                val latestAssetWithRelease = parseAppAssetsFromRelease(release)
                    .asSequence()
                    .filter { it.flavor == flavor && it.buildType == buildType }
                    .maxByOrNull { appVersionSortKey(it.version) ?: Long.MIN_VALUE }
                    ?: return@withContext com.box.app.data.model.UpdateCheckResult(
                        hasUpdate = false,
                        stableRelease = null,
                        prereleaseRelease = null,
                        currentVersion = currentVersion,
                        recommendedRelease = null
                    )
                val latestAsset = latestAssetWithRelease

                val latestVersionKey = appVersionSortKey(latestAsset.version)
                val currentVersionKey = appVersionSortKey(currentVersion)
                val hasUpdate = when {
                    latestVersionKey != null && currentVersionKey != null -> latestVersionKey > currentVersionKey
                    else -> !latestAsset.fileName.equals(currentFileName, ignoreCase = true)
                }

                val latestReleaseInfo = com.box.app.data.model.ReleaseInfo(
                    tag = latestAsset.version,
                    name = latestAsset.fileName,
                    url = release.optString("html_url").orEmpty(),
                    isPrerelease = release.optBoolean("prerelease", false),
                    body = release.optString("body").orEmpty(),
                    publishedAt = release.optString("published_at").orEmpty(),
                    downloadUrl = latestAsset.downloadUrl,
                    commitSha = ""
                )

                com.box.app.data.model.UpdateCheckResult(
                    hasUpdate = hasUpdate,
                    stableRelease = if (hasUpdate) latestReleaseInfo else null,
                    prereleaseRelease = null,
                    currentVersion = currentFileName,
                    recommendedRelease = if (hasUpdate) latestReleaseInfo else null
                )
            } catch (_: Exception) {
                com.box.app.data.model.UpdateCheckResult(
                    hasUpdate = false,
                    stableRelease = null,
                    prereleaseRelease = null,
                    currentVersion = buildCurrentAppFileName(
                        normalizeAppVersion(currentAppVersion),
                        appAssetFlavorToken(),
                        appBuildTypeToken()
                    ),
                    recommendedRelease = null
                )
            }
        }
    }

    private fun determineVersionsToShow(
        currentVersion: String,
        stableRelease: com.box.app.data.model.ReleaseInfo?,
        prereleaseRelease: com.box.app.data.model.ReleaseInfo?
    ): Triple<Boolean, Boolean, com.box.app.data.model.ReleaseInfo?> {
        val isCurrentPrerelease = currentVersion.contains("(") && currentVersion.contains(")")
        val currentBaseVersion = extractBaseVersion(currentVersion)
        
        // 检查是否与当前版本相同
        val stableIsSame = stableRelease?.let {
            extractBaseVersion(it.tag) == currentBaseVersion
        } ?: false
        
        val prereleaseIsSame = prereleaseRelease?.let {
            extractBaseVersion(it.tag) == currentBaseVersion && 
            extractCommitFromVersion(it.tag) == extractCommitFromVersion(currentVersion)
        } ?: false
        
        return when {
            isCurrentPrerelease -> {
                // 当前是预发布版
                val showStable = stableRelease != null && !stableIsSame
                val showPrerelease = prereleaseRelease != null && !prereleaseIsSame
                val recommended = when {
                    showPrerelease -> prereleaseRelease
                    showStable -> stableRelease
                    else -> null
                }
                Triple(showStable, showPrerelease, recommended)
            }
            else -> {
                // 当前是正式版
                val showStable = stableRelease != null && !stableIsSame
                val showPrerelease = prereleaseRelease != null
                val recommended = when {
                    showStable -> stableRelease
                    showPrerelease -> prereleaseRelease
                    else -> null
                }
                Triple(showStable, showPrerelease, recommended)
            }
        }
    }

    private fun extractBaseVersion(version: String): String {
        // 提取基础版本号，如 "1.2.6(a53b38c)" -> "1.2.6"
        return version.substringBefore("(").trim()
    }

    private fun extractCommitFromVersion(version: String): String {
        // 提取commit SHA，如 "1.2.6(a53b38c)" -> "a53b38c"
        val regex = "\\(([a-f0-9]{6,40})\\)".toRegex()
        return regex.find(version)?.groupValues?.get(1) ?: ""
    }

    private fun isVersionSame(version1: String, version2: String): Boolean {
        val base1 = extractBaseVersion(version1)
        val base2 = extractBaseVersion(version2)
        val commit1 = extractCommitFromVersion(version1)
        val commit2 = extractCommitFromVersion(version2)
        
        // 如果基础版本不同，肯定不同
        if (base1 != base2) return false
        
        // 如果都有commit SHA，比较commit SHA
        if (commit1.isNotEmpty() && commit2.isNotEmpty()) {
            return commit1 == commit2
        }
        
        // 如果一个有commit SHA一个没有，认为不同
        if (commit1.isNotEmpty() != commit2.isNotEmpty()) return false
        
        // 都没有commit SHA，只比较基础版本
        return base1 == base2
    }

    private suspend fun fetchAllReleases(): List<com.box.app.data.model.ReleaseInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val base = repoApiBase().trimEnd('/')

                // 获取正常 releases
                val normalReleases = fetchReleasesFromUrl("$base/releases")

                // 获取 debug releases
                val debugReleases = fetchReleasesFromUrl("$base/releases/tags/debug")

                (normalReleases + debugReleases).distinctBy { it.tag }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private suspend fun fetchReleasesFromUrl(url: String): List<com.box.app.data.model.ReleaseInfo> {
        return try {
            val req = Request.Builder().get().url(url).build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body.string().orEmpty()
                if (body.isBlank()) return emptyList()
                
                val releases = mutableListOf<com.box.app.data.model.ReleaseInfo>()
                
                if (url.contains("/tags/debug")) {
                    // 处理单个debug release
                    val o = JSONObject(body)
                    if (!o.optBoolean("draft", false)) {
                        val release = parseReleaseObject(o)
                        if (release != null) releases.add(release)
                    }
                } else {
                    // 处理releases数组
                    val arr = JSONArray(body)
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        if (o.optBoolean("draft", false)) continue
                        val release = parseReleaseObject(o)
                        if (release != null) releases.add(release)
                    }
                }
                
                releases
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseReleaseObject(o: JSONObject): com.box.app.data.model.ReleaseInfo? {
        val tagName = o.optString("tag_name").orEmpty()
        val name = o.optString("name").orEmpty()
        val html = o.optString("html_url").orEmpty()
        val bodyText = o.optString("body").orEmpty()
        val publishedAt = o.optString("published_at").orEmpty()
        
        if (tagName.isBlank() && name.isBlank()) return null

        val fullVersionRegex = """(\d+\.\d+\.\d+)\(([a-f0-9]{7})\)""".toRegex()
        val nameMatch = fullVersionRegex.find(name)

        val fullVersion = when {
            nameMatch != null -> {
                val version = nameMatch.groupValues[1]
                val sha = nameMatch.groupValues[2]
                "$version($sha)"
            }
            tagName.isNotBlank() -> {
                tagName.removePrefix("v").trim()
            }
            else -> name.trim()
        }
        
        // 查找模块包下载链接
        val assets = o.optJSONArray("assets")
        var downloadUrl = ""
        if (assets != null) {
            for (j in 0 until assets.length()) {
                val asset = assets.optJSONObject(j) ?: continue
                val assetName = asset.optString("name").orEmpty()
                if (
                    assetName.endsWith(".zip", ignoreCase = true) ||
                    assetName.endsWith(".tar", ignoreCase = true) ||
                    assetName.endsWith(".tar.gz", ignoreCase = true) ||
                    assetName.endsWith(".tgz", ignoreCase = true)
                ) {
                    downloadUrl = asset.optString("browser_download_url").orEmpty()
                    break
                }
            }
        }
        
        // 提取commit SHA
        val commitSha = extractCommitFromVersion(fullVersion).ifBlank {
            extractCommitSha(tagName, bodyText)
        }
        
        // 判断是否为预发布版：包含括号或者是debug标签
        val isPrerelease = o.optBoolean("prerelease", false) || 
                          tagName == "debug" || 
                          fullVersion.contains("(") ||
                          name.contains("debug", ignoreCase = true)
        
        return com.box.app.data.model.ReleaseInfo(
            tag = fullVersion,
            name = name,
            url = html,
            isPrerelease = isPrerelease,
            body = bodyText,
            publishedAt = publishedAt,
            downloadUrl = downloadUrl,
            commitSha = commitSha
        )
    }

    private fun extractCommitSha(tag: String, body: String): String {
        // 从tag中提取commit SHA (如果tag包含括号中的SHA)
        val tagShaRegex = "\\(([a-f0-9]{6,40})\\)".toRegex()
        tagShaRegex.find(tag)?.groupValues?.get(1)?.let { return it }
        
        // 从body中提取commit SHA
        val bodyShaRegex = "([a-f0-9]{7,40})".toRegex()
        bodyShaRegex.find(body)?.groupValues?.get(1)?.let { return it }
        
        return ""
    }

    private suspend fun fetchAppLatestRelease(): JSONObject? {
        return try {
            val url = appRepoApiBase().trimEnd('/') + "/releases/latest"
            val req = Request.Builder().get().url(url).build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body.string().orEmpty()
                if (body.isBlank()) return null
                val o = JSONObject(body)
                if (o.optBoolean("draft", false)) return null
                o
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseAppAssetsFromRelease(release: JSONObject): List<AppAssetInfo> {
        val assets = release.optJSONArray("assets") ?: return emptyList()
        val result = mutableListOf<AppAssetInfo>()
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val fileName = asset.optString("name").orEmpty()
            val match = appAssetRegex.find(fileName) ?: continue
            val version = match.groupValues.getOrNull(1).orEmpty()
            val flavor = match.groupValues.getOrNull(2).orEmpty().lowercase()
            val buildType = match.groupValues.getOrNull(3).orEmpty().lowercase()
            val downloadUrl = asset.optString("browser_download_url").orEmpty()
            if (version.isBlank() || flavor.isBlank() || buildType.isBlank() || downloadUrl.isBlank()) continue
            result.add(
                AppAssetInfo(
                    fileName = fileName,
                    version = version,
                    flavor = flavor,
                    buildType = buildType,
                    downloadUrl = downloadUrl
                )
            )
        }
        return result
    }

    private fun buildCurrentAppFileName(version: String, flavor: String, buildType: String): String {
        return "app-$version-$flavor-$buildType.apk"
    }

    private fun normalizeAppVersion(version: String): String {
        val v = version.trim()
        if (v.isBlank()) return "Unknown"
        return Regex("""(\d{8}-\d{4})""").find(v)?.groupValues?.get(1) ?: v
    }

    private fun appVersionSortKey(version: String): Long? {
        val normalized = normalizeAppVersion(version)
        return normalized.replace("-", "").toLongOrNull()
    }

    private suspend fun getCurrentModuleVersion(): String {
        val prop = runCatching { getModuleProp() }.getOrNull()
        return prop?.let { parseModuleVersion(it) } ?: "Unknown"
    }

    suspend fun getModuleVersion(): String = getCurrentModuleVersion()

    suspend fun downloadApk(url: String, onProgress: (Int) -> Unit = {}): String? {
        // 简化版本，暂时返回null
        return null
    }

    suspend fun getSettings(): String {
        val result = execute("cat /data/adb/box/settings.ini 2>/dev/null")
        return if (result.exitCode == 0) result.stdout else ""
    }

    suspend fun getHotspotClientMacs(): List<String> {
        fun parseSoftApIfacesFromIw(output: String): List<String> {
            var current: String? = null
            val ifaces = mutableListOf<String>()
            output.lineSequence().forEach { raw ->
                val line = raw.trim()
                when {
                    line.startsWith("Interface ") -> {
                        current = line.removePrefix("Interface ").trim().ifBlank { null }
                    }

                    line == "type AP" -> {
                        val iface = current
                        if (iface != null) ifaces.add(iface)
                    }
                }
            }
            return ifaces.distinct()
        }

        val macRegex = Regex("[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}")
        val macs = linkedSetOf<String>()

        val iwRes = execute("iw dev 2>/dev/null")
        val apIfaces = if (iwRes.exitCode == 0) parseSoftApIfacesFromIw(iwRes.stdout) else emptyList()
        if (apIfaces.isEmpty()) return emptyList()

        fun parseStationDump(output: String): List<String> {
            return output.lineSequence()
                .map { it.trim() }
                .mapNotNull { line ->
                    macRegex.find(line)?.value
                }
                .map { it.lowercase() }
                .distinct()
                .toList()
        }

        for (iface in apIfaces) {
            val stationRes = execute("iw dev $iface station dump 2>/dev/null")
            if (stationRes.exitCode == 0) {
                val stations = parseStationDump(stationRes.stdout)
                if (stations.isNotEmpty()) {
                    macs.addAll(stations)
                    continue
                }
            }

            // Fallback (still bound to SoftAP interface only). If hotspot is off, this should usually be empty.
            val neighRes = execute("ip neigh show dev $iface 2>/dev/null")
            if (neighRes.exitCode != 0) continue
            neighRes.stdout.lineSequence()
                .filterNot { it.contains(" FAILED") }
                .forEach { line ->
                    macRegex.findAll(line).forEach { m ->
                        macs.add(m.value.lowercase())
                    }
                }
        }

        return macs.toList().sorted()
    }

    fun parseBooleanSetting(settingsText: String, key: String): Boolean? {
        val match = Regex("^${Regex.escape(key)}=\\\"?(true|false)\\\"?$", RegexOption.MULTILINE)
            .find(settingsText)
            ?: return null
        return match.groupValues[1].lowercase() == "true"
    }

    suspend fun getBooleanSetting(key: String): Boolean? {
        val settingsText = getSettings()
        if (settingsText.isBlank()) return null
        return parseBooleanSetting(settingsText, key)
    }

    suspend fun updateSetting(key: String, value: String): Boolean {
        val originalValueResult = execute("grep '^$key=' /data/adb/box/settings.ini | cut -d '=' -f 2-")
        val needsQuotes = originalValueResult.stdout.trim().startsWith("\"")

        val finalValue = if (needsQuotes) "\"$value\"" else value
        val escapedValue = finalValue.replace("/", "\\/").replace("&", "\\&")
        val command = "sed -i 's/^$key=.*/$key=$escapedValue/g' /data/adb/box/settings.ini"
        val result = execute(command)
        return result.exitCode == 0
    }

    suspend fun updateBooleanSetting(key: String, value: Boolean): Boolean {
        val stringValue = "\"$value\""
        val command = "sed -i 's/^$key=.*/$key=$stringValue/g' /data/adb/box/settings.ini"
        val result = execute(command)
        return result.exitCode == 0
    }

    suspend fun updateArraySetting(key: String, values: List<String>): Boolean {
        val arrayValue = if (values.isEmpty()) {
            "()"
        } else {
            "(\"${values.joinToString("\" \"")}\")"
        }
        val escapedValue = arrayValue.replace("/", "\\/").replace("&", "\\&")
        val command = "sed -i 's/^$key=.*/$key=$escapedValue/g' /data/adb/box/settings.ini"
        val result = execute(command)
        return result.exitCode == 0
    }

    fun parseBashArray(arrayString: String): List<String> {
        val trimmed = arrayString.trim()
        if (trimmed == "()" || trimmed.isEmpty()) {
            return emptyList()
        }

        val content = trimmed.removePrefix("(").removeSuffix(")")
        val values = mutableListOf<String>()
        val regex = "\"([^\"]*)\"".toRegex()
        regex.findAll(content).forEach { match ->
            values.add(match.groupValues[1])
        }
        return values
    }

    suspend fun startService(): Boolean {
        clearPidCache()
        val result = execute("/data/adb/box/scripts/box.service start; /data/adb/box/scripts/box.iptables enable")
        return result.exitCode == 0
    }

    suspend fun stopService(): Boolean {
        clearPidCache()
        val result = execute("/data/adb/box/scripts/box.iptables disable; /data/adb/box/scripts/box.service stop")
        return result.exitCode == 0
    }

    suspend fun restartService(): Boolean {
        clearPidCache()
        val result = execute("/data/adb/box/scripts/box.service restart")
        return result.exitCode == 0
    }

    fun clearPidCache() {
        cachedPid = null
    }

    suspend fun getExternalControllerConfig(): ApiConfig? {
        val settings = getSettings()
        if (settings.isBlank()) return null

        val binPattern = Pattern.compile("^bin_name=\"?(.*?)\"?$", Pattern.MULTILINE)
        val binMatcher = binPattern.matcher(settings)
        val binName = if (binMatcher.find()) binMatcher.group(1) else null
        if (binName.isNullOrBlank()) return null

        val configFileKey = when (binName) {
            "mihomo" -> "name_mihomo_config"
            "clash" -> "name_clash_config"
            "sing-box" -> "name_sing_config"
            else -> "name_${binName}_config"
        }
        val configPattern = Pattern.compile("^${configFileKey}=\"?(.*?)\"?$", Pattern.MULTILINE)
        val configMatcher = configPattern.matcher(settings)
        val activeConfig = if (configMatcher.find()) configMatcher.group(1) else null
        if (activeConfig.isNullOrBlank()) return null

        val configPath = "/data/adb/box/$binName/$activeConfig"

        return when (binName) {
            "mihomo", "clash" -> {
                val ecLineRes = execute("grep -m1 -E '^[[:space:]]*external-controller:' '$configPath' 2>/dev/null || true")
                val ecLineRaw = ecLineRes.stdout.lineSequence().firstOrNull()?.trim().orEmpty()
                val ecLine = ecLineRaw.substringBefore('#').trim()

                // Prefer host:port, but also accept ':port' or 'port'.
                var port = Regex("external-controller:\\s*['\"]?([0-9.]+):(\\d{2,5})", RegexOption.IGNORE_CASE)
                    .find(ecLine)?.groupValues?.get(2)?.toIntOrNull()
                if (port == null) {
                    port = Regex("external-controller:\\s*['\"]?:?(\\d{2,5})", RegexOption.IGNORE_CASE)
                        .find(ecLine)?.groupValues?.get(1)?.toIntOrNull()
                }
                if (port == null) return null

                val secretLineRes = execute("grep -m1 -E '^[[:space:]]*secret:' '$configPath' 2>/dev/null || true")
                val secretLineRaw = secretLineRes.stdout.lineSequence().firstOrNull().orEmpty()
                val secretLine = secretLineRaw.substringBefore('#')
                val secret = Regex("secret:\\s*['\"]?([^'\"\\s]+)['\"]?", RegexOption.IGNORE_CASE)
                    .find(secretLine)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

                ApiConfig(port, secret)
            }
            "sing-box" -> {
                // sing-box config is JSON; external_controller is nested under experimental.clash_api
                val jsonRes = execute("cat '$configPath' 2>/dev/null || true")
                val jsonText = jsonRes.stdout
                if (jsonText.isBlank()) return null

                try {
                    val root = JSONObject(jsonText)
                    val experimental = root.optJSONObject("experimental")
                    val clashApi = experimental?.optJSONObject("clash_api")

                    val externalController = clashApi
                        ?.optString("external_controller")
                        ?.takeIf { it.isNotBlank() }

                    val port = externalController
                        ?.substringAfterLast(":")
                        ?.toIntOrNull()
                        ?: return null

                    val secret = clashApi
                        .optString("secret")
                        .trim()
                        .takeIf { it.isNotEmpty() }

                    ApiConfig(port, secret)
                } catch (_: Exception) {
                    null
                }
            }
            else -> null
        }
    }

    suspend fun getClashVersionInfo(): ClashVersionInfo? {
        val json = executeClashJsonRequest("/version") ?: return null
        val version = json.optString("version").trim()
        if (version.isEmpty()) return null
        val name = json.optString("name").trim().ifEmpty {
            val settings = getSettings()
            Regex("^bin_name=\"?(.*?)\"?$", RegexOption.MULTILINE)
                .find(settings)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.ifEmpty { "clash" }
                ?: "clash"
        }
        return ClashVersionInfo(
            name = name,
            version = version,
            premium = json.optBoolean("premium", false),
            meta = json.optBoolean("meta", false)
        )
    }

    suspend fun getClashConfigInfo(): ClashConfigInfo? {
        val json = executeClashJsonRequest("/configs") ?: return null
        val externalController = json.optString("external-controller")
            .ifBlank { json.optString("externalController") }
        return ClashConfigInfo(
            mode = json.optString("mode", "rule"),
            logLevel = json.optString("log-level", "info"),
            allowLan = json.optBoolean("allow-lan", false),
            ipv6 = json.optBoolean("ipv6", false),
            mixedPort = json.optInt("mixed-port").takeIf { it > 0 },
            socksPort = json.optInt("socks-port").takeIf { it > 0 },
            httpPort = json.optInt("port").takeIf { it > 0 },
            redirPort = json.optInt("redir-port").takeIf { it > 0 },
            tproxyPort = json.optInt("tproxy-port").takeIf { it > 0 },
            externalController = externalController,
            secretEnabled = json.optString("secret").isNotBlank()
        )
    }

    suspend fun updateClashConfigs(
        mode: String? = null,
        logLevel: String? = null
    ): Boolean {
        if (mode == null && logLevel == null) return true
        val payload = JSONObject()
        if (!mode.isNullOrBlank()) payload.put("mode", mode)
        if (!logLevel.isNullOrBlank()) payload.put("log-level", logLevel)
        return executeClashTextRequest(
            path = "/configs",
            method = "PATCH",
            jsonBody = payload
        ) != null
    }

    suspend fun getProxyGroupsFromApi(): List<ClashProxyGroup> = withContext(Dispatchers.IO) {
        val json = executeClashJsonRequest("/proxies") ?: return@withContext emptyList()
        val proxies = json.optJSONObject("proxies") ?: return@withContext emptyList()
        val names = mutableListOf<String>()
        val keys = proxies.keys()
        while (keys.hasNext()) names += keys.next()

        return@withContext names
            .sorted()
            .mapNotNull { name ->
                val item = proxies.optJSONObject(name) ?: return@mapNotNull null
                val optionsArr = item.optJSONArray("all") ?: return@mapNotNull null
                val options = buildList {
                    for (i in 0 until optionsArr.length()) {
                        val value = optionsArr.optString(i).trim()
                        if (value.isNotEmpty()) add(value)
                    }
                }.distinct()
                if (options.size <= 1) return@mapNotNull null
                ClashProxyGroup(
                    name = name,
                    type = item.optString("type"),
                    now = item.optString("now"),
                    options = options,
                    alive = item.optBoolean("alive", true)
                )
            }
    }

    suspend fun selectProxy(groupName: String, proxyName: String): Boolean {
        if (groupName.isBlank() || proxyName.isBlank()) return false
        val payload = JSONObject().put("name", proxyName)
        return executeClashTextRequest(
            path = "/proxies/${encodePathSegment(groupName)}",
            method = "PUT",
            jsonBody = payload
        ) != null
    }

    suspend fun getRulesFromApi(): List<ClashRuleInfo> = withContext(Dispatchers.IO) {
        val json = executeClashJsonRequest("/rules") ?: return@withContext emptyList()
        val rules = json.optJSONArray("rules") ?: return@withContext emptyList()
        return@withContext buildList {
            for (i in 0 until rules.length()) {
                val rule = rules.optJSONObject(i) ?: continue
                add(
                    ClashRuleInfo(
                        type = rule.optString("type"),
                        payload = rule.optString("payload"),
                        proxy = rule.optString("proxy")
                    )
                )
            }
        }
    }

    suspend fun getProviderListFromApi(): List<ClashProviderInfo> = withContext(Dispatchers.IO) {
        val json = getProvidersFromApi() ?: return@withContext emptyList()
        val providers = json.optJSONObject("providers") ?: return@withContext emptyList()
        val names = mutableListOf<String>()
        val keys = providers.keys()
        while (keys.hasNext()) names += keys.next()

        return@withContext names
            .sorted()
            .mapNotNull { name ->
                val provider = providers.optJSONObject(name) ?: return@mapNotNull null
                val proxies = provider.optJSONArray("proxies")
                ClashProviderInfo(
                    name = name,
                    type = provider.optString("type"),
                    vehicleType = provider.optString("vehicleType"),
                    updatedAt = provider.optString("updatedAt"),
                    proxyCount = proxies?.length() ?: 0
                )
            }
    }

    suspend fun getProxyTrafficFromApi(): TrafficStats? {
        val apiConfig = cachedApiConfig ?: getExternalControllerConfig()?.also { cachedApiConfig = it } ?: return null
        return try {
            val url = "http://127.0.0.1:${apiConfig.port}/connections"
            val requestBuilder = Request.Builder().get().url(url)
            if (!apiConfig.secret.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer ${apiConfig.secret}")
            }

            val currentTime = System.currentTimeMillis()
            okHttpClient.newCall(requestBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body.string().orEmpty()
                if (body.isBlank()) return null

                val json = JSONObject(body)
                val connections = json.optJSONArray("connections")
                var totalDownload = 0L
                var totalUpload = 0L
                val filters = proxyTrafficFilterChains

                val currentConnectionsMap = mutableMapOf<String, Pair<Long, Long>>()
                val hostSpeedMap = mutableMapOf<String, Triple<Long, Long, List<String>>>() // host -> (downBps, upBps, chains)

                val deltaTimeForConn = if (lastProxyTrafficTime > 0L) {
                    (currentTime - lastProxyTrafficTime) / 1000.0
                } else {
                    0.0
                }
                if (connections != null) {
                    for (i in 0 until connections.length()) {
                        val conn = connections.optJSONObject(i) ?: continue
                        // Filter non-proxy connections (box.app behavior).
                        val chains = conn.optJSONArray("chains")
                        var shouldFilter = false
                        if (chains != null) {
                            for (j in 0 until chains.length()) {
                                val chain = chains.optString(j)
                                if (filters.contains(chain.trim().uppercase())) {
                                    shouldFilter = true
                                    break
                                }
                            }
                        }
                        if (shouldFilter) continue
                        val id = conn.optString("id")
                        val down = conn.optLong("download", 0L)
                        val up = conn.optLong("upload", 0L)
                        totalDownload += down
                        totalUpload += up

                        if (id.isNotEmpty()) {
                            currentConnectionsMap[id] = Pair(down, up)
                        }

                        val prev = previousConnectionsMap[id]
                        if (prev != null && deltaTimeForConn > 0.0) {
                            val downDelta = (down - prev.first).coerceAtLeast(0L)
                            val upDelta = (up - prev.second).coerceAtLeast(0L)
                            val connDownSpeed = (downDelta / deltaTimeForConn).toLong().coerceAtLeast(0L)
                            val connUpSpeed = (upDelta / deltaTimeForConn).toLong().coerceAtLeast(0L)

                            val metadata = conn.optJSONObject("metadata")
                            val host = metadata?.optString("sniffHost")?.takeIf { it.isNotBlank() }
                                ?: metadata?.optString("host")?.takeIf { it.isNotBlank() }
                                ?: metadata?.optString("destinationIP")?.takeIf { it.isNotBlank() }
                                ?: ""

                            val chainsList = mutableListOf<String>()
                            if (chains != null) {
                                for (j in chains.length() - 1 downTo 0) {
                                    chainsList.add(chains.optString(j))
                                }
                            }

                            if (host.isNotBlank() && (connDownSpeed > 0L || connUpSpeed > 0L)) {
                                val existing = hostSpeedMap[host]
                                if (existing != null) {
                                    hostSpeedMap[host] = Triple(
                                        existing.first + connDownSpeed,
                                        existing.second + connUpSpeed,
                                        chainsList
                                    )
                                } else {
                                    hostSpeedMap[host] = Triple(connDownSpeed, connUpSpeed, chainsList)
                                }
                            }
                        }
                    }
                }

                val downSpeed: Long
                val upSpeed: Long
                if (lastProxyTrafficTime > 0L) {
                    val deltaTime = (currentTime - lastProxyTrafficTime) / 1000.0
                    if (deltaTime > 0) {
                        val deltaDown = (totalDownload - lastProxyTrafficDown).coerceAtLeast(0L)
                        val deltaUp = (totalUpload - lastProxyTrafficUp).coerceAtLeast(0L)
                        downSpeed = (deltaDown / deltaTime).toLong()
                        upSpeed = (deltaUp / deltaTime).toLong()
                    } else {
                        downSpeed = 0L
                        upSpeed = 0L
                    }
                } else {
                    downSpeed = 0L
                    upSpeed = 0L
                }

                lastProxyTrafficDown = totalDownload
                lastProxyTrafficUp = totalUpload
                lastProxyTrafficTime = currentTime

                previousConnectionsMap.clear()
                previousConnectionsMap.putAll(currentConnectionsMap)

                var fastestDownload: Pair<String, Triple<Long, Long, List<String>>>? = null
                var fastestUpload: Pair<String, Triple<Long, Long, List<String>>>? = null
                for ((host, speeds) in hostSpeedMap) {
                    if (speeds.first > 0 && (fastestDownload == null || speeds.first > fastestDownload.second.first)) {
                        fastestDownload = Pair(host, speeds)
                    }
                    if (speeds.second > 0 && (fastestUpload == null || speeds.second > fastestUpload.second.second)) {
                        fastestUpload = Pair(host, speeds)
                    }
                }

                val fastestDown = fastestDownload?.let { (host, speeds) ->
                    FastestConnection(
                        host = host,
                        chains = speeds.third,
                        downloadBytesPerSec = speeds.first,
                        uploadBytesPerSec = 0L
                    )
                }
                val fastestUp = fastestUpload?.let { (host, speeds) ->
                    FastestConnection(
                        host = host,
                        chains = speeds.third,
                        downloadBytesPerSec = 0L,
                        uploadBytesPerSec = speeds.second
                    )
                }

                TrafficStats(
                    downBytesPerSec = downSpeed,
                    upBytesPerSec = upSpeed,
                    fastestDownload = fastestDown,
                    fastestUpload = fastestUp
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private val emptySnapshot = ConnectionsSnapshot(0L, 0L, 0L, emptyList())

    suspend fun getAllConnections(): ConnectionsSnapshot = withContext(Dispatchers.IO) {
        val apiConfig = cachedApiConfig ?: getExternalControllerConfig()?.also { cachedApiConfig = it } ?: return@withContext emptySnapshot
        return@withContext try {
            val url = "http://127.0.0.1:${apiConfig.port}/connections"
            val requestBuilder = Request.Builder().get().url(url)
            if (!apiConfig.secret.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer ${apiConfig.secret}")
            }
            val resp = okHttpClient.newCall(requestBuilder.build()).execute()
            resp.use { r ->
                if (!r.isSuccessful) return@withContext emptySnapshot
                val body = r.body.string().orEmpty()
                if (body.isBlank()) return@withContext emptySnapshot
                val json = JSONObject(body)
                val downloadTotal = json.optLong("downloadTotal", 0L)
                val uploadTotal = json.optLong("uploadTotal", 0L)
                val memory = json.optLong("memory", 0L)
                val conns = json.optJSONArray("connections") ?: return@withContext ConnectionsSnapshot(downloadTotal, uploadTotal, memory, emptyList())
                val result = mutableListOf<ConnectionInfo>()
                for (i in 0 until conns.length()) {
                    val c = conns.optJSONObject(i) ?: continue
                    val metadata = c.optJSONObject("metadata")
                    val chainArr = c.optJSONArray("chains")
                    val chainList = mutableListOf<String>()
                    if (chainArr != null) {
                        for (j in 0 until chainArr.length()) chainList.add(chainArr.optString(j))
                    }
                    val sniffHost = metadata?.optString("sniffHost").orEmpty()
                    val metaHost = metadata?.optString("host").orEmpty()
                    val destIP = metadata?.optString("destinationIP").orEmpty()
                    val host = sniffHost.takeIf { it.isNotBlank() }
                        ?: metaHost.takeIf { it.isNotBlank() }
                        ?: destIP.takeIf { it.isNotBlank() }
                        ?: ""
                    // destinationGeoIP 是一个数组，取第一个元素
                    val geoArr = metadata?.optJSONArray("destinationGeoIP")
                    val geoIP = if (geoArr != null && geoArr.length() > 0) geoArr.optString(0) else ""
                    result.add(
                        ConnectionInfo(
                            id = c.optString("id"),
                            host = host,
                            destinationIP = destIP,
                            destinationPort = metadata?.optString("destinationPort").orEmpty(),
                            sourceIP = metadata?.optString("sourceIP").orEmpty(),
                            sourcePort = metadata?.optString("sourcePort").orEmpty(),
                            network = metadata?.optString("network").orEmpty(),
                            type = metadata?.optString("type").orEmpty(),
                            chains = chainList,
                            rule = c.optString("rule"),
                            rulePayload = c.optString("rulePayload"),
                            download = c.optLong("download", 0L),
                            upload = c.optLong("upload", 0L),
                            start = c.optString("start"),
                            process = metadata?.optString("process").orEmpty(),
                            processPath = metadata?.optString("processPath").orEmpty(),
                            sniffHost = sniffHost,
                            destinationGeoIP = geoIP,
                            isAlive = true
                        )
                    )
                }
                ConnectionsSnapshot(downloadTotal, uploadTotal, memory, result)
            }
        } catch (_: Exception) {
            emptySnapshot
        }
    }

    suspend fun closeConnection(id: String): Boolean = withContext(Dispatchers.IO) {
        val apiConfig = cachedApiConfig ?: getExternalControllerConfig()?.also { cachedApiConfig = it } ?: return@withContext false
        return@withContext try {
            val url = "http://127.0.0.1:${apiConfig.port}/connections/$id"
            val requestBuilder = Request.Builder().delete("".toRequestBody(null)).url(url)
            if (!apiConfig.secret.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer ${apiConfig.secret}")
            }
            okHttpClient.newCall(requestBuilder.build()).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun closeAllConnections(): Boolean = withContext(Dispatchers.IO) {
        val apiConfig = cachedApiConfig ?: getExternalControllerConfig()?.also { cachedApiConfig = it } ?: return@withContext false
        return@withContext try {
            val url = "http://127.0.0.1:${apiConfig.port}/connections"
            val requestBuilder = Request.Builder().delete("".toRequestBody(null)).url(url)
            if (!apiConfig.secret.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer ${apiConfig.secret}")
            }
            okHttpClient.newCall(requestBuilder.build()).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getPanelUrl(): String? {
        val latest = runCatching { getExternalControllerConfig() }.getOrNull()
        val cfg = if (latest != null) {
            cachedApiConfig = latest
            latest
        } else {
            cachedApiConfig ?: return null
        }
        return "http://127.0.0.1:${cfg.port}/ui"
    }

    // Sub-Store
    suspend fun isSubStoreModuleInstalled(): Boolean {
        val result = execute("[ -d /data/adb/modules/sub_store ] && echo 'installed' || echo 'not_installed'")
        return result.exitCode == 0 && result.stdout.trim() == "installed"
    }

    suspend fun getSubStorePort(): String? {
        val result = execute("grep '^sub_store_frontend_port=' /data/adb/sub_store/scripts/sub_store.config 2>/dev/null | sed 's/.*\"\\(.*\\)\"/\\1/'")
        return if (result.exitCode == 0) result.stdout.trim().takeIf { it.isNotBlank() } else null
    }

    suspend fun getSubStoreBackendPort(): String? {
        val result = execute("grep '^sub_store_backend_port=' /data/adb/sub_store/scripts/sub_store.config 2>/dev/null | sed 's/.*\"\\(.*\\)\"/\\1/'")
        return if (result.exitCode == 0) result.stdout.trim().takeIf { it.isNotBlank() } else null
    }

    suspend fun getSubStoreUrl(): String? {
        val installed = isSubStoreModuleInstalled()
        if (!installed) return null
        val frontendPort = getSubStorePort() ?: return null
        val backendPort = getSubStoreBackendPort()

        val base = "http://127.0.0.1:$frontendPort"
        if (backendPort.isNullOrBlank()) return base

        val api = "http://127.0.0.1:$backendPort"
        val encodedApi = URLEncoder.encode(api, "UTF-8")
        return "$base?api=$encodedApi"
    }

    suspend fun getProvidersFromApi(): JSONObject? {
        val apiConfig = cachedApiConfig ?: getExternalControllerConfig()?.also { cachedApiConfig = it } ?: return null
        return try {
            val url = "http://127.0.0.1:${apiConfig.port}/providers/proxies"
            val requestBuilder = Request.Builder().get().url(url)
            if (!apiConfig.secret.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer ${apiConfig.secret}")
            }
            okHttpClient.newCall(requestBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body.string().orEmpty()
                if (body.isBlank()) return null
                JSONObject(body)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun refreshClashProvider(providerName: String): Boolean {
        val apiConfig = cachedApiConfig ?: getExternalControllerConfig()?.also { cachedApiConfig = it } ?: return false
        return try {
            val url = "http://127.0.0.1:${apiConfig.port}/providers/proxies/$providerName"
            val requestBuilder = Request.Builder().put(ByteArray(0).toRequestBody(null)).url(url)
            if (!apiConfig.secret.isNullOrEmpty()) {
                requestBuilder.header("Authorization", "Bearer ${apiConfig.secret}")
            }
            okHttpClient.newCall(requestBuilder.build()).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    // Updates
    suspend fun updateKernel(kernelName: String): Boolean {
        val cmd = if (BuildConfig.FLAVOR == "bfr") {
            "/data/adb/box/scripts/box.tool upkernel"
        } else {
            "/data/adb/box/scripts/box.tool upkernel $kernelName"
        }
        val result = execute(cmd)
        return result.exitCode == 0
    }

    suspend fun updateWebUI(): Boolean {
        val result = execute("/data/adb/box/scripts/box.tool upxui")
        return result.exitCode == 0
    }

    suspend fun updateSubs(): Boolean {
        val result = execute("/data/adb/box/scripts/box.tool subs")
        return result.exitCode == 0
    }

    suspend fun updateCnipList(): Boolean {
        if (BuildConfig.FLAVOR == "bfr") return false
        val result = execute("/data/adb/box/scripts/box.tool upcnip")
        return result.exitCode == 0
    }
}
