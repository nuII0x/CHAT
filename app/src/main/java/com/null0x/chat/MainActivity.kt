package com.null0x.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.null0x.chat.notification.MessageNotifier
import com.null0x.chat.security.AppSecurityManager
import com.null0x.chat.network.ChatNodeManager
import com.null0x.chat.network.NetworkBootstrapScheduler
import com.null0x.chat.ui.chat.ChatScreen
import com.null0x.chat.ui.home.HomeScreen
import com.null0x.chat.ui.theme.ChatTheme
import com.null0x.chat.ui.theme.ThemePreference
import com.null0x.chat.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    private var openChatUsername by mutableStateOf<String?>(null)
    private var lastNavigationBarColor = Color.Black

    override fun onStart() {
        super.onStart()
        AppVisibility.markVisible()
    }

    override fun onStop() {
        AppVisibility.markHidden()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSecurityManager.initialize(this)
        if (AppSecurityManager.currentState() != AppSecurityManager.GateState.SetupRequired) {
            NetworkBootstrapScheduler.schedule(applicationContext)
            ChatNodeManager.ensureBackgroundNetwork(applicationContext)
        }
        ThemePreference.initialize(this)
        openChatUsername = MessageNotifier.consumeOpenChatUsername(this, intent)
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
                                    .onSuccess {
                                        NetworkBootstrapScheduler.schedule(applicationContext)
                                        ChatNodeManager.ensureBackgroundNetwork(applicationContext)
                                    }
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
                                        NetworkBootstrapScheduler.schedule(applicationContext)
                                        ChatNodeManager.ensureBackgroundNetwork(applicationContext)
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
                            keyboardType = KeyboardType.Password,
                            imeAction = if (confirmLabel == "Criar senha") ImeAction.Next else ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() })
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
