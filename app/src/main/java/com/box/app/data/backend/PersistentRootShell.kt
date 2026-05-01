package com.box.app.data.backend

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong

/**
 * 单实例 Root Shell 封装（带主动探活 + 短超时 + 失败重建）
 *
 * 设计思路：
 *   - 复用 libsu 的全局 cached shell（[Shell.cmd] / [Shell.getShell]）：root 授权、
 *     生命周期、IPC 都由 libsu 内部统一管理，**保证唯一会话**（cwd / env / 已加载脚本一致）
 *   - 之前两版极端方案的取舍如下：
 *       (A) 多 shell 池：每个 shell 独立 su 子进程 → 会话丢失（用户已反馈）
 *       (B) 单 shell + 12s 超时：模块重启时旧 cached 引用陈旧，新命令必须等满 12s
 *           才发现 shell 已死，UI 长时间"加载中"（用户已反馈）
 *
 *   - 本版采用「单 shell + 入口探活 + 短超时 + 失败即重建」：
 *       1. 入口快速 [Shell.isAlive] 检查 — 死则 close，下一次 [Shell.cmd] 由 libsu
 *          自动重建（< 1s），不再等满超时
 *       2. 命令级超时缩短到 4s — 模块重启 IPC 中断的物理上限 ~1-2s，4s 给足容错
 *       3. timeout / 任何异常都触发 close — 避免陈旧 cached 引用拖累后续命令
 *       4. [generation] 计数器：每次 close 自增；上层（如日志页）切换文件时若需要丢弃
 *          in-flight 旧命令的结果，可通过 [currentGeneration] 比对决定是否更新 UI
 *
 *   - 任何写操作仍走全局 cached shell — 与启动 / 配置 / 服务控制共享同一会话与权限上下文
 */
internal object PersistentRootShell {

    /**
     * 命令级超时。模块重启等 IPC 中断的物理上限约 1-2 秒，4 秒已经留足容错；
     * 任何超时立即 close，下次 [Shell.cmd] 让 libsu 自动重建（典型 < 1s）。
     */
    private const val COMMAND_TIMEOUT_MS = 4_000L

    private val generationCounter = AtomicLong(0)

    /** 当前 shell 代际号；每次 [close] 自增，用于上层丢弃 in-flight 过期结果。 */
    val currentGeneration: Long
        get() = generationCounter.get()

    /**
     * 预热全局 cached shell。重复调用幂等，每次都触发 [Shell.getShell] 获取
     * 已 cache 的实例（首次会执行 root 授权与初始化）。
     */
    suspend fun warmUp(minSessions: Int = 1) = withContext(Dispatchers.IO) {
        repeat(minSessions.coerceAtLeast(1)) {
            Shell.getShell()
        }
    }

    /**
     * 执行 root 命令（在全局 cached shell 上排队）。
     *
     * 入口先做 [Shell.isAlive] 探活，死则 close 让 libsu 自动重建 — 这是模块重启
     * 场景下 UI 不卡顿的关键：避免新命令陪着陈旧的 cached shell 等满整个 timeout。
     */
    suspend fun execute(command: String): ShellExecutor.Result = withContext(Dispatchers.IO) {
        // ── 快速探活：cached shell 死 → 提前 close，下次 Shell.cmd 触发 libsu 重建 ──
        Shell.getCachedShell()?.takeUnless { it.isAlive }?.let { close() }

        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()

        try {
            val result = withTimeout(COMMAND_TIMEOUT_MS) {
                Shell.cmd(command).to(stdout, stderr).exec()
            }
            ShellExecutor.Result(
                stdout = stdout.joinToString("\n"),
                stderr = stderr.joinToString("\n"),
                exitCode = result.code
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            // 超时 → 大概率 shell 卡住或死了：close 让下次重建，避免后续命令同样卡死
            close()
            ShellExecutor.Result("", "shell timeout", -1)
        } catch (e: Exception) {
            // 任意异常都 close，防止陈旧 cached 引用反复触发同样错误
            close()
            ShellExecutor.Result("", e.message ?: "shell error", -1)
        }
    }

    /**
     * 关闭全局 cached shell。代际号自增，让上层可以判断 in-flight 命令的结果是否过期。
     * 典型用法：[EnvironmentChecker.requestRootAccess] 在请求新授权前调用，
     * 强制下一次 [execute] 让 libsu 重新打开 shell，让新授权立即生效。
     */
    fun close() {
        generationCounter.incrementAndGet()
        runCatching {
            Shell.getCachedShell()?.waitAndClose()
        }
    }
}
