package com.null0x.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.null0x.chat.notification.MessageNotifier
import com.null0x.chat.security.AppSecurityManager
import com.null0x.chat.security.SensitiveClipboard
import com.null0x.chat.network.BackgroundNetworkPreference
import com.null0x.chat.network.BackgroundRelaunchPreference
import com.null0x.chat.network.ChatNodeManager
import com.null0x.chat.network.AppNetworkService
import com.null0x.chat.network.AppRestartReceiver
import com.null0x.chat.network.NetworkBootstrapScheduler
import com.null0x.chat.ui.chat.ChatScreen
import com.null0x.chat.ui.home.HomeScreen
import com.null0x.chat.ui.security.ProtectedWindowCapture
import com.null0x.chat.ui.theme.ChatTheme
import com.null0x.chat.ui.theme.ThemePreference
import com.null0x.chat.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    private var openChatUsername by mutableStateOf<String?>(null)
    private var launchedFromNotification = false
    private var lastNavigationBarColor = Color.Black

    companion object {
        @Volatile
        private var backgroundBootstrapRequested = false
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.markVisible()
        AppRestartReceiver.clearPendingRelaunch(applicationContext)
    }

    override fun onStop() {
        AppVisibility.markHidden()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSecurityManager.initialize(this)
        val initialOpenChatUsername = MessageNotifier.consumeOpenChatUsername(this, intent)
        openChatUsername = initialOpenChatUsername
        launchedFromNotification = !initialOpenChatUsername.isNullOrBlank()
        if (
            AppSecurityManager.currentState() == AppSecurityManager.GateState.Locked ||
            AppSecurityManager.currentState() == AppSecurityManager.GateState.Unlocked
        ) {
            if (!launchedFromNotification) {
                AppNetworkService.start(applicationContext)
                NetworkBootstrapScheduler.schedule(applicationContext)
                ChatNodeManager.ensureBackgroundNetwork(applicationContext)
            }
        }
        ThemePreference.initialize(this)
        BackgroundNetworkPreference.initialize(this)
        BackgroundRelaunchPreference.initialize(this)
        requestNotificationPermission()

        val vmFactory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return ChatViewModel(application) as T
                }
                throw IllegalArgumentException("Classe de ViewModel desconhecida: ${modelClass.name}")
            }
        }

        setContent {
            val themeMode by ThemePreference.themeMode.collectAsState()
            val backgroundNetworkEnabled by BackgroundNetworkPreference.enabled.collectAsState()
            val backgroundRelaunchEnabled by BackgroundRelaunchPreference.enabled.collectAsState()
            ChatTheme(themeMode = themeMode) {
                val navigationBarColor = MaterialTheme.colorScheme.background
                SideEffect {
                    applySystemBarColors(navigationBarColor)
                }
                val gateState by AppSecurityManager.state.collectAsState()
                when (gateState) {
                    AppSecurityManager.GateState.SetupRequired -> {
                        AppLockScreen(
                            title = "Crie a senha do app",
                            subtitle = "Ela protege suas conversas e desbloqueia o acesso no futuro.",
                            confirmLabel = "Criar senha",
                            onSubmit = { password ->
                                AppSecurityManager.createPassword(this@MainActivity, password)
                            }
                        )
                    }
                    AppSecurityManager.GateState.PrivateAccessRequired -> {
                        PrivateAccessSetupScreen(
                            onReady = {
                                requestBackgroundBootstrap()
                            }
                        )
                    }
                    AppSecurityManager.GateState.Locked -> {
                        AppLockScreen(
                            title = "App trancado",
                            subtitle = "Digite a senha para acessar as conversas.",
                            confirmLabel = "Destrancar",
                            onSubmit = { password ->
                                AppSecurityManager.unlock(this@MainActivity, password)
                                    .onSuccess {
                                        requestBackgroundBootstrap()
                                    }
                            }
                        )
                    }
                    AppSecurityManager.GateState.Unlocked -> {
                        val vm: ChatViewModel = viewModel(factory = vmFactory)
                        val pendingOpenChat = openChatUsername
                        LaunchedEffect(pendingOpenChat) {
                            val username = pendingOpenChat
                            if (!username.isNullOrBlank()) {
                                vm.selectTarget(username)
                                openChatUsername = null
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            HomeScreen(
                                vm,
                                themeMode = themeMode,
                                onThemeModeChange = { mode ->
                                    ThemePreference.setThemeMode(this@MainActivity, mode)
                                },
                                backgroundNetworkEnabled = backgroundNetworkEnabled,
                                onBackgroundNetworkChange = { enabled ->
                                    BackgroundNetworkPreference.setEnabled(this@MainActivity, enabled)
                                },
                                backgroundRelaunchEnabled = backgroundRelaunchEnabled,
                                onBackgroundRelaunchChange = { enabled ->
                                    BackgroundRelaunchPreference.setEnabled(this@MainActivity, enabled)
                                },
                                onLockApp = { AppSecurityManager.lock() }
                            ) { peer -> vm.selectTarget(peer) }
                            if (vm.inChat) {
                                ChatScreen(vm, onBack = { vm.openHome() })
                            }
                        }
                    }
                    AppSecurityManager.GateState.Uninitialized -> {
                        AppLockScreen(
                            title = "Preparando segurança",
                            subtitle = "Só um instante.",
                            confirmLabel = "Certo",
                            readOnly = true,
                            onSubmit = { Result.success(Unit) }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val username = MessageNotifier.consumeOpenChatUsername(this, intent)
        if (!username.isNullOrBlank()) {
            launchedFromNotification = true
            openChatUsername = username
        }
    }

    override fun onResume() {
        super.onResume()
        applySystemBarColors()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            return
        }

        ActivityCompat.requestPermissions(this, arrayOf(permission), 1001)
    }

    private fun applySystemBarColors() {
        applySystemBarColors(lastNavigationBarColor)
    }

    private fun applySystemBarColors(navigationBarColor: Color) {
        lastNavigationBarColor = navigationBarColor
        window.statusBarColor = Color.Black.toArgb()
        window.navigationBarColor = navigationBarColor.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = navigationBarColor.luminance() > 0.5f
        }
    }

    private fun requestBackgroundBootstrap() {
        val shouldBootstrap = synchronized(MainActivity::class.java) {
            if (backgroundBootstrapRequested) {
                false
            } else {
                backgroundBootstrapRequested = true
                true
            }
        }
        if (!shouldBootstrap) return

        runCatching {
            AppNetworkService.start(applicationContext)
            NetworkBootstrapScheduler.schedule(applicationContext)
            ChatNodeManager.ensureBackgroundNetwork(applicationContext)
        }.onFailure {
            synchronized(MainActivity::class.java) {
                backgroundBootstrapRequested = false
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PrivateAccessSetupScreen(
    onReady: () -> Unit
) {
    val context = LocalContext.current
    ProtectedWindowCapture(enabled = true)
    var mode by rememberSaveable { mutableStateOf(PrivateAccessMode.Chooser) }
    var generatedPhrase by rememberSaveable { mutableStateOf("") }
    var restorePhrase by rememberSaveable { mutableStateOf("") }
    var restoreError by rememberSaveable { mutableStateOf("") }

    fun createNewRoute() {
        AppSecurityManager.createNewRouteIdentity(context)
            .onSuccess {
                generatedPhrase = it
                restoreError = ""
                mode = PrivateAccessMode.CreateNew
            }
            .onFailure {
                restoreError = it.message ?: "Não foi possível configurar o acesso privado"
            }
    }

    fun finishWithCreatedRoute() {
        AppSecurityManager.completePrivateAccessSetup()
        onReady()
    }

    fun restoreRoute() {
        AppSecurityManager.restoreRouteIdentityFromSetup(context, restorePhrase)
            .onSuccess {
                restoreError = ""
                onReady()
            }
            .onFailure {
                restoreError = it.message ?: "Não foi possível configurar o acesso privado"
            }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 2.dp) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Token de acesso privado", style = MaterialTheme.typography.headlineSmall)
                    when (mode) {
                        PrivateAccessMode.Chooser -> {
                            Text(
                                text = "Escolha como configurar sua rota antes de entrar no app.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { createNewRoute() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Criar nova rota")
                            }
                            TextButton(
                                onClick = {
                                    restoreError = ""
                                    mode = PrivateAccessMode.Restore
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Restaurar acesso privado")
                            }
                        }
                        PrivateAccessMode.CreateNew -> {
                            Text(
                                text = "Guarde estas 12 palavras. Elas não serão mostradas novamente.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = generatedPhrase,
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            TextButton(
                                onClick = {
                                    SensitiveClipboard.copy(
                                        context,
                                        "Token de acesso privado",
                                        generatedPhrase
                                    )
                                    Toast.makeText(
                                        context,
                                        "Palavra-passe copiada por 60 segundos",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Copiar palavra-passe")
                            }
                            Button(
                                onClick = { finishWithCreatedRoute() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Entrar")
                            }
                        }
                        PrivateAccessMode.Restore -> {
                            Text(
                                text = "Digite sua palavra-passe de 12 palavras para restaurar a rota.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = restorePhrase,
                                onValueChange = {
                                    restorePhrase = it
                                    restoreError = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 5,
                                label = { Text("12 palavras") },
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Done
                                )
                            )
                            if (restoreError.isNotBlank()) {
                                Text(restoreError, color = MaterialTheme.colorScheme.error)
                            }
                            Button(
                                onClick = { restoreRoute() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Restaurar e entrar")
                            }
                            TextButton(
                                onClick = {
                                    restoreError = ""
                                    mode = PrivateAccessMode.Chooser
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Voltar")
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class PrivateAccessMode {
    Chooser,
    CreateNew,
    Restore
}

@androidx.compose.runtime.Composable
private fun AppLockScreen(
    title: String,
    subtitle: String,
    confirmLabel: String,
    readOnly: Boolean = false,
    onSubmit: (String) -> Result<Unit>
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    fun submit() {
        if (readOnly) return
        if (password.isBlank()) {
            error = "Digite uma senha."
            return
        }
        if (confirmLabel == "Criar senha" && password != confirmPassword) {
            error = "As senhas não conferem."
            return
        }
        val result = onSubmit(password)
        if (result.isFailure) {
            error = if (confirmLabel == "Criar senha") {
                "Não foi possível criar a senha, tente novamente"
            } else {
                "Senha errada, tente novamente"
            }
        } else {
            password = ""
            confirmPassword = ""
            error = ""
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 2.dp) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            error = ""
                        },
                        label = { Text("Senha") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = if (confirmLabel == "Criar senha") ImeAction.Next else ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        supportingText = {
                            Text("Entrada privada: sem sugestões do teclado.")
                        }
                    )
                    if (!readOnly && confirmLabel == "Criar senha") {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                error = ""
                            },
                            label = { Text("Confirmar senha") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() })
                        )
                    }
                    if (error.isNotBlank()) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = { submit() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(confirmLabel)
                    }
                }
            }
        }
    }
}
