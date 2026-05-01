package com.box.app.data.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object ShellExecutor {
    data class Result(val stdout: String, val stderr: String, val exitCode: Int)

    /**
     * 在 libsu 全局 cached shell 上执行命令（IO 派发，单实例串行）。
     * 调度由调用方负责（已在 Dispatchers.IO 上跑，不阻塞 UI 线程）。
     */
    suspend fun execute(command: String): Result = withContext(Dispatchers.IO) {
        PersistentRootShell.execute(command)
    }

    /**
     * 预热全局 cached shell。重复调用幂等。
     */
    suspend fun warmUpRootShell(minSessions: Int = 1) = withContext(Dispatchers.IO) {
        PersistentRootShell.warmUp(minSessions)
    }
}
