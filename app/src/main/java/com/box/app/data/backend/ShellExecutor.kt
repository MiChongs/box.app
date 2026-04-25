package com.box.app.data.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ShellExecutor {
    data class Result(val stdout: String, val stderr: String, val exitCode: Int)

    /**
     * 通过 root shell 池执行命令。并发调用会分派到不同 shell 实例并行执行，
     * 避免全局单例 shell 导致的串行阻塞。
     */
    suspend fun execute(command: String): Result = withContext(Dispatchers.IO) {
        PersistentRootShell.execute(command)
    }

    /**
     * 预热 shell 池。默认池大小为 3（读路径与写路径可并行）。
     * 可调用方按需显式扩容；重复调用只会向上扩展，不会缩容。
     */
    suspend fun warmUpRootShell(minSessions: Int = 3) = withContext(Dispatchers.IO) {
        PersistentRootShell.warmUp(minSessions)
    }
}
