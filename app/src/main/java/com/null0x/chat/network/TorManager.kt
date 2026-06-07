package com.null0x.chat.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import org.torproject.jni.TorService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    private val _networkAvailableState = MutableStateFlow(true)
    val networkAvailableState: StateFlow<Boolean> = _networkAvailableState.asStateFlow()
    private val _diagnostics = MutableStateFlow<List<String>>(emptyList())
    val diagnostics: StateFlow<List<String>> = _diagnostics.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val onionHostRegex = Regex("^[a-z2-7]{56}\\.onion$")
    private const val START_TIMEOUT_MS = 45_000L
    private const val HOSTNAME_TIMEOUT_MS = 30_000L
    private const val HOSTNAME_CHECK_INTERVAL_MS = 250L
    private const val DEFAULT_SOCKS_PORT = 9050
    private const val MAX_DIAGNOSTICS = 80
    private const val WAITING_NETWORK_MESSAGE = "Aguardando rede..."
    private const val TOR_IDENTITY_PREFS = "tor_identity"
    private const val HIDDEN_SERVICE_DIR_KEY = "hidden_service_dir"

    @Volatile
    private var receiverRegistered = false
    @Volatile
    private var networkCallbackRegistered = false
    @Volatile
    private var networkAvailable = true
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
    private var startTimeoutJob: Job? = null

    fun socksHost(): String = "127.0.0.1"
    fun socksPort(): Int = TorService.socksPort.takeIf { it > 0 } ?: DEFAULT_SOCKS_PORT
    fun onionAddress(): String = onionHost
    fun isNetworkAvailable(): Boolean = networkAvailable
    fun ensureNetworkMonitoring(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        ensureNetworkCallback(appContext)
        updateNetworkAvailability(appContext)
    }

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
        val hiddenServiceDir = hiddenServiceDir(appContext).apply {
            mkdirs()
            setReadable(false, false)
            setWritable(false, false)
            setExecutable(false, false)
            setReadable(true, true)
            setWritable(true, true)
            setExecutable(true, true)
        }
        val torrc = TorService.getTorrc(appContext)
        torrc.parentFile?.mkdirs()
        val torrcText = listOf(
            "HiddenServiceDir ${hiddenServiceDir.absolutePath}",
            "HiddenServicePort $localPort 127.0.0.1:$localPort"
        ).joinToString(separator = "\n", postfix = "\n")
        torrc.writeText(torrcText)
        record(appContext, "torrc atualizado: ${torrc.absolutePath}")
        record(appContext, "servico onion configurado: porta local=$localPort")
        refreshOnionAddress(appContext)
    }

    fun ensureStarted(context: Context) {
        val appContext = context.applicationContext
        appContextRef = appContext
        ensureNetworkCallback(appContext)
        networkAvailable = updateNetworkAvailability(appContext)
        if (!networkAvailable) {
            record(appContext, "rede Android indisponivel")
            waitForNetwork(appContext)
            return
        }
        runCatching {
            manualStop = false
            if (_status.value is Status.Starting || _status.value is Status.Ready || restartJob?.isActive == true) {
                record(appContext, "inicio ignorado: estado=${statusName(_status.value)}, reinicioAtivo=${restartJob?.isActive == true}")
                return
            }
            ensureReceiver(appContext)
            markStarting(appContext)
            startTorService(appContext).getOrThrow()
        }
            .onFailure {
                record(appContext, "falha ao iniciar servico: ${it.message.orEmpty()}")
                scheduleRestart(appContext)
            }
    }

    fun stop(context: Context) {
        manualStop = true
        restartJob?.cancel()
        hostnameWaitJob?.cancel()
        startTimeoutJob?.cancel()
        runCatching {
            context.applicationContext.stopService(Intent(context, TorService::class.java))
        }
        onionHost = ""
        appContextRef = null
        _status.value = Status.Idle
    }

    private fun hiddenServiceDir(context: Context): File {
        val appContext = context.applicationContext
        val stableDir = File(appContext.filesDir, "tor/hidden_service")
        val prefs = appContext.getSharedPreferences(TOR_IDENTITY_PREFS, Context.MODE_PRIVATE)
        val savedDir = prefs.getString(HIDDEN_SERVICE_DIR_KEY, null)?.let(::File)
        val currentTorServiceDir = File(TorService.getTorrc(appContext).parentFile, "primochat-onion")
        val legacyFilesDir = File(appContext.filesDir, "tor/primochat-onion")
        val candidates = listOfNotNull(stableDir, savedDir, currentTorServiceDir, legacyFilesDir)
            .distinctBy { it.absolutePath }

        if (readOnionHostname(stableDir) != null) {
            prefs.edit().putString(HIDDEN_SERVICE_DIR_KEY, stableDir.absolutePath).apply()
            return stableDir
        }

        val existingIdentity = candidates.firstOrNull { readOnionHostname(it) != null }
        if (existingIdentity != null && existingIdentity.absolutePath != stableDir.absolutePath) {
            runCatching {
                stableDir.parentFile?.mkdirs()
                existingIdentity.copyRecursively(stableDir, overwrite = true)
            }
            if (readOnionHostname(stableDir) != null) {
                prefs.edit().putString(HIDDEN_SERVICE_DIR_KEY, stableDir.absolutePath).apply()
                return stableDir
            }
            prefs.edit().putString(HIDDEN_SERVICE_DIR_KEY, existingIdentity.absolutePath).apply()
            return existingIdentity
        }

        prefs.edit().putString(HIDDEN_SERVICE_DIR_KEY, stableDir.absolutePath).apply()
        return stableDir
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

    private fun ensureNetworkCallback(context: Context) {
        if (networkCallbackRegistered) return
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            manager.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
            networkAvailable = updateNetworkAvailability(appContext)
            record(appContext, "monitor de rede registrado: disponivel=$networkAvailable")
        }
            .onFailure { record(appContext, "falha no monitor de rede: ${it.message.orEmpty()}") }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val appContext = appContextRef ?: return
            networkAvailable = isNetworkAvailable(appContext)
            _networkAvailableState.value = networkAvailable
            if (!networkAvailable) {
                record(appContext, "rede Android sem internet validada")
                _status.value = Status.Error(WAITING_NETWORK_MESSAGE)
                waitForNetwork(appContext)
                return
            }
            record(appContext, "rede Android disponivel")
            val status = _status.value
            if (!manualStop && status !is Status.Ready && status !is Status.Starting) {
                scheduleRestart(appContext, delayMs = 250)
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val appContext = appContextRef ?: return
            val available = hasValidatedInternet(networkCapabilities)
            if (available == networkAvailable) return
            networkAvailable = available
            _networkAvailableState.value = available
            if (available) {
                record(appContext, "rede Android validada")
                val status = _status.value
                if (!manualStop && status !is Status.Ready && status !is Status.Starting) {
                    scheduleRestart(appContext, delayMs = 250)
                }
            } else {
                record(appContext, "rede Android sem internet validada")
                _status.value = Status.Error(WAITING_NETWORK_MESSAGE)
                waitForNetwork(appContext)
            }
        }

        override fun onLost(network: Network) {
            val appContext = appContextRef ?: return
            networkAvailable = false
            _networkAvailableState.value = false
            record(appContext, "rede Android perdida")
            _status.value = Status.Error(WAITING_NETWORK_MESSAGE)
            waitForNetwork(appContext)
        }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                TorService.ACTION_STATUS -> {
                    val serviceStatus = intent.getStringExtra(TorService.EXTRA_STATUS).orEmpty()
                    (context?.applicationContext ?: appContextRef)?.let { record(it, "evento Tor: ${serviceStatusName(serviceStatus)}") }
                    when (serviceStatus) {
                        TorService.STATUS_STARTING -> context?.applicationContext?.let { markStarting(it) }
                            ?: run { _status.value = Status.Starting }
                        TorService.STATUS_ON -> {
                            val appContext = context?.applicationContext
                            if (appContext != null) {
                                if (refreshOnionAddress(appContext)) {
                                    hostnameWaitJob?.cancel()
                                    restartJob?.cancel()
                                    startTimeoutJob?.cancel()
                                    record(appContext, "Tor pronto: nome onion=${maskedOnionHost(onionHost)}")
                                    _status.value = Status.Ready
                                } else {
                                    record(appContext, "Tor pronto sem nome onion; aguardando arquivo")
                                    waitForOnionHostname(appContext)
                                }
                            } else {
                                _status.value = Status.Starting
                            }
                        }
                        TorService.STATUS_STOPPING -> {
                            hostnameWaitJob?.cancel()
                            if (restartJob?.isActive == true) {
                                context?.applicationContext?.let { markStarting(it) }
                                    ?: run { _status.value = Status.Starting }
                            } else if (!manualStop) {
                                context?.applicationContext?.let { markStarting(it) }
                                    ?: run { _status.value = Status.Starting }
                            } else {
                                startTimeoutJob?.cancel()
                                _status.value = Status.Idle
                            }
                        }
                        TorService.STATUS_OFF -> {
                            hostnameWaitJob?.cancel()
                            val appContext = context?.applicationContext
                            if (restartJob?.isActive == true) {
                                startTimeoutJob?.cancel()
                                _status.value = Status.Starting
                            } else if (!manualStop && appContext != null) {
                                scheduleRestart(appContext)
                            } else {
                                startTimeoutJob?.cancel()
                                _status.value = Status.Idle
                            }
                        }
                    }
                }
                TorService.ACTION_ERROR -> {
                    hostnameWaitJob?.cancel()
                    val appContext = context?.applicationContext ?: appContextRef
                    val message = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                    appContext?.let { record(it, "erro do Tor: ${message.ifBlank { "sem detalhe" }}") }
                    if (restartJob?.isActive == true) {
                        _status.value = Status.Starting
                    } else {
                        _status.value = Status.Error(WAITING_NETWORK_MESSAGE)
                        appContext?.let { scheduleRestart(it) }
                    }
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
            record(appContext, "iniciando servico do Tor")
            appContext.startService(intent)
            Unit
        }
    }

    private fun scheduleRestart(context: Context, delayMs: Long = 350L) {
        val appContext = context.applicationContext
        if (manualStop) {
            startTimeoutJob?.cancel()
            _status.value = Status.Idle
            record(appContext, "restart cancelado: parada manual")
            return
        }
        if (restartJob?.isActive == true) {
            record(appContext, "restart ignorado: ja existe restart ativo")
            return
        }
        ensureNetworkCallback(appContext)
        networkAvailable = isNetworkAvailable(appContext)
        _networkAvailableState.value = networkAvailable
        if (!networkAvailable) {
            record(appContext, "restart adiado: rede Android indisponivel")
            waitForNetwork(appContext)
            return
        }
        _status.value = Status.Error(WAITING_NETWORK_MESSAGE)
        restartJob?.cancel()
        hostnameWaitJob?.cancel()
        startTimeoutJob?.cancel()
        restartJob = scope.launch {
            record(appContext, "reiniciando Tor em ${delayMs}ms")
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
            markStarting(appContext)
            startTorService(appContext).onFailure {
                record(appContext, "falha no restart: ${it.message.orEmpty()}")
                delay(1_500)
                if (!manualStop) {
                    scheduleRestart(appContext, delayMs = 1_500)
                }
            }
        }
    }

    private fun waitForOnionHostname(appContext: Context) {
        hostnameWaitJob?.cancel()
        markStarting(appContext)
        hostnameWaitJob = scope.launch {
            val attempts = (HOSTNAME_TIMEOUT_MS / HOSTNAME_CHECK_INTERVAL_MS).toInt()
            repeat(attempts) {
                if (manualStop) {
                    startTimeoutJob?.cancel()
                    _status.value = Status.Idle
                    return@launch
                }
                if (refreshOnionAddress(appContext)) {
                    restartJob?.cancel()
                    startTimeoutJob?.cancel()
                    record(appContext, "nome onion encontrado: ${maskedOnionHost(onionHost)}")
                    _status.value = Status.Ready
                    return@launch
                }
                delay(HOSTNAME_CHECK_INTERVAL_MS)
            }
            if (!manualStop) {
                record(appContext, "tempo esgotado aguardando nome onion")
                _status.value = Status.Error(WAITING_NETWORK_MESSAGE)
                scheduleRestart(appContext, delayMs = 750)
            }
        }
    }

    private fun markStarting(appContext: Context) {
        if (manualStop) {
            startTimeoutJob?.cancel()
            _status.value = Status.Idle
            record(appContext, "inicio cancelado: parada manual")
            return
        }
        _status.value = Status.Starting
        record(appContext, "status interno: iniciando")
        scheduleStartTimeout(appContext)
    }

    private fun scheduleStartTimeout(appContext: Context) {
        if (startTimeoutJob?.isActive == true) return
        startTimeoutJob = scope.launch {
            delay(START_TIMEOUT_MS)
            if (!manualStop && _status.value is Status.Starting) {
                record(appContext, "timeout de inicializacao")
                _status.value = Status.Error(WAITING_NETWORK_MESSAGE)
                scheduleRestart(appContext, delayMs = 750)
            }
        }
    }

    private fun waitForNetwork(appContext: Context) {
        restartJob?.cancel()
        hostnameWaitJob?.cancel()
        startTimeoutJob?.cancel()
        networkAvailable = false
        _networkAvailableState.value = false
        onionHost = ""
        record(appContext, "aguardando rede Android")
        NetworkBootstrapScheduler.schedule(appContext)
        if (!manualStop) {
            _status.value = Status.Error(WAITING_NETWORK_MESSAGE)
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return hasValidatedInternet(capabilities)
    }

    private fun updateNetworkAvailability(context: Context): Boolean {
        val available = isNetworkAvailable(context)
        networkAvailable = available
        _networkAvailableState.value = available
        if (!available && !manualStop) {
            _status.value = Status.Error(WAITING_NETWORK_MESSAGE)
        }
        return available
    }

    private fun hasValidatedInternet(capabilities: NetworkCapabilities): Boolean {
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun readOnionHostname(context: Context): String? {
        val hostnameFile = File(hiddenServiceDir(context), "hostname")
        return readOnionHostname(hostnameFile.parentFile ?: return null)
    }

    private fun readOnionHostname(hiddenServiceDir: File): String? {
        val hostnameFile = File(hiddenServiceDir, "hostname")
        if (!hostnameFile.exists()) return null
        val host = runCatching { hostnameFile.readText().trim().lowercase() }.getOrNull().orEmpty()
        return host.takeIf { onionHostRegex.matches(it) }
    }

    private fun refreshOnionAddress(context: Context): Boolean {
        val host = readOnionHostname(context) ?: return false
        onionHost = host
        return true
    }

    private fun maskedOnionHost(host: String): String {
        val clean = host.trim().removeSuffix(".onion")
        if (clean.length <= 16) return host
        return "${clean.take(8)}…${clean.takeLast(6)}.onion"
    }

    private fun diagnosticsFile(context: Context): File {
        return File(context.filesDir, "tor/diagnostics.log")
    }

    private fun record(context: Context, message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val line = "$timestamp $message"
        _diagnostics.value = (_diagnostics.value + line).takeLast(MAX_DIAGNOSTICS)
        runCatching {
            val file = diagnosticsFile(context).apply { parentFile?.mkdirs() }
            file.appendText("$line\n")
            if (file.length() > 64 * 1024) {
                file.writeText(file.readLines().takeLast(MAX_DIAGNOSTICS).joinToString(separator = "\n", postfix = "\n"))
            }
        }
    }

    private fun statusName(status: Status): String {
        return when (status) {
            is Status.Idle -> "Parado"
            is Status.Starting -> "Iniciando"
            is Status.Ready -> "Pronto"
            is Status.Error -> "Erro"
        }
    }

    private fun serviceStatusName(status: String): String {
        return when (status) {
            TorService.STATUS_STARTING -> "iniciando"
            TorService.STATUS_ON -> "pronto"
            TorService.STATUS_STOPPING -> "parando"
            TorService.STATUS_OFF -> "desligado"
            else -> status.ifBlank { "desconhecido" }
        }
    }
}
