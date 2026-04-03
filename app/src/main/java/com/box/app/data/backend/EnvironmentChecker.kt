package com.box.app.data.backend

import com.box.app.data.model.EnvironmentState

internal object EnvironmentChecker {

    @Volatile
    private var cached: EnvironmentState? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    private const val CACHE_MS = 5_000L

    suspend fun check(forceRefresh: Boolean = false): EnvironmentState {
        val now = System.currentTimeMillis()
        val snap = cached
        if (!forceRefresh && snap != null && (now - cachedAtMs) < CACHE_MS) return snap

        val res = try {
            ShellExecutor.execute(
                "u=\$(id -u 2>/dev/null); r=0; m=0; s=0; [ \"\$u\" = \"0\" ] && r=1; [ -d /data/adb/modules/box_for_root ] && m=1; [ -d /data/adb/box/scripts ] && s=1; echo \"\$r \$m \$s\""
            )
        } catch (e: Exception) {
            val out = EnvironmentState(
                checked = true,
                hasRoot = false,
                hasModule = false,
                hasScripts = false,
                message = ""
            )
            cached = out
            cachedAtMs = now
            return out
        }

        val parts = res.stdout.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val hasRoot = parts.getOrNull(0) == "1"
        val hasModule = parts.getOrNull(1) == "1"
        val hasScripts = parts.getOrNull(2) == "1"

        val out = EnvironmentState(
            checked = true,
            hasRoot = hasRoot,
            hasModule = hasModule,
            hasScripts = hasScripts,
            message = ""
        )
        cached = out
        cachedAtMs = now
        return out
    }

    fun invalidateCache() {
        cached = null
        cachedAtMs = 0L
    }

    suspend fun requestRootAccess(): Boolean {
        invalidateCache()
        val res = try {
            ShellExecutor.execute("id -u 2>/dev/null")
        } catch (_: Exception) {
            return false
        }

        val granted = res.exitCode == 0 && res.stdout.trim() == "0"
        if (granted) {
            check(forceRefresh = true)
        }
        return granted
    }
}
