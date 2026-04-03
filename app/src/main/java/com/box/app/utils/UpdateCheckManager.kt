package com.box.app.utils

import android.content.Context
import com.box.app.data.backend.BoxApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class UpdateCheckStatus(
    val isChecking: Boolean = false,
    val appHasUpdate: Boolean = false,
    val moduleHasUpdate: Boolean = false,
    val checkedAtMillis: Long = 0L
)

object UpdateCheckManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private val _status = MutableStateFlow(UpdateCheckStatus())
    val status: StateFlow<UpdateCheckStatus> = _status.asStateFlow()

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        scope.launch {
            ensureChecked(context.applicationContext, force = false)
        }
    }

    suspend fun ensureChecked(context: Context, force: Boolean = false) {
        lock.withLock {
            if (!force && _status.value.checkedAtMillis > 0L) return
            _status.value = _status.value.copy(isChecking = true)
        }

        val appVersionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")

        var appHasUpdate = false
        var moduleHasUpdate = false

        try {
            coroutineScope {
                val appDeferred = async {
                    runCatching { BoxApi.checkForAppUpdates(appVersionName).hasUpdate }.getOrDefault(false)
                }
                val moduleDeferred = async {
                    runCatching { BoxApi.checkForModuleUpdates().hasUpdate }.getOrDefault(false)
                }
                appHasUpdate = appDeferred.await()
                moduleHasUpdate = moduleDeferred.await()
            }
        } catch (_: Exception) {
            lock.withLock {
                _status.value = _status.value.copy(
                    isChecking = false,
                    checkedAtMillis = System.currentTimeMillis()
                )
            }
            return
        }

        lock.withLock {
            _status.value = UpdateCheckStatus(
                isChecking = false,
                appHasUpdate = appHasUpdate,
                moduleHasUpdate = moduleHasUpdate,
                checkedAtMillis = System.currentTimeMillis()
            )
        }
    }

    fun setAppUpdateAvailable(hasUpdate: Boolean) {
        _status.value = _status.value.copy(
            appHasUpdate = hasUpdate,
            checkedAtMillis = System.currentTimeMillis()
        )
    }

    fun setModuleUpdateAvailable(hasUpdate: Boolean) {
        _status.value = _status.value.copy(
            moduleHasUpdate = hasUpdate,
            checkedAtMillis = System.currentTimeMillis()
        )
    }
}
