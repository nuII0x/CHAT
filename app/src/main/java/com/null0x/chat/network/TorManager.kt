package com.null0x.chat.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import org.torproject.jni.TorService
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object TorManager {
    sealed class Status {
        data object Idle : Status()
        data object Starting : Status()
        data object Ready : Status()
        data class Error(val message: String) : Status()
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val onionHostRegex = Regex("^[a-z2-7]{56}\\.onion$")

    @Volatile
    private var receiverRegistered = false
    @Volatile
    private var onionHost: String = ""
    @Volatile
    private var lastLocalPort: Int = 5000
    @Volatile
    private var manualStop = false
    @Volatile
    private var appContextRef: Context? = null
    private var restartJob: Job? = null
    private var hostnameWaitJob: Job? = null

    fun socksHost(): String = "127.0.0.1"
    fun socksPort(): Int = TorService.socksPort
    fun onionAddress(): String = onionHost
    fun onionRoute(port: Int): String {
        if (_status.value !is Status.Ready) return ""
        if (onionHost.isBlank()) {
            appContextRef?.let { refreshOnionAddress(it) }
        }
        return onionHost.takeIf { it.isNotBlank() }?.let { "onion:$it:$port" } ?: ""
    }

    fun configureOnionService(context: Context, localPort: Int) {
        val appContext = context.applicationContext
        appContextRef = appContext
        lastLocalPort = localPort
        val hiddenServiceDir = hiddenServiceDir(appContext).apply { mkdirs() }
        val torrc = TorService.getTorrc(appContext)
        torrc.parentFile?.mkdirs()
        torrc.writeText(
            listOf(
                "HiddenServiceDir ${hiddenServiceDir.absolutePath}",
                "HiddenServiceVersion 3",
                "HiddenServicePort $localPort 127.0.0.1:$localPort"
            ).joinToString(separator = "\n", postfix = "\n")
        )
        refreshOnionAddress(appContext)
    }

    fun ensureStarted(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        runCatching {
            manualStop = false
            if (_status.value is Status.Starting || _status.value is Status.Ready) {
                return
            }
            ensureReceiver(appContext)
            _status.value = Status.Starting
            startTorService(appContext).getOrThrow()
        }
            .onFailure { scheduleRestart(appContext) }
    }

    fun stop(context: Context) {
        manualStop = true
        restartJob?.cancel()
        hostnameWaitJob?.cancel()
        runCatching {
            context.applicationContext.stopService(Intent(context, TorService::class.java))
        }
        onionHost = ""
        appContextRef = null
        _status.value = Status.Idle
    }

    private fun hiddenServiceDir(context: Context): File {
        return File(context.filesDir, "tor/primochat-onion")
    }

    private fun ensureReceiver(context: Context) {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(TorService.ACTION_STATUS)
            addAction(TorService.ACTION_ERROR)
        }
        ContextCompat.registerReceiver(
            context,
            statusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                TorService.ACTION_STATUS -> {
                    when (intent.getStringExtra(TorService.EXTRA_STATUS)) {
                        TorService.STATUS_STARTING -> _status.value = Status.Starting
                        TorService.STATUS_ON -> {
                            val appContext = context?.applicationContext
                            if (appContext != null) {
                                if (refreshOnionAddress(appContext)) {
                                    hostnameWaitJob?.cancel()
                                    restartJob?.cancel()
                                    _status.value = Status.Ready
                                } else {
                                    waitForOnionHostname(appContext)
                                }
                            } else {
                                _status.value = Status.Starting
                            }
                        }
                        TorService.STATUS_STOPPING -> {
                            hostnameWaitJob?.cancel()
                            if (!manualStop) {
                                _status.value = Status.Starting
                            } else {
                                _status.value = Status.Idle
                            }
                        }
                        TorService.STATUS_OFF -> {
                            hostnameWaitJob?.cancel()
                            val appContext = context?.applicationContext
                            if (!manualStop && appContext != null) {
                                scheduleRestart(appContext)
                            } else {
                                _status.value = Status.Idle
                            }
                        }
                    }
                }
                TorService.ACTION_ERROR -> {
                    hostnameWaitJob?.cancel()
                    context?.applicationContext?.let { scheduleRestart(it) }
                        ?: run { _status.value = Status.Starting }
                }
            }
        }
    }

    private fun startTorService(appContext: Context): Result<Unit> {
        return runCatching {
            val intent = Intent(appContext, TorService::class.java).apply {
                action = TorService.ACTION_START
                putExtra(TorService.EXTRA_PACKAGE_NAME, appContext.packageName)
                putExtra(TorService.EXTRA_SERVICE_PACKAGE_NAME, appContext.packageName)
            }
            appContext.startService(intent)
            Unit
        }
    }

    private fun scheduleRestart(context: Context, delayMs: Long = 350L) {
        val appContext = context.applicationContext
        if (manualStop) {
            _status.value = Status.Idle
            return
        }
        _status.value = Status.Starting
        restartJob?.cancel()
        hostnameWaitJob?.cancel()
        restartJob = scope.launch {
            runCatching { appContext.stopService(Intent(appContext, TorService::class.java)) }
            delay(delayMs)
            if (manualStop) {
                _status.value = Status.Idle
                return@launch
            }
            runCatching {
                configureOnionService(appContext, lastLocalPort)
                ensureReceiver(appContext)
            }
            startTorService(appContext).onFailure {
                delay(1_500)
                if (!manualStop) {
                    scheduleRestart(appContext, delayMs = 1_500)
                }
            }
        }
    }

    private fun waitForOnionHostname(appContext: Context) {
        hostnameWaitJob?.cancel()
        _status.value = Status.Starting
        hostnameWaitJob = scope.launch {
            repeat(120) {
                if (manualStop) {
                    _status.value = Status.Idle
                    return@launch
                }
                if (refreshOnionAddress(appContext)) {
                    restartJob?.cancel()
                    _status.value = Status.Ready
                    return@launch
                }
                delay(250)
            }
            if (!manualStop) {
                _status.value = Status.Starting
            }
        }
    }

    private fun readOnionHostname(context: Context): String? {
        val hostnameFile = File(hiddenServiceDir(context), "hostname")
        if (!hostnameFile.exists()) return null
        val host = runCatching { hostnameFile.readText().trim().lowercase() }.getOrNull().orEmpty()
        return host.takeIf { onionHostRegex.matches(it) }
    }

    private fun refreshOnionAddress(context: Context): Boolean {
        val host = readOnionHostname(context) ?: return false
        onionHost = host
        return true
    }
}
