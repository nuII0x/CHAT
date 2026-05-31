package com.null0x.chat.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.null0x.chat.AppVisibility
import org.torproject.jni.TorService
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TorManager {
    sealed class Status {
        data object Idle : Status()
        data object Starting : Status()
        data object Ready : Status()
        data class Error(val message: String) : Status()
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    @Volatile
    private var receiverRegistered = false
    @Volatile
    private var onionHost: String = ""

    fun socksHost(): String = "127.0.0.1"
    fun socksPort(): Int = TorService.socksPort
    fun onionAddress(): String = onionHost
    fun onionRoute(port: Int): String {
        if (_status.value !is Status.Ready) return ""
        return onionHost.takeIf { it.isNotBlank() }?.let { "onion:$it:$port" } ?: ""
    }

    fun configureOnionService(context: Context, localPort: Int) {
        val appContext = context.applicationContext
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
        runCatching {
            if (_status.value is Status.Starting || _status.value is Status.Ready) {
                return
            }
            if (!AppVisibility.isVisible) {
                _status.value = Status.Error("Abra o app para iniciar o Tor")
                return
            }
            ensureReceiver(appContext)

            _status.value = Status.Starting
            val intent = Intent(appContext, TorService::class.java).apply {
                action = TorService.ACTION_START
                putExtra(TorService.EXTRA_PACKAGE_NAME, appContext.packageName)
                putExtra(TorService.EXTRA_SERVICE_PACKAGE_NAME, appContext.packageName)
            }
            appContext.startService(intent)
        }
            .onFailure { _status.value = Status.Error(it.message ?: "Falha ao iniciar TorService") }
    }

    fun stop(context: Context) {
        runCatching {
            context.applicationContext.stopService(Intent(context, TorService::class.java))
        }
        onionHost = ""
        _status.value = Status.Idle
    }

    private fun refreshOnionAddress(context: Context) {
        val host = File(hiddenServiceDir(context), "hostname")
            .takeIf { it.exists() }
            ?.readText()
            ?.trim()
            .orEmpty()
        if (host.endsWith(".onion")) {
            onionHost = host
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(statusReceiver, filter)
        }
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
                            context?.applicationContext?.let { refreshOnionAddress(it) }
                            _status.value = Status.Ready
                        }
                        TorService.STATUS_STOPPING,
                        TorService.STATUS_OFF -> _status.value = Status.Idle
                    }
                }
                TorService.ACTION_ERROR -> {
                    val msg = intent.getStringExtra(TorService.EXTRA_STATUS)
                        ?: "Erro ao iniciar TorService"
                    _status.value = Status.Error(msg)
                }
            }
        }
    }
}
