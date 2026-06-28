package com.null0x.chat.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.null0x.chat.security.identity.RouteIdentityRegistry
import org.bouncycastle.crypto.digests.SHA3Digest
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.torproject.jni.TorService
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
    private const val MIN_RESTART_DELAY_MS = 5_000L
    private const val MAX_RESTART_DELAY_MS = 5 * 60_000L
    private const val MAX_DIAGNOSTICS = 80
    private const val WAITING_NETWORK_MESSAGE = "Aguardando rede..."
    private const val TOR_IDENTITY_PREFS = "tor_identity"
    private const val HIDDEN_SERVICE_DIR_KEY = "hidden_service_dir"
    private val ONION_SECRET_HEADER = "== ed25519v1-secret: type0 ==".toByteArray(StandardCharsets.US_ASCII) + byteArrayOf(0, 0, 0)
    private val ONION_PUBLIC_HEADER = "== ed25519v1-public: type0 ==".toByteArray(StandardCharsets.US_ASCII) + byteArrayOf(0, 0, 0)
    private val ONION_CHECKSUM_PREFIX = ".onion checksum".toByteArray(StandardCharsets.US_ASCII)
    private const val ONION_VERSION: Byte = 3

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
    private var networkLossJob: Job? = null
    private var networkRecoveryJob: Job? = null
    @Volatile
    private var restartFailureCount: Int = 0
    @Volatile
    private var lastRestartAtMs: Long = 0L
    @Volatile
    private var nextRestartAllowedAtMs: Long = 0L

    fun socksHost(): String = "127.0.0.1"
    fun socksPort(): Int = TorService.socksPort.takeIf { it > 0 } ?: DEFAULT_SOCKS_PORT
    fun onionAddress(): String = onionHost
    fun isNetworkAvailable(): Boolean = networkAvailable

    fun exportOnionIdentity(context: Context, destination: Uri): Result<Unit> {
        return runCatching {
            val appContext = context.applicationContext
            val sourceDir = hiddenServiceDir(appContext)
            if (readOnionHostname(sourceDir).isNullOrBlank()) {
                throw IllegalStateException("Ainda não existe uma onion para exportar")
            }
            appContext.contentResolver.openOutputStream(destination)?.use { output ->
                ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                    sourceDir.walkTopDown()
                        .filter { it.isFile }
                        .forEach { file ->
                            val relativePath = sourceDir.toPath().relativize(file.toPath())
                                .toString()
                                .replace(File.separatorChar, '/')
                            zip.putNextEntry(ZipEntry(relativePath))
                            file.inputStream().use { input ->
                                input.copyTo(zip)
                            }
                            zip.closeEntry()
                        }
                    zip.finish()
                }
            } ?: throw IllegalStateException("Não foi possível abrir o arquivo de backup")
            record(appContext, "backup onion exportado")
        }
    }

    fun importOnionIdentity(context: Context, source: Uri): Result<Unit> {
        return runCatching {
            val appContext = context.applicationContext
            val targetDir = hiddenServiceDir(appContext)
            val stagingDir = File(targetDir.parentFile, "${targetDir.name}.importing")
            stagingDir.deleteRecursively()
            stagingDir.mkdirs()
            appContext.contentResolver.openInputStream(source)?.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zip ->
                    unzipSafely(zip, stagingDir)
                }
            } ?: throw IllegalStateException("Não foi possível abrir o backup")

            if (readOnionHostname(stagingDir).isNullOrBlank()) {
                throw IllegalStateException("Backup inválido ou incompleto")
            }

            clearDirectoryContents(targetDir)
            stagingDir.copyRecursively(targetDir, overwrite = true)
            stagingDir.deleteRecursively()

            appContext.getSharedPreferences(TOR_IDENTITY_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(HIDDEN_SERVICE_DIR_KEY, targetDir.absolutePath)
                .apply()

            val shouldRestart = _status.value is Status.Ready || _status.value is Status.Starting || onionHost.isNotBlank()
            if (shouldRestart) {
                val localPort = lastLocalPort
                stop(appContext)
                configureOnionService(appContext, localPort)
                ensureStarted(appContext)
            }
            record(appContext, "backup onion importado")
        }
    }

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
        val identityChanged = ensureDeterministicHiddenServiceIdentity(appContext, hiddenServiceDir)
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
        if (identityChanged && (_status.value is Status.Ready || _status.value is Status.Starting)) {
            stop(appContext)
        }
    }

    private fun ensureDeterministicHiddenServiceIdentity(context: Context, hiddenServiceDir: File): Boolean {
        val seed = runCatching {
            RouteIdentityRegistry.identityManager().getOnionServiceSeed()
        }.getOrNull() ?: return false
        return runCatching {
            val identity = DeterministicOnionIdentity.fromSeed(seed)
            seed.fill(0)
            val currentHostname = readOnionHostname(hiddenServiceDir)
            if (currentHostname == identity.hostname &&
                File(hiddenServiceDir, "hs_ed25519_secret_key").readBytesOrNull()?.contentEquals(identity.secretKeyFile) == true
            ) {
                return@runCatching false
            }
            clearDirectoryContents(hiddenServiceDir)
            hiddenServiceDir.mkdirs()
            writeHiddenServiceFile(File(hiddenServiceDir, "hs_ed25519_secret_key"), identity.secretKeyFile)
            writeHiddenServiceFile(File(hiddenServiceDir, "hs_ed25519_public_key"), identity.publicKeyFile)
            File(hiddenServiceDir, "hostname").writeText(identity.hostname + "\n", StandardCharsets.US_ASCII)
            record(context, "identidade onion reconstruida pela palavra-passe")
            true
        }.getOrElse {
            seed.fill(0)
            record(context, "falha ao reconstruir onion pela palavra-passe: ${it.message.orEmpty()}")
            false
        }
    }

    private fun writeHiddenServiceFile(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setExecutable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
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

    fun recoverAfterTransportFailure(context: Context, reason: String) {
        val appContext = context.applicationContext
        appContextRef = appContext
        if (manualStop) return
        ensureNetworkCallback(appContext)
        networkAvailable = isNetworkAvailable(appContext)
        _networkAvailableState.value = networkAvailable
        if (!networkAvailable) {
            record(appContext, "recuperacao Tor adiada: rede Android indisponivel")
            waitForNetwork(appContext)
            return
        }
        record(appContext, "recuperando Tor apos falha de envio: ${reason.ifBlank { "sem detalhe" }}")
        registerRestartFailure()
        scheduleRestart(appContext, delayMs = nextRestartDelay(requestedDelayMs = 10_000L))
    }

    fun stop(context: Context) {
        manualStop = true
        restartJob?.cancel()
        hostnameWaitJob?.cancel()
        startTimeoutJob?.cancel()
        networkLossJob?.cancel()
        networkLossJob = null
        networkRecoveryJob?.cancel()
        networkRecoveryJob = null
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

    private fun clearDirectoryContents(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { child ->
            child.deleteRecursively()
        }
    }

    private fun unzipSafely(zip: ZipInputStream, targetDir: File) {
        val targetCanonical = targetDir.canonicalFile
        while (true) {
            val entry = zip.nextEntry ?: break
            val entryName = entry.name.trim().removePrefix("/")
            if (entryName.isBlank()) {
                zip.closeEntry()
                continue
            }
            val outFile = File(targetDir, entryName)
            val canonicalOut = outFile.canonicalFile
            if (!canonicalOut.path.startsWith(targetCanonical.path + File.separator) &&
                canonicalOut.path != targetCanonical.path
            ) {
                zip.closeEntry()
                throw IllegalStateException("Backup inválido")
            }
            if (entry.isDirectory) {
                canonicalOut.mkdirs()
            } else {
                canonicalOut.parentFile?.mkdirs()
                FileOutputStream(canonicalOut).use { output ->
                    zip.copyTo(output)
                }
            }
            zip.closeEntry()
        }
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
            cancelPendingNetworkLoss()
            networkAvailable = isNetworkAvailable(appContext)
            _networkAvailableState.value = networkAvailable
            if (!networkAvailable) {
                record(appContext, "rede Android sem internet validada")
                scheduleNetworkLoss(appContext, "rede Android sem internet validada")
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
            if (available) {
                cancelPendingNetworkLoss()
                if (networkAvailable == available) return
                networkAvailable = true
                _networkAvailableState.value = true
                record(appContext, "rede Android validada")
                val status = _status.value
                if (!manualStop && status !is Status.Ready && status !is Status.Starting) {
                    scheduleRestart(appContext, delayMs = 250)
                }
            } else {
                if (networkAvailable == available) return
                scheduleNetworkLoss(appContext, "rede Android sem internet validada")
            }
        }

        override fun onLost(network: Network) {
            val appContext = appContextRef ?: return
            record(appContext, "rede Android perdida")
            scheduleNetworkLoss(appContext, "rede Android perdida")
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
                                    resetRestartBackoff()
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
                                registerRestartFailure()
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
                        appContext?.let {
                            registerRestartFailure()
                            scheduleRestart(it)
                        }
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
            cancelPendingNetworkLoss()
            waitForNetwork(appContext)
            return
        }
        val effectiveDelay = nextRestartDelay(delayMs)
        if (effectiveDelay > delayMs) {
            record(appContext, "restart desacelerado para ${effectiveDelay}ms")
        }
        _status.value = Status.Error(WAITING_NETWORK_MESSAGE)
        restartJob?.cancel()
        hostnameWaitJob?.cancel()
        startTimeoutJob?.cancel()
        restartJob = scope.launch {
            lastRestartAtMs = System.currentTimeMillis()
            record(appContext, "reiniciando Tor em ${effectiveDelay}ms")
            runCatching { appContext.stopService(Intent(appContext, TorService::class.java)) }
            delay(effectiveDelay)
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
                registerRestartFailure()
                record(appContext, "falha no restart: ${it.message.orEmpty()}")
                delay(nextRestartDelay(requestedDelayMs = 15_000L))
                if (!manualStop) {
                    scheduleRestart(appContext, delayMs = nextRestartDelay(requestedDelayMs = 15_000L))
                }
            }
        }
    }

    private fun scheduleNetworkLoss(appContext: Context, reason: String, delayMs: Long = 1_200L) {
        if (manualStop) return
        if (networkLossJob?.isActive == true) {
            record(appContext, "queda de rede já aguardando confirmação")
            return
        }
        networkLossJob?.cancel()
        networkLossJob = scope.launch {
            record(appContext, "$reason; confirmando em ${delayMs}ms")
            delay(delayMs)
            if (manualStop) return@launch
            val available = isNetworkAvailable(appContext)
            networkAvailable = available
            _networkAvailableState.value = available
            if (!available) {
                _status.value = Status.Error(WAITING_NETWORK_MESSAGE)
                networkLossJob = null
                waitForNetwork(appContext)
            } else {
                record(appContext, "queda de rede cancelada: conexao voltou")
                networkRecoveryJob?.cancel()
                networkRecoveryJob = null
                if (_status.value !is Status.Ready && _status.value !is Status.Starting) {
                    scheduleRestart(appContext, delayMs = 250)
                }
            }
        }
    }

    private fun cancelPendingNetworkLoss() {
        networkLossJob?.cancel()
        networkLossJob = null
        networkRecoveryJob?.cancel()
        networkRecoveryJob = null
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
        networkRecoveryJob?.cancel()
        networkLossJob = null
        networkRecoveryJob = null
        networkAvailable = false
        _networkAvailableState.value = false
        onionHost = ""
        record(appContext, "aguardando rede Android")
        NetworkBootstrapScheduler.schedule(appContext)
        if (!manualStop) {
            _status.value = Status.Error(WAITING_NETWORK_MESSAGE)
            startNetworkRecoveryWatch(appContext)
        }
    }

    private fun startNetworkRecoveryWatch(appContext: Context) {
        if (manualStop) return
        if (networkRecoveryJob?.isActive == true) return
        networkRecoveryJob = scope.launch {
            record(appContext, "aguardando reconexao automatica")
            while (isActive && !manualStop) {
                delay(2_000L)
                if (manualStop) return@launch
                if (!isNetworkAvailable(appContext)) {
                    continue
                }
                networkAvailable = true
                _networkAvailableState.value = true
                networkRecoveryJob = null
                record(appContext, "internet voltou; retomando Tor")
                scheduleRestart(appContext, delayMs = 250)
                return@launch
            }
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

    private fun File.readBytesOrNull(): ByteArray? {
        return runCatching { readBytes() }.getOrNull()
    }

    private fun nextRestartDelay(requestedDelayMs: Long): Long {
        val baseDelay = requestedDelayMs.coerceAtLeast(MIN_RESTART_DELAY_MS)
        val cappedCount = restartFailureCount.coerceAtMost(6)
        val exponentialDelay = MIN_RESTART_DELAY_MS shl cappedCount
        val candidate = maxOf(baseDelay, exponentialDelay).coerceAtMost(MAX_RESTART_DELAY_MS)
        val now = System.currentTimeMillis()
        if (nextRestartAllowedAtMs > now) {
            return maxOf(candidate, nextRestartAllowedAtMs - now)
        }
        return candidate
    }

    private fun registerRestartFailure() {
        restartFailureCount = (restartFailureCount + 1).coerceAtMost(10)
        val now = System.currentTimeMillis()
        nextRestartAllowedAtMs = now + nextRestartDelay(requestedDelayMs = MIN_RESTART_DELAY_MS)
    }

    private fun resetRestartBackoff() {
        restartFailureCount = 0
        lastRestartAtMs = 0L
        nextRestartAllowedAtMs = 0L
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

    private data class DeterministicOnionIdentity(
        val hostname: String,
        val secretKeyFile: ByteArray,
        val publicKeyFile: ByteArray
    ) {
        companion object {
            fun fromSeed(seed: ByteArray): DeterministicOnionIdentity {
                require(seed.size == 32) { "Seed onion invalido" }
                val publicKey = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
                val expandedSecret = expandedEd25519Secret(seed)
                val hostname = onionHostname(publicKey)
                return DeterministicOnionIdentity(
                    hostname = hostname,
                    secretKeyFile = ONION_SECRET_HEADER + expandedSecret,
                    publicKeyFile = ONION_PUBLIC_HEADER + publicKey
                )
            }

            private fun expandedEd25519Secret(seed: ByteArray): ByteArray {
                val digest = MessageDigest.getInstance("SHA-512").digest(seed)
                val left = digest.copyOfRange(0, 32)
                left[0] = (left[0].toInt() and 248).toByte()
                left[31] = ((left[31].toInt() and 63) or 64).toByte()
                val right = digest.copyOfRange(32, 64)
                digest.fill(0)
                return left + right
            }

            private fun onionHostname(publicKey: ByteArray): String {
                val checksum = ByteArray(32)
                SHA3Digest(256).apply {
                    update(ONION_CHECKSUM_PREFIX, 0, ONION_CHECKSUM_PREFIX.size)
                    update(publicKey, 0, publicKey.size)
                    update(byteArrayOf(ONION_VERSION), 0, 1)
                    doFinal(checksum, 0)
                }
                val payload = publicKey + checksum.copyOfRange(0, 2) + byteArrayOf(ONION_VERSION)
                checksum.fill(0)
                return base32NoPadding(payload).lowercase(Locale.ROOT) + ".onion"
            }

            private fun base32NoPadding(bytes: ByteArray): String {
                val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
                val output = StringBuilder((bytes.size * 8 + 4) / 5)
                var buffer = 0
                var bitsLeft = 0
                for (byte in bytes) {
                    buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
                    bitsLeft += 8
                    while (bitsLeft >= 5) {
                        val index = (buffer shr (bitsLeft - 5)) and 31
                        output.append(alphabet[index])
                        bitsLeft -= 5
                    }
                }
                if (bitsLeft > 0) {
                    val index = (buffer shl (5 - bitsLeft)) and 31
                    output.append(alphabet[index])
                }
                return output.toString()
            }
        }
    }
}
