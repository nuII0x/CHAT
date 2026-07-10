package com.null0x.chat

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.view.WindowManager
import com.null0x.chat.notification.MessageNotifier
import com.null0x.chat.security.AppDataWiper
import com.null0x.chat.security.AppSecurityManager
import com.null0x.chat.security.SensitiveClipboard
import com.null0x.chat.security.identity.MnemonicLanguage
import com.null0x.chat.network.BackgroundConnectionModeController
import com.null0x.chat.network.AppRestartReceiver
import com.null0x.chat.network.ChatNodeManager
import com.null0x.chat.network.TorManager
import com.null0x.chat.ui.chat.ChatScreen
import com.null0x.chat.ui.common.MnemonicLanguagePicker
import com.null0x.chat.ui.common.CursorAwareOutlinedTextField
import com.null0x.chat.ui.home.HomeScreen
import com.null0x.chat.ui.security.ProtectedWindowCapture
import com.null0x.chat.ui.theme.ChatTheme
import com.null0x.chat.ui.theme.AppearancePreference
import com.null0x.chat.ui.theme.ThemeMode
import com.null0x.chat.ui.theme.ThemePreference
import com.null0x.chat.update.AppUpdateManager
import com.null0x.chat.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    private var openChatUsername by mutableStateOf<String?>(null)
    private var openChatRequestVersion by mutableIntStateOf(0)
    private var launchedFromNotification = false
    private var lastStatusBarColor = Color.Black
    private var lastStatusBarDarkIcons = false
    private var lastNavigationBarColor = Color.Black
    private var initialContentReady = false
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(
                    this,
                    "Ative notificações para receber mensagens em tempo real",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onStart() {
        super.onStart()
        AppVisibility.markVisible()
        BackgroundConnectionModeController.onAppVisible(applicationContext)
    }

    override fun onStop() {
        AppVisibility.markHidden()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !initialContentReady }
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        AppSecurityManager.initialize(this)
        val initialOpenChatUsername = MessageNotifier.consumeOpenChatUsername(this, intent)
        openChatUsername = initialOpenChatUsername
        launchedFromNotification = !initialOpenChatUsername.isNullOrBlank()
        if (launchedFromNotification) {
            openChatRequestVersion++
        }
        ThemePreference.initialize(this)
        AppearancePreference.initialize(this)
        BackgroundConnectionModeController.initialize(this)
        AppUpdateManager.initialize(this)

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
            LaunchedEffect(Unit) {
                initialContentReady = true
            }
            var notificationPermissionRequested by rememberSaveable { mutableStateOf(false) }
            val themeMode by ThemePreference.themeMode.collectAsState()
            ChatTheme(themeMode = themeMode) {
                val gateState by AppSecurityManager.state.collectAsState()
                LaunchedEffect(gateState) {
                    when (gateState) {
                        AppSecurityManager.GateState.Unlocked -> {
                            ChatNodeManager.setAppRequestsPaused(false)
                            MessageNotifier(this@MainActivity).restorePendingNotifications()
                            requestBackgroundBootstrap()
                        }
                        AppSecurityManager.GateState.Locked,
                        AppSecurityManager.GateState.SetupRequired,
                        AppSecurityManager.GateState.PrivateAccessRequired -> {
                            ChatNodeManager.setAppRequestsPaused(true)
                            MessageNotifier(this@MainActivity).clearAll()
                        }
                        AppSecurityManager.GateState.Uninitialized -> Unit
                    }
                }
                when (gateState) {
                    AppSecurityManager.GateState.SetupRequired -> {
                        AppLockScreen(
                            themeMode = themeMode,
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
                            themeMode = themeMode,
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
                        LaunchedEffect(Unit) {
                            if (!notificationPermissionRequested) {
                                notificationPermissionRequested = true
                                requestNotificationPermission()
                            }
                        }
                        val pendingOpenChat = openChatUsername
                        LaunchedEffect(pendingOpenChat) {
                            val username = pendingOpenChat
                            if (!username.isNullOrBlank()) {
                                vm.selectTarget(username, fromNotification = true)
                                openChatUsername = null
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            HomeScreen(
                                vm,
                                onSignOut = { AppDataWiper.wipeAndExit(this@MainActivity) }
                            ) { peer -> vm.selectTarget(peer) }
                            if (vm.inChat) {
                                ChatScreen(
                                    vm,
                                    themeMode = themeMode,
                                    openChatRequestVersion = openChatRequestVersion,
                                    onBack = { vm.openHome() }
                                )
                            }
                        }
                    }
                    AppSecurityManager.GateState.Uninitialized -> {
                        AppLockScreen(
                            themeMode = themeMode,
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
            openChatRequestVersion++
            openChatUsername = username
        }
    }

    override fun onResume() {
        super.onResume()
        applyStatusBarColor(lastStatusBarColor, lastStatusBarDarkIcons)
        applyNavigationBarColor(lastNavigationBarColor)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            return
        }

        notificationPermissionLauncher.launch(permission)
    }

    fun applyStatusBarColor(
        statusBarColor: Color,
        darkIcons: Boolean = statusBarColor.luminance() > 0.5f
    ) {
        lastStatusBarColor = statusBarColor
        lastStatusBarDarkIcons = darkIcons
        window.statusBarColor = statusBarColor.toArgb()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = darkIcons
        }
    }

    fun applyNavigationBarColor(navigationBarColor: Color) {
        lastNavigationBarColor = navigationBarColor
        window.navigationBarColor = navigationBarColor.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightNavigationBars = navigationBarColor.luminance() > 0.5f
        }
    }

    private fun requestBackgroundBootstrap() {
        BackgroundConnectionModeController.onAppVisible(applicationContext)
        AppUpdateManager.enqueueCheck(applicationContext)
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
    var mnemonicLanguageName by rememberSaveable { mutableStateOf(MnemonicLanguage.ENGLISH.name) }
    val mnemonicLanguage = MnemonicLanguage.valueOf(mnemonicLanguageName)

    fun createNewRoute() {
        AppSecurityManager.createNewRouteIdentity(context, mnemonicLanguage)
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
        AppSecurityManager.restoreRouteIdentityFromSetup(context, restorePhrase, mnemonicLanguage)
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
                .imePadding()
                .navigationBarsPadding()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Token de acesso privado", style = MaterialTheme.typography.titleMedium)
                    when (mode) {
                        PrivateAccessMode.Chooser -> {
                            Text(
                                text = "Escolha como configurar sua rota antes de entrar no app.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            MnemonicLanguagePicker(
                                currentLanguage = mnemonicLanguage,
                                onLanguageSelected = { mnemonicLanguageName = it.name }
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
                            Text(
                                text = "Idioma: ${mnemonicLanguage.label}",
                                style = MaterialTheme.typography.bodySmall,
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
                                text = "Selecione o idioma correto e digite sua palavra-passe de 12 palavras para restaurar a rota.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            MnemonicLanguagePicker(
                                currentLanguage = mnemonicLanguage,
                                onLanguageSelected = { mnemonicLanguageName = it.name }
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
    themeMode: ThemeMode,
    title: String,
    subtitle: String,
    confirmLabel: String,
    readOnly: Boolean = false,
    onSubmit: (String) -> Result<Unit>
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val confirmFocusRequester = remember { FocusRequester() }
    val systemDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val lockColor = when (themeMode) {
        ThemeMode.SYSTEM -> if (systemDarkTheme) Color.Black else MaterialTheme.colorScheme.background
        ThemeMode.DARK -> Color.Black
        ThemeMode.LIGHT -> MaterialTheme.colorScheme.background
    }

    DisposableEffect(context) {
        val activity = context.findActivity()
        val previousMode = activity?.window?.attributes?.softInputMode
        activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        activity?.window?.statusBarColor = Color.Transparent.toArgb()
        activity?.window?.navigationBarColor = lockColor.toArgb()
        activity?.window?.let { window ->
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = lockColor.luminance() > 0.5f
                isAppearanceLightNavigationBars = lockColor.luminance() > 0.5f
            }
        }
        onDispose {
            if (previousMode != null) {
                activity?.window?.setSoftInputMode(previousMode)
            }
        }
    }

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
            password = ""
            confirmPassword = ""
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = lockColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CursorAwareOutlinedTextField(
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
                        keyboardActions = KeyboardActions(
                            onNext = { confirmFocusRequester.requestFocus() },
                            onDone = { submit() }
                        ),
                        supportingText = {
                            Text("Entrada privada: sem sugestões do teclado.")
                        }
                    )
                    if (!readOnly && confirmLabel == "Criar senha") {
                        CursorAwareOutlinedTextField(
                            value = confirmPassword,
                            onValueChange = {
                                confirmPassword = it
                                error = ""
                            },
                            label = { Text("Confirmar senha") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(confirmFocusRequester),
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
                    if (confirmLabel == "Criar senha") {
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
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
