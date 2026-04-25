package com.box.app.data.backend

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

/**
 * Root shell 池（基于 libsu）
 *
 * 背景：libsu 的每个 [Shell] 实例内部对 Job 执行加互斥锁，Shell.cmd()/Shell.getShell()
 * 返回全局单例，因此所有 root 命令（tail 日志、读配置、执行 box.service 控制等）
 * 会在同一个 shell 上排队。当某条命令耗时较长（模块重启、大日志 cat、外部管理器
 * IPC 抖动…），整个 App 都会被阻塞，UI 也会因此出现状态卡住。
 *
 * 实现要点：
 * 1. **动态扩容**：按需创建 shell（`Shell.Builder.create().build()` 各开一个独立 su 子进程）
 *    直到达到 [POOL_SIZE]；超过容量的请求在 [idle] Channel 上排队等待释放
 * 2. **健康反馈**：任务成功 → 归还复用；超时/异常/取消 → 关闭后由下次 acquire 重建
 * 3. **世代失效**：[close] 递增 [generation]，所有"在飞"的 shell 归还时会因世代不匹配
 *    被直接丢弃，确保下次 acquire 拿到新授权下创建的全新 shell
 * 4. **非阻塞释放**：归还逻辑跑在 [NonCancellable] 下，上游协程取消不会泄漏 shell
 *
 * 公开 API 与旧版保持兼容（`execute` / `warmUp` / `close`）。
 */
internal object PersistentRootShell {

    private const val COMMAND_TIMEOUT_MS = 12_000L
    private const val POOL_SIZE = 3
    // 与 AppApplication 中 Shell.setDefaultBuilder 保持一致的 shell 配置
    private const val SHELL_TIMEOUT_S = 12L

    private val idle = Channel<PooledShell>(POOL_SIZE)
    private val active = AtomicInteger(0)
    private val generation = AtomicInteger(0)

    private class PooledShell(val shell: Shell, val gen: Int)

    /**
     * 构建新的独立 root shell 实例。
     * libsu 6.0 无 `Shell.newInstance()` 静态方法，通过 Builder 显式创建；
     * 参数与 [com.box.app.AppApplication.onCreate] 的 setDefaultBuilder 保持一致。
     */
    private fun buildNewShell(): Shell = Shell.Builder.create()
        .setFlags(Shell.FLAG_MOUNT_MASTER)
        .setTimeout(SHELL_TIMEOUT_S)
        .build()

    /**
     * 预热 shell 池。重复调用幂等，只会向 [target] 扩容不会缩容。
     * App 启动时调用一次即可避免首次命令因冷启动 su 进程而额外等待。
     */
    suspend fun warmUp(minSessions: Int = POOL_SIZE) = withContext(Dispatchers.IO) {
        val target = minSessions.coerceIn(1, POOL_SIZE)
        while (active.get() < target) {
            val gen = generation.get()
            val shell = runCatching { buildNewShell() }.getOrNull() ?: break
            // CAS 扩容计数；若并发其它线程已达目标，回滚
            val updated = active.incrementAndGet()
            if (updated > target) {
                active.decrementAndGet()
                runCatching { shell.waitAndClose() }
                break
            }
            idle.trySend(PooledShell(shell, gen))
        }
    }

    /**
     * 执行 root 命令
     * 返回值中 exitCode = -1 表示 shell 超时 / 异常 / 取消。
     */
    suspend fun execute(command: String): ShellExecutor.Result = withContext(Dispatchers.IO) {
        val pooled = acquire()
        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        var healthy = true
        var cancelled: CancellationException? = null

        val result: ShellExecutor.Result = try {
            val r = withTimeout(COMMAND_TIMEOUT_MS) {
                pooled.shell.newJob().add(command).to(stdout, stderr).exec()
            }
            ShellExecutor.Result(
                stdout = stdout.joinToString("\n"),
                stderr = stderr.joinToString("\n"),
                exitCode = r.code
            )
        } catch (e: TimeoutCancellationException) {
            healthy = false
            ShellExecutor.Result("", "shell timeout", -1)
        } catch (e: CancellationException) {
            healthy = false
            cancelled = e
            ShellExecutor.Result("", "cancelled", -1)
        } catch (e: Exception) {
            healthy = false
            ShellExecutor.Result("", e.message ?: "shell error", -1)
        }

        withContext(NonCancellable) { release(pooled, healthy) }
        cancelled?.let { throw it }
        result
    }

    /**
     * 关闭所有已知 shell；"在飞" shell 归还时会因世代失效被丢弃。
     * 典型场景：`EnvironmentChecker.requestRootAccess` 请求新授权前调用，
     * 保证下一次 `execute` 打开全新子进程，使新的 root 授权立刻生效。
     */
    suspend fun close() = withContext(Dispatchers.IO + NonCancellable) {
        generation.incrementAndGet() // 失效所有 in-flight shell
        while (true) {
            val p = idle.tryReceive().getOrNull() ?: break
            runCatching { p.shell.waitAndClose() }
            active.decrementAndGet()
        }
        // 兼容直接使用 Shell.cmd() 的外部代码：关闭 libsu 全局缓存 shell
        runCatching { Shell.getCachedShell()?.waitAndClose() }
    }

    // ── 内部：shell 获取 / 归还 ────────────────────────────────────────────

    private suspend fun acquire(): PooledShell {
        // 1) 快速路径：直接从空闲队列取
        idle.tryReceive().getOrNull()?.let { return it }

        // 2) 容量未满：创建新 shell（CAS 保证并发安全）
        while (true) {
            val curr = active.get()
            if (curr >= POOL_SIZE) break
            if (active.compareAndSet(curr, curr + 1)) {
                val gen = generation.get()
                val shell = runCatching { buildNewShell() }.getOrNull()
                if (shell != null) return PooledShell(shell, gen)
                // 创建失败（root 被撤 / 系统 IO 异常）回滚，尝试走等待路径
                active.decrementAndGet()
                break
            }
        }

        // 3) 池已满：挂起等待某个 shell 归还
        return idle.receive()
    }

    /**
     * 归还 shell。
     * @param healthy false = 任务失败/取消/超时 或 shell 已被 [close] 失效
     *                → 关闭并不再入池；下次 [acquire] 走容量路径重建
     */
    private fun release(pooled: PooledShell, healthy: Boolean) {
        val validGen = pooled.gen == generation.get()
        if (healthy && validGen) {
            idle.trySend(pooled) // buffered capacity=POOL_SIZE，trySend 必然成功
            return
        }
        runCatching { pooled.shell.waitAndClose() }
        active.decrementAndGet()
    }
}
