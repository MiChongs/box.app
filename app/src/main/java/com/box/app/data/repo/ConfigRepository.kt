package com.box.app.data.repo

import com.box.app.data.backend.BoxApi
import com.box.app.data.backend.ShellExecutor
import com.box.app.data.model.ConfigFsItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object ConfigRepository {

    private const val ROOT_DIR = "/data/adb/box"

    data class Result<T>(val data: T? = null, val error: String? = null)

    private val rootCanonical: File by lazy {
        runCatching { File(ROOT_DIR).canonicalFile }.getOrElse { File(ROOT_DIR).absoluteFile }
    }

    private fun shQuote(raw: String): String {
        return "'" + raw.replace("'", "'\\''") + "'"
    }

    private fun resolveWithinRoot(relativeOrAbs: String): Result<File> {
        val base = if (relativeOrAbs.startsWith("/")) {
            File(relativeOrAbs)
        } else {
            File(rootCanonical, relativeOrAbs)
        }

        val canonical = runCatching { base.canonicalFile }.getOrElse { base.absoluteFile }
        val rootPath = rootCanonical.path
        val targetPath = canonical.path

        if (targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)) {
            return Result(data = canonical)
        }
        return Result(error = "Invalid path")
    }

    private fun validateName(name: String): String? {
        val n = name.trim()
        if (n.isBlank()) return "Invalid name"
        if (n == "." || n == "..") return "Invalid name"
        if (n.contains('/')) return "Invalid name"
        if (n.contains('\u0000')) return "Invalid name"
        return null
    }

    private fun validateConfigFileName(fileName: String): String? {
        validateName(fileName)?.let { return it }
        if (fileName.contains('\n') || fileName.contains('\r')) return "Invalid name"
        if (fileName.contains('"') || fileName.contains('\'')) return "Invalid name"
        return null
    }

    private fun escapeForSedReplacement(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("&", "\\&")
            .replace("/", "\\/")
    }

    suspend fun listPath(relativePath: String): Result<List<ConfigFsItem>> = withContext(Dispatchers.IO) {
        val absFile = resolveWithinRoot(relativePath)
        val abs = absFile.data?.path
            ?: return@withContext Result(error = absFile.error ?: "Invalid path")

        val dirsRes = ShellExecutor.execute("find ${shQuote(abs)} -maxdepth 1 -mindepth 1 -type d 2>/dev/null")
        val filesRes = ShellExecutor.execute("find ${shQuote(abs)} -maxdepth 1 -mindepth 1 -type f 2>/dev/null")

        if (dirsRes.exitCode != 0 || filesRes.exitCode != 0) {
            val err = (dirsRes.stderr + "\n" + filesRes.stderr).trim().ifBlank { "Failed to list path" }
            return@withContext Result(error = err)
        }

        val folders = dirsRes.stdout.lineSequence().mapNotNull { p ->
            val full = p.trim()
            if (full.isBlank()) return@mapNotNull null
            val name = File(full).name
            val rel = full.removePrefix("$ROOT_DIR/")
            ConfigFsItem.Folder(name = name, path = rel)
        }.toList()

        val files = filesRes.stdout.lineSequence().mapNotNull { p ->
            val full = p.trim()
            if (full.isBlank()) return@mapNotNull null
            val name = File(full).name
            val rel = full.removePrefix("$ROOT_DIR/")
            val parentRel = File(full).parent?.removePrefix("$ROOT_DIR/")?.takeIf { it.isNotBlank() }
            val desc = parentRel ?: ""
            ConfigFsItem.File(name = name, path = rel, description = desc)
        }.toList()

        val all = (folders + files).sortedWith(compareBy({ it !is ConfigFsItem.Folder }, { it.name.lowercase() }))
        Result(data = all)
    }

    suspend fun search(query: String): Result<List<ConfigFsItem>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result(data = emptyList())

        val trimmed = query.trim()
        val pattern = "*${trimmed}*"

        val dirsRes = ShellExecutor.execute("find ${shQuote(ROOT_DIR)} -type d -iname ${shQuote(pattern)} 2>/dev/null")
        val filesRes = ShellExecutor.execute("find ${shQuote(ROOT_DIR)} -type f -iname ${shQuote(pattern)} 2>/dev/null")

        val err = (dirsRes.stderr + "\n" + filesRes.stderr).trim()
        if (err.isNotBlank() && (dirsRes.exitCode != 0 || filesRes.exitCode != 0)) {
            return@withContext Result(error = err)
        }

        val folders = dirsRes.stdout.lineSequence().mapNotNull { p ->
            val full = p.trim()
            if (full.isBlank() || full == ROOT_DIR) return@mapNotNull null
            val name = File(full).name
            val rel = full.removePrefix("$ROOT_DIR/")
            ConfigFsItem.Folder(name = name, path = rel)
        }.toList()

        val files = filesRes.stdout.lineSequence().mapNotNull { p ->
            val full = p.trim()
            if (full.isBlank()) return@mapNotNull null
            val name = File(full).name
            val rel = full.removePrefix("$ROOT_DIR/")
            val parentRel = File(full).parent?.removePrefix("$ROOT_DIR/")?.takeIf { it.isNotBlank() }
            val desc = parentRel ?: ""
            ConfigFsItem.File(name = name, path = rel, description = desc)
        }.toList()

        val all = (folders + files).sortedWith(compareBy({ it !is ConfigFsItem.Folder }, { it.name.lowercase() }))
        Result(data = all)
    }

    suspend fun createItem(currentRelativeDir: String, name: String, isFolder: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        validateName(name)?.let { return@withContext Result(error = it) }
        val rel = if (currentRelativeDir.isBlank()) name else "$currentRelativeDir/$name"
        val absFile = resolveWithinRoot(rel)
        val abs = absFile.data?.path
            ?: return@withContext Result(error = absFile.error ?: "Invalid path")

        val check = ShellExecutor.execute("ls -d ${shQuote(abs)} 2>/dev/null")
        if (check.exitCode == 0) return@withContext Result(error = "Already exists")

        val cmd = if (isFolder) "mkdir -p ${shQuote(abs)}" else "touch ${shQuote(abs)}"
        val res = ShellExecutor.execute(cmd)
        if (res.exitCode == 0) Result(data = Unit) else Result(error = res.stdout.ifBlank { res.stderr }.ifBlank { "Create failed" })
    }

    suspend fun deleteItem(item: ConfigFsItem): Result<Unit> = withContext(Dispatchers.IO) {
        val absFile = resolveWithinRoot(item.path)
        val abs = absFile.data?.path
            ?: return@withContext Result(error = absFile.error ?: "Invalid path")
        if (abs == rootCanonical.path) return@withContext Result(error = "Invalid path")

        val cmd = "rm -rf -- ${shQuote(abs)}"
        val res = ShellExecutor.execute(cmd)
        if (res.exitCode == 0) Result(data = Unit) else Result(error = res.stdout.ifBlank { res.stderr }.ifBlank { "Delete failed" })
    }

    suspend fun renameItem(item: ConfigFsItem, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        validateName(newName)?.let { return@withContext Result(error = it) }

        val oldAbsFile = resolveWithinRoot(item.path)
        val oldAbs = oldAbsFile.data?.path
            ?: return@withContext Result(error = oldAbsFile.error ?: "Invalid path")

        val parent = File(oldAbs).parent ?: return@withContext Result(error = "Invalid path")
        val newAbsFile = resolveWithinRoot("${File(parent).name}/${newName}")
        val newAbs = runCatching {
            File(parent, newName).canonicalPath
        }.getOrElse {
            File(parent, newName).absolutePath
        }

        val within = resolveWithinRoot(newAbs)
        if (within.data == null) return@withContext Result(error = within.error ?: "Invalid path")

        val exists = ShellExecutor.execute("ls -d ${shQuote(newAbs)} 2>/dev/null")
        if (exists.exitCode == 0) return@withContext Result(error = "Already exists")

        val res = ShellExecutor.execute("mv ${shQuote(oldAbs)} ${shQuote(newAbs)}")
        if (res.exitCode == 0) Result(data = Unit) else Result(error = res.stdout.ifBlank { res.stderr }.ifBlank { "Rename failed" })
    }

    suspend fun readFile(relativeOrAbsPath: String): Result<String> = withContext(Dispatchers.IO) {
        val absFile = resolveWithinRoot(relativeOrAbsPath)
        val abs = absFile.data?.path
            ?: return@withContext Result(error = absFile.error ?: "Invalid path")
        val res = ShellExecutor.execute("cat ${shQuote(abs)} 2>/dev/null")
        if (res.exitCode == 0) Result(data = res.stdout) else Result(error = res.stdout.ifBlank { res.stderr }.ifBlank { "Read failed" })
    }

    suspend fun writeFile(relativeOrAbsPath: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        val absFile = resolveWithinRoot(relativeOrAbsPath)
        val abs = absFile.data?.path
            ?: return@withContext Result(error = absFile.error ?: "Invalid path")
        val tmp = File.createTempFile("cfg_", ".tmp")
        try {
            tmp.writeText(content)
            val cmd = "cat ${shQuote(tmp.absolutePath)} > ${shQuote(abs)}"
            val res = ShellExecutor.execute(cmd)
            if (res.exitCode == 0) Result(data = Unit) else Result(error = res.stdout.ifBlank { res.stderr }.ifBlank { "Write failed" })
        } finally {
            runCatching { tmp.delete() }
        }
    }

    data class ConfigFile(val name: String, val isActive: Boolean)

    suspend fun listConfigFilesForCore(coreName: String, activeConfigFile: String): Result<List<ConfigFile>> = withContext(Dispatchers.IO) {
        validateName(coreName)?.let { return@withContext Result(error = it) }
        val coreDirFile = resolveWithinRoot(coreName)
        val coreDir = coreDirFile.data?.path
            ?: return@withContext Result(error = coreDirFile.error ?: "Invalid path")

        val res = ShellExecutor.execute("ls -1 ${shQuote(coreDir)} 2>/dev/null")
        if (res.exitCode != 0) return@withContext Result(error = res.stdout.ifBlank { res.stderr }.ifBlank { "List failed" })

        val allowedSuffixes = when (coreName) {
            "sing-box" -> listOf(".json")
            "xray" -> listOf(".json")
            "v2fly" -> listOf(".json")
            "mihomo" -> listOf(".yaml", ".yml")
            "hysteria" -> listOf(".yaml", ".yml")
            else -> listOf(".yaml", ".yml", ".json")
        }

        val files = res.stdout.lineSequence()
            .map { it.trim() }
            .filter { name -> allowedSuffixes.any { suf -> name.endsWith(suf) } }
            .filter { it.isNotBlank() }
            .map { ConfigFile(it, it == activeConfigFile) }
            .toList()
        Result(data = files)
    }

    suspend fun setActiveConfigFile(coreName: String, fileName: String): Result<Unit> = withContext(Dispatchers.IO) {
        validateName(coreName)?.let { return@withContext Result(error = it) }
        validateConfigFileName(fileName)?.let { return@withContext Result(error = it) }

        val allowedSuffixes = when (coreName) {
            "sing-box" -> listOf(".json")
            "xray" -> listOf(".json")
            "v2fly" -> listOf(".json")
            "mihomo" -> listOf(".yaml", ".yml")
            "hysteria" -> listOf(".yaml", ".yml")
            else -> listOf(".yaml", ".yml", ".json")
        }
        if (!allowedSuffixes.any { suf -> fileName.endsWith(suf) }) {
            return@withContext Result(error = "Invalid config format for $coreName")
        }

        val key = when (coreName) {
            "sing-box" -> "name_sing_config"
            else -> "name_${coreName}_config"
        }
        val escaped = escapeForSedReplacement(fileName)
        val settingsFile = resolveWithinRoot("settings.ini")
        val settingsPath = settingsFile.data?.path
            ?: return@withContext Result(error = settingsFile.error ?: "Invalid path")

        val cmd = "sed -i 's/^${key}=.*/${key}=\\\"${escaped}\\\"/' ${shQuote(settingsPath)}"
        val res = ShellExecutor.execute(cmd)
        if (res.exitCode == 0) Result(data = Unit) else Result(error = res.stdout.ifBlank { res.stderr }.ifBlank { "Set active failed" })
    }

    suspend fun pullConfigFileFromUrl(coreName: String, url: String, fileName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userAgent = when (coreName) {
                "mihomo" -> "ClashMeta"
                else -> coreName
            }

            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val req = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", userAgent)
                .build()

            val body = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result(error = "HTTP ${resp.code}: ${resp.message}")
                resp.body.string().orEmpty()
            }

            val relPath = "$coreName/$fileName"
            pullConfigTextToRelativePath(
                relativePath = relPath,
                content = body,
                fileNameForTemp = fileName
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result(error = e.message?.takeIf { it.isNotBlank() } ?: "Download failed")
        }
    }

    suspend fun pullConfigFileFromUrlToRelativePath(
        url: String,
        relativePath: String,
        userAgent: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            val req = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", userAgent)
                .build()

            val body = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result(error = "HTTP ${resp.code}: ${resp.message}")
                resp.body.string().orEmpty()
            }

            val fileName = relativePath.substringAfterLast('/')
            pullConfigTextToRelativePath(
                relativePath = relativePath,
                content = body,
                fileNameForTemp = fileName
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result(error = e.message?.takeIf { it.isNotBlank() } ?: "Download failed")
        }
    }

    private suspend fun pullConfigTextToRelativePath(
        relativePath: String,
        content: String,
        fileNameForTemp: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetFile = resolveWithinRoot(relativePath)
        val target = targetFile.data?.path
            ?: return@withContext Result(error = targetFile.error ?: "Invalid path")
        val tmp = File.createTempFile("box_pull_", "_$fileNameForTemp")
        try {
            tmp.writeText(content)
            val cmd = "cat ${shQuote(tmp.absolutePath)} > ${shQuote(target)}"
            val writeRes = ShellExecutor.execute(cmd)
            if (writeRes.exitCode == 0) Result(data = Unit) else Result(error = writeRes.stdout.ifBlank { writeRes.stderr }.ifBlank { "Write failed" })
        } finally {
            runCatching { tmp.delete() }
        }
    }

    suspend fun warmUpShell(): Unit = withContext(Dispatchers.IO) {
        ShellExecutor.warmUpRootShell(1)
    }

    suspend fun getSettingsIni(): String? {
        return BoxApi.getSettings().takeIf { it.isNotBlank() }
    }

    private fun findSetting(settings: String, key: String): String? {
        val pattern = Pattern.compile("^${key}=\"?(.*?)\"?$", Pattern.MULTILINE)
        val matcher = pattern.matcher(settings)
        return if (matcher.find()) matcher.group(1) else null
    }

    suspend fun getCurrentCoreName(): String? {
        val settings = getSettingsIni() ?: return null
        return findSetting(settings, "bin_name")?.takeIf { it.isNotBlank() }
    }

    suspend fun getActiveConfigFileName(coreName: String): String? {
        val settings = getSettingsIni() ?: return null
        val key = when (coreName) {
            "mihomo" -> "name_mihomo_config"
            "clash" -> "name_clash_config"
            "sing-box" -> "name_sing_config"
            else -> "name_${coreName}_config"
        }
        return findSetting(settings, key)?.takeIf { it.isNotBlank() }
    }
}
