package com.null0x.chat.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.null0x.chat.model.DeliveryState
import com.null0x.chat.model.Message
import com.null0x.chat.network.TorManager
import com.null0x.chat.security.AppSecurityManager
import com.null0x.chat.security.SensitiveClipboard
import com.null0x.chat.security.identity.MessageCrypto
import com.null0x.chat.security.identity.MnemonicLanguage
import com.null0x.chat.security.identity.OnionInboxMessage
import com.null0x.chat.security.identity.OnionInboxStore
import com.null0x.chat.security.identity.RouteIdentityRegistry
import com.null0x.chat.ui.common.CursorAwareOutlinedTextField
import com.null0x.chat.ui.common.MnemonicLanguagePicker
import com.null0x.chat.ui.common.SystemBarsColorEffect
import com.null0x.chat.ui.common.SwipeToCloseContainer
import com.null0x.chat.ui.common.WindowDispositionScaffold
import com.null0x.chat.ui.chat.ChatMessageBubble
import com.null0x.chat.ui.maskedRouteLabel
import com.null0x.chat.ui.theme.AppearanceSettings
import com.null0x.chat.ui.theme.AppearancePreference
import com.null0x.chat.ui.theme.AppearanceSection
import com.null0x.chat.ui.theme.ThemeMode
import com.null0x.chat.ui.theme.ThemePreference
import com.null0x.chat.ui.theme.readableContentColor
import com.null0x.chat.ui.theme.themeBackgroundColor
import com.null0x.chat.ui.theme.themeDialogColor
import com.null0x.chat.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
internal fun SettingsTab(
    publicRoute: String,
    publicRouteToken: String,
    bottomPadding: androidx.compose.ui.unit.Dp,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    keepViewedMessages: Boolean,
    onKeepViewedMessagesChange: (Boolean) -> Unit,
    screenshotsEnabled: Boolean,
    onScreenshotsEnabledChange: (Boolean) -> Unit,
    showChatPresenceStatus: Boolean,
    onShowChatPresenceStatusChange: (Boolean) -> Unit,
    showChatLastActivity: Boolean,
    onShowChatLastActivityChange: (Boolean) -> Unit,
    contacts: List<ChatViewModel.ContactPreview>,
    locationSharingMode: ChatViewModel.LocationSharingMode,
    locationSharingAllowedRoutes: Set<String>,
    locationEmergencyAllowedRoutes: Set<String>,
    onShareLocationWithAll: () -> Unit,
    onShareLocationWithSelected: (Set<String>) -> Unit,
    onShareLocationInEmergency: (Set<String>) -> Unit,
    onDisableLocationSharing: () -> Unit,
    blockedContacts: List<ChatViewModel.ContactPreview>,
    onUnblockContact: (String) -> Unit,
    onContactsBackupRequested: () -> String,
    onLockApp: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val tokenLabel = publicRouteToken.ifBlank { "Aguardando token..." }
    var identityVersion by rememberSaveable { mutableStateOf(0) }
    var showTokensWindow by rememberSaveable { mutableStateOf(false) }
    var showAccountWindow by rememberSaveable { mutableStateOf(false) }
    var showBlockedWindow by rememberSaveable { mutableStateOf(false) }
    var showAppearancesWindow by rememberSaveable { mutableStateOf(false) }
    var showLocationWindow by rememberSaveable { mutableStateOf(false) }
    var showRestoreIdentity by rememberSaveable { mutableStateOf(false) }
    var showPrivateInbox by rememberSaveable { mutableStateOf(false) }
    val onionInboxStore = remember(context) { OnionInboxStore(context) }
    val privateInboxMessages = remember(identityVersion, showPrivateInbox) {
        onionInboxStore.listMessages()
    }
    val signingPublicKey = remember(identityVersion) {
        RouteIdentityRegistry.identityManager().getPublicKey()
    }
    val exchangePublicKey = remember(identityVersion) {
        RouteIdentityRegistry.identityManager().getExchangePublicKey()
    }
    val sendToken = remember(identityVersion) {
        RouteIdentityRegistry.sendTokenManager().ensureToken()
    }
    val publicSendPackage = remember(publicRoute, exchangePublicKey, sendToken) {
        JSONObject()
            .put("endpoint", publicSendEndpoint(publicRoute))
            .put("method", "POST")
            .put("recipientPublicKey", exchangePublicKey)
            .put("antiSpamToken", sendToken)
            .toString()
    }
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val exportOnionBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { destination ->
        if (destination == null) return@rememberLauncherForActivityResult
        val result = TorManager.exportOnionIdentity(context, destination)
        Toast.makeText(
            context,
            result.fold(
                onSuccess = { "Backup da rota salvo" },
                onFailure = { it.message ?: "Falha ao salvar backup" }
            ),
            Toast.LENGTH_SHORT
        ).show()
    }
    val importOnionBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { source ->
        if (source == null) return@rememberLauncherForActivityResult
        val result = TorManager.importOnionIdentity(context, source)
        Toast.makeText(
            context,
            result.fold(
                onSuccess = {
                    identityVersion++
                    "Rota restaurada"
                },
                onFailure = { it.message ?: "Falha ao restaurar backup" }
            ),
            Toast.LENGTH_SHORT
        ).show()
    }
    val exportContactsBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { destination ->
        if (destination == null) return@rememberLauncherForActivityResult
        val result = runCatching {
            context.contentResolver.openOutputStream(destination)?.use { output ->
                output.write(onContactsBackupRequested().toByteArray(Charsets.UTF_8))
            } ?: error("Destino indisponivel")
        }
        Toast.makeText(
            context,
            result.fold(
                onSuccess = { "Backup de contatos salvo" },
                onFailure = { it.message ?: "Falha ao salvar contatos" }
            ),
            Toast.LENGTH_SHORT
        ).show()
    }
    BackHandler(enabled = showTokensWindow && !showRestoreIdentity && !showPrivateInbox) {
        showTokensWindow = false
    }
    BackHandler(enabled = showBlockedWindow && !showRestoreIdentity && !showPrivateInbox) {
        showBlockedWindow = false
    }
    BackHandler(enabled = showAccountWindow && !showRestoreIdentity && !showPrivateInbox) {
        showAccountWindow = false
    }
    BackHandler(enabled = showAppearancesWindow && !showRestoreIdentity && !showPrivateInbox) {
        showAppearancesWindow = false
    }
    BackHandler(enabled = showLocationWindow && !showRestoreIdentity && !showPrivateInbox) {
        showLocationWindow = false
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 12.dp,
                end = 12.dp,
                bottom = bottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle("Conta") }
            item {
                SettingsRow(
                    title = "Tokens",
                    subtitle = "Itens para copiar sem bagunçar as ações",
                    onClick = { showTokensWindow = true }
                )
            }
            item {
                SettingsRow(
                    title = "Conta",
                    subtitle = "Envio, inbox, rotacao e restauracao",
                    onClick = { showAccountWindow = true }
                )
            }
            item {
                SettingsRow(
                    title = "Bloqueados",
                    subtitle = if (blockedContacts.isEmpty()) {
                        "Nenhum contato bloqueado"
                    } else {
                        "${blockedContacts.size} contatos bloqueados"
                    },
                    onClick = { showBlockedWindow = true }
                )
            }
            item {
            SettingsRow(
                title = "Aparências",
                subtitle = "Abas, diálogos e conversas",
                onClick = { showAppearancesWindow = true }
            )
            }
            if (!notificationsEnabled) {
                item {
                    SettingsRow(
                        title = "Ativar notificações",
                        subtitle = if (notificationsEnabled) {
                            "Ajuste útil se o Android limitou os alertas do app"
                        } else {
                            "Necessário para receber mensagens no Android 13+"
                        },
                        important = true,
                        trailing = if (!notificationsEnabled) {
                            {
                                AttentionBadge(count = 1)
                            }
                        } else null,
                        onClick = { openNotificationSettings(context) }
                    )
                }
            }
            item { SectionTitle("Privacidade") }
            item {
                SettingsRow(
                    title = "Localização",
                    subtitle = locationSharingSubtitle(
                        locationSharingMode,
                        if (locationSharingMode == ChatViewModel.LocationSharingMode.EMERGENCY) {
                            locationEmergencyAllowedRoutes.size
                        } else {
                            locationSharingAllowedRoutes.size
                        }
                    ),
                    onClick = { showLocationWindow = true }
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Estado no chat",
                    subtitle = if (showChatPresenceStatus) "Mostra Disponível ou Indisponível" else "Oculto no subtítulo do chat",
                    checked = showChatPresenceStatus,
                    onCheckedChange = onShowChatPresenceStatusChange
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Última atividade no chat",
                    subtitle = if (showChatLastActivity) "Mostra quando houve atividade recente" else "Oculta do subtítulo do chat",
                    checked = showChatLastActivity,
                    onCheckedChange = onShowChatLastActivityChange
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Manter historico de mensagens",
                    subtitle = "Padrao para novas conversas",
                    checked = keepViewedMessages,
                    onCheckedChange = onKeepViewedMessagesChange
                )
            }
            item {
                SettingsSwitchRow(
                    title = "Permitir print da tela",
                    subtitle = "Padrao para novas conversas",
                    checked = screenshotsEnabled,
                    onCheckedChange = onScreenshotsEnabledChange
                )
            }
            item {
                Text(
                    text = "Feito com amor e carinho por nuII0x",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp)
                )
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showTokensWindow,
            modifier = Modifier.fillMaxSize(),
            enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeOut()
        ) {
            AccountTokensScreen(
                tokenLabel = tokenLabel,
                sendToken = sendToken,
                signingPublicKey = signingPublicKey,
                exchangePublicKey = exchangePublicKey,
                onBack = { showTokensWindow = false },
                onLockApp = onLockApp,
                onCopyToken = {
                    val token = publicRouteToken.trim()
                    if (token.isNotBlank()) {
                        SensitiveClipboard.copy(context, "Token NoChat", token)
                        Toast.makeText(context, "Token sensivel copiado por 60 segundos", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Token ainda indisponivel", Toast.LENGTH_SHORT).show()
                    }
                },
                onCopySendToken = {
                    SensitiveClipboard.copy(context, "Token publico de envio", sendToken)
                    Toast.makeText(context, "Token público copiado por 60 segundos", Toast.LENGTH_SHORT).show()
                },
                onCopySigningKey = {
                    SensitiveClipboard.copy(context, "Chave publica do dono", signingPublicKey)
                    Toast.makeText(context, "Chave copiada por 60 segundos", Toast.LENGTH_SHORT).show()
                },
                onCopyExchangeKey = {
                    SensitiveClipboard.copy(context, "Chave publica de recebimento", exchangePublicKey)
                    Toast.makeText(context, "Chave copiada por 60 segundos", Toast.LENGTH_SHORT).show()
                }
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = showAccountWindow,
            modifier = Modifier.fillMaxSize(),
            enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeOut()
        ) {
            AccountActionsScreen(
                privateInboxMessages = privateInboxMessages,
                onBack = { showAccountWindow = false },
                onLockApp = onLockApp,
                onCopyPublicSend = {
                    SensitiveClipboard.copy(context, "Envio publico NoChat", publicSendPackage)
                    Toast.makeText(context, "Dados de envio copiados por 60 segundos", Toast.LENGTH_SHORT).show()
                },
                onOpenInbox = {
                    identityVersion++
                    showPrivateInbox = true
                },
                onRotateToken = {
                    RouteIdentityRegistry.sendTokenManager().rotateToken()
                    identityVersion++
                    Toast.makeText(context, "Token público rotacionado", Toast.LENGTH_SHORT).show()
                },
                onRestoreAccess = { showRestoreIdentity = true },
                onExportOnionBackup = {
                    exportOnionBackupLauncher.launch("rota-onion-backup.zip")
                },
                onImportOnionBackup = {
                    importOnionBackupLauncher.launch(arrayOf("application/zip"))
                },
                onExportContactsBackup = {
                    exportContactsBackupLauncher.launch("null0xchat-contatos.json")
                },
                onSignOut = onSignOut
            )
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showAppearancesWindow,
        modifier = Modifier.fillMaxSize(),
        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeOut()
    ) {
        AppearancesScreen(
            currentThemeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onBack = { showAppearancesWindow = false },
            onLockApp = onLockApp
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showLocationWindow,
        modifier = Modifier.fillMaxSize(),
        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeOut()
    ) {
        LocationSharingSettingsWindow(
            contacts = contacts,
            mode = locationSharingMode,
            selectedRoutes = locationSharingAllowedRoutes,
            emergencyRoutes = locationEmergencyAllowedRoutes,
            onBack = { showLocationWindow = false },
            onLockApp = onLockApp,
            onShareAll = onShareLocationWithAll,
            onShareSelected = onShareLocationWithSelected,
            onShareEmergency = onShareLocationInEmergency,
            onDisable = onDisableLocationSharing
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showBlockedWindow,
        modifier = Modifier.fillMaxSize(),
        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeOut()
    ) {
        BlockedContactsScreen(
            blockedContacts = blockedContacts,
            onBack = { showBlockedWindow = false },
            onLockApp = onLockApp,
            onUnblockContact = onUnblockContact
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showRestoreIdentity,
        modifier = Modifier.fillMaxSize(),
        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeOut()
    ) {
        RestoreIdentityScreen(
            onBack = { showRestoreIdentity = false },
            onLockApp = onLockApp,
            onRestored = {
                identityVersion++
                showRestoreIdentity = false
            }
        )
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = showPrivateInbox,
        modifier = Modifier.fillMaxSize(),
        enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + androidx.compose.animation.fadeOut()
    ) {
            PrivateInboxScreen(
                messages = privateInboxMessages,
                onBack = { showPrivateInbox = false },
                onLockApp = onLockApp,
                onDelete = { message ->
                onionInboxStore.delete(message.id)
                identityVersion++
            }
        )
    }
}

@Composable
internal fun AccountTokensScreen(
    tokenLabel: String,
    sendToken: String,
    signingPublicKey: String,
    exchangePublicKey: String,
    onBack: () -> Unit,
    onLockApp: () -> Unit,
    onCopyToken: () -> Unit,
    onCopySendToken: () -> Unit,
    onCopySigningKey: () -> Unit,
    onCopyExchangeKey: () -> Unit
) {
    SettingsWindowScaffold(
        title = "Tokens",
        subtitle = "Itens para copiar",
        onBack = onBack,
        onLockApp = onLockApp
    ) {
        item { SectionTitle("Tokens da rota") }
        item {
            SettingsRow(
                title = "Seu Token",
                subtitle = if (tokenLabel == "Aguardando token...") tokenLabel else "Token protegido",
                onClick = onCopyToken
            )
        }
        item {
            SettingsRow(
                title = "Token público de envio",
                subtitle = if (sendToken.isBlank()) "Aguardando token..." else "Token protegido",
                onClick = onCopySendToken
            )
        }
        item { SectionTitle("Chaves públicas") }
        item {
            SettingsRow(
                title = "Chave pública do dono",
                subtitle = signingPublicKey.ifBlank { "Aguardando chave..." },
                onClick = onCopySigningKey
            )
        }
        item {
            SettingsRow(
                title = "Chave de recebimento",
                subtitle = exchangePublicKey.ifBlank { "Aguardando chave..." },
                onClick = onCopyExchangeKey
            )
        }
    }
}

@Composable
internal fun AccountActionsScreen(
    privateInboxMessages: List<OnionInboxMessage>,
    onBack: () -> Unit,
    onLockApp: () -> Unit,
    onCopyPublicSend: () -> Unit,
    onOpenInbox: () -> Unit,
    onRotateToken: () -> Unit,
    onRestoreAccess: () -> Unit,
    onExportOnionBackup: () -> Unit,
    onImportOnionBackup: () -> Unit,
    onExportContactsBackup: () -> Unit,
    onSignOut: () -> Unit
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var wipingData by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    SettingsWindowScaffold(
        title = "Conta",
        subtitle = "Recebimento, backup e segurança",
        onBack = onBack,
        onLockApp = onLockApp
    ) {
        item { SectionTitle("Recebimento") }
        item {
            SettingsRow(
                title = "Dados para receber",
                subtitle = "Copia as informações que permitem enviar mensagens para você",
                onClick = onCopyPublicSend
            )
        }
        item {
            SettingsRow(
                title = "Caixa privada",
                subtitle = "${privateInboxMessages.size} mensagens recebidas fora da lista principal",
                onClick = onOpenInbox
            )
        }
        item { SectionTitle("Backup") }
        item {
            SettingsRow(
                title = "Backup de contatos",
                subtitle = "Salva sua lista de contatos no Drive ou nos arquivos",
                onClick = onExportContactsBackup
            )
        }
        item {
            SettingsRow(
                title = "Backup da rota",
                subtitle = "Guarda sua rota atual para recuperar depois",
                onClick = onExportOnionBackup
            )
        }
        item { SectionTitle("Recuperação") }
        item {
            SettingsRow(
                title = "Restaurar backup da rota",
                subtitle = "Recupera uma rota salva anteriormente",
                onClick = onImportOnionBackup
            )
        }
        item {
            SettingsRow(
                title = "Restaurar acesso privado",
                subtitle = "Use suas 12 palavras e a senha local para recuperar o acesso",
                onClick = onRestoreAccess
            )
        }
        item { SectionTitle("Segurança") }
        item {
            SettingsRow(
                title = "Trocar token público",
                subtitle = "Cancela o token antigo e cria um novo para receber mensagens",
                onClick = onRotateToken
            )
        }
        item {
            SettingsRow(
                title = "Sair",
                subtitle = "Apaga os dados locais e permite entrar em outra conta",
                important = true,
                onClick = { confirmSignOut = true }
            )
        }
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = {
                if (!wipingData) confirmSignOut = false
            },
            title = { Text("Sair desta conta?") },
            text = {
                Text(
                    "Isso apaga os dados locais do app neste aparelho. Depois será preciso criar ou restaurar uma conta."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !wipingData,
                    onClick = {
                        wipingData = true
                        scope.launch(Dispatchers.IO) {
                            onSignOut()
                        }
                    }
                ) {
                    Text(if (wipingData) "Saindo..." else "Sair")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !wipingData,
                    onClick = { confirmSignOut = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SettingsWindowScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onLockApp: () -> Unit,
    hideKeyboard: Boolean = true,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val systemDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val baseThemeMode = ThemePreference.themeMode.value
    val resolvedWindowColor = themeBackgroundColor(baseThemeMode, systemDarkTheme)
    WindowDispositionScaffold(
        title = title,
        subtitle = subtitle,
        onBack = onBack,
        windowColor = resolvedWindowColor,
        hideKeyboard = hideKeyboard,
        bottomActions = {
            IconButton(onClick = onLockApp) {
                Icon(
                    imageVector = Icons.Filled.VpnKey,
                    contentDescription = "Trancar app",
                    tint = readableContentColor(resolvedWindowColor)
                )
            }
        }
    ) {
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
}

@Composable
internal fun AppearancesScreen(
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onLockApp: () -> Unit
) {
    val context = LocalContext.current
    val appearance by AppearancePreference.appearance.collectAsState()
    SettingsWindowScaffold(
        title = "Aparências",
        subtitle = "Ajustes mínimos por área visual",
        onBack = onBack,
        onLockApp = onLockApp
    ) {
        item { SectionTitle("Conversas") }
        item {
            ThemeModeOptions(
                currentMode = appearance.conversations,
                onModeSelected = { AppearancePreference.setSectionMode(context, AppearanceSection.CONVERSATIONS, it) }
            )
        }
        item {
            ConversationAppearancePreview(appearance = appearance)
        }
        item { SectionTitle("Base") }
        item {
            ThemeModeOptions(
                currentMode = currentThemeMode,
                onModeSelected = onThemeModeChange
            )
        }
    }
}

@Composable
internal fun PrivateInboxScreen(
    messages: List<OnionInboxMessage>,
    onBack: () -> Unit,
    onLockApp: () -> Unit,
    onDelete: (OnionInboxMessage) -> Unit
) {
    val exchangePrivateKey = remember {
        runCatching { RouteIdentityRegistry.identityManager().getExchangePrivateKeyB64() }.getOrDefault("")
    }
    SettingsWindowScaffold(
        title = "Inbox privada",
        subtitle = "Mensagens recebidas por /send",
        onBack = onBack,
        onLockApp = onLockApp
    ) {
        if (messages.isEmpty()) {
            item {
                Text(
                    text = "Nenhuma mensagem recebida por /send.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            itemsIndexed(messages, key = { _, message -> message.id }) { _, message ->
                val plainText = remember(message.id, exchangePrivateKey) {
                    MessageCrypto.decryptMessage(message.ciphertext, exchangePrivateKey)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = plainText ?: "Mensagem criptografada",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formatTime(message.receivedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { onDelete(message) }) {
                            Text("Excluir")
                        }
                        ListSeparator()
                    }
                }
            }
        }
    }
}

@Composable
internal fun RestoreIdentityScreen(
    onBack: () -> Unit,
    onLockApp: () -> Unit,
    onRestored: () -> Unit
) {
    val context = LocalContext.current
    var mnemonic by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    var mnemonicLanguageName by rememberSaveable { mutableStateOf(MnemonicLanguage.ENGLISH.name) }
    val mnemonicLanguage = MnemonicLanguage.valueOf(mnemonicLanguageName)
    SettingsWindowScaffold(
        title = "Restaurar acesso privado",
        subtitle = "Escolha o idioma certo, use suas 12 palavras e a senha local",
        onBack = onBack,
        onLockApp = onLockApp,
        hideKeyboard = false
    ) {
        item {
            MnemonicLanguagePicker(
                currentLanguage = mnemonicLanguage,
                onLanguageSelected = { mnemonicLanguageName = it.name }
            )
        }
        item {
                CursorAwareOutlinedTextField(
                    value = mnemonic,
                    onValueChange = {
                        mnemonic = it
                    error = ""
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                label = { Text("12 palavras") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text
                )
            )
        }
        item {
                CursorAwareOutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                    error = ""
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Senha local") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )
            )
        }
        if (error.isNotBlank()) {
            item {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Button(
                onClick = {
                    val result = AppSecurityManager.restoreRouteIdentity(
                        context,
                        mnemonic,
                        password,
                        mnemonicLanguage
                    )
                    if (result.isSuccess) {
                        Toast.makeText(context, "Acesso privado restaurado", Toast.LENGTH_SHORT).show()
                        onRestored()
                    } else {
                        error = result.exceptionOrNull()?.message ?: "Senha errada, tente novamente"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Restaurar")
            }
        }
    }
}

internal fun publicSendEndpoint(route: String): String {
    val clean = route.trim()
    if (!clean.startsWith("onion:", ignoreCase = true)) return "Aguardando rota"
    val value = clean.substringAfter(':')
    val separator = value.lastIndexOf(':')
    if (separator <= 0 || separator == value.lastIndex) return "Aguardando rota"
    val host = value.substring(0, separator)
    val port = value.substring(separator + 1)
    return "http://$host:$port/send"
}

@Composable
internal fun BlockedContactsScreen(
    blockedContacts: List<ChatViewModel.ContactPreview>,
    onBack: () -> Unit,
    onLockApp: () -> Unit,
    onUnblockContact: (String) -> Unit
) {
    SettingsWindowScaffold(
        title = "Bloqueados",
        subtitle = "A lista e as ações de bloqueio",
        onBack = onBack,
        onLockApp = onLockApp
    ) {
        item { SectionTitle("Contatos bloqueados") }
        if (blockedContacts.isEmpty()) {
            item {
                SettingsRow(
                    title = "Nenhum contato bloqueado",
                    subtitle = "Quando você bloquear alguém, ele vai aparecer aqui",
                    onClick = { }
                )
            }
        } else {
            itemsIndexed(blockedContacts, key = { _, item -> item.username }) { _, item ->
                SettingsRow(
                    title = item.displayName,
                    subtitle = maskedRouteLabel(item.username),
                    onClick = { },
                    trailing = {
                        TextButton(onClick = { onUnblockContact(item.username) }) {
                            Text("Desbloquear")
                        }
                    }
                )
            }
        }
    }
}

data class ThemeOption(
    val mode: ThemeMode,
    val title: String
)

@Composable
internal fun ThemeModeOptions(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    val options = remember {
        listOf(
            ThemeOption(ThemeMode.BLUE, "Azul"),
            ThemeOption(ThemeMode.LIGHT, "Claro"),
            ThemeOption(ThemeMode.DARK, "Escuro"),
            ThemeOption(ThemeMode.PINK, "Rosa"),
            ThemeOption(ThemeMode.SYSTEM, "Sistema")
        )
    }
    val selectedOption = remember(currentMode, options) {
        options.firstOrNull { it.mode == currentMode } ?: options.first()
    }
    val systemDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val topRow = remember { listOf(options[0], options[1], options[3]) }
    val bottomRow = remember { listOf(options[4], options[2]) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ThemeModeOptionsRow(
            options = topRow,
            selectedOption = selectedOption,
            systemDarkTheme = systemDarkTheme,
            onModeSelected = onModeSelected
        )
        ThemeModeOptionsRow(
            options = bottomRow,
            selectedOption = selectedOption,
            systemDarkTheme = systemDarkTheme,
            onModeSelected = onModeSelected
        )
    }
}

@Composable
private fun ThemeModeOptionsRow(
    options: List<ThemeOption>,
    selectedOption: ThemeOption,
    systemDarkTheme: Boolean,
    onModeSelected: (ThemeMode) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val isSelected = selectedOption.mode == option.mode
            val previewColor = themeModePreviewColor(option.mode, systemDarkTheme)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .border(
                        width = if (isSelected) 1.25.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { onModeSelected(option.mode) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(previewColor, CircleShape)
                    )
                    Text(
                        text = option.title,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge
                    )
                    RadioButton(
                        selected = isSelected,
                        onClick = { onModeSelected(option.mode) },
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

private fun themeModePreviewColor(mode: ThemeMode, systemDarkTheme: Boolean): Color {
    return when (mode) {
        ThemeMode.BLUE -> Color(0xFF1976D2)
        ThemeMode.LIGHT -> Color(0xFFE8E8E8)
        ThemeMode.DARK -> Color(0xFF202020)
        ThemeMode.PINK -> Color(0xFFD81B60)
        ThemeMode.SYSTEM -> if (systemDarkTheme) Color(0xFF202020) else Color(0xFFE8E8E8)
    }
}

@Composable
private fun ConversationAppearancePreview(appearance: AppearanceSettings) {
    val previewMessages = remember {
        listOf(
            Message(
                id = "appearance-preview-mine",
                text = "Oi, ficou assim",
                isMine = true,
                timestamp = 1_735_737_600_000L,
                delivery = DeliveryState.Delivered
            ),
            Message(
                id = "appearance-preview-peer",
                text = "Visualizei",
                isMine = false,
                timestamp = 1_735_737_660_000L
            )
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            previewMessages.forEach { message ->
                ChatMessageBubble(
                    msg = message,
                    allowTextSelection = false,
                    appearance = appearance,
                    animateIn = false,
                    onEntranceAnimationFinished = {},
                    onClick = {}
                )
            }
        }
    }
}

private fun locationSharingSubtitle(mode: ChatViewModel.LocationSharingMode, selectedCount: Int): String {
    return when (mode) {
        ChatViewModel.LocationSharingMode.ALL -> "Compartilhando com todos"
        ChatViewModel.LocationSharingMode.SELECTED -> if (selectedCount == 0) {
            "Selecionado, mas sem contatos"
        } else {
            "Compartilhando com $selectedCount contatos"
        }
        ChatViewModel.LocationSharingMode.EMERGENCY -> if (selectedCount == 0) {
            "Emergência ativa"
        } else {
            "Emergência ativa para $selectedCount contatos"
        }
        ChatViewModel.LocationSharingMode.NONE -> "Desligado"
        ChatViewModel.LocationSharingMode.UNSET -> "Escolha quem pode ver sua distância"
    }
}

@Composable
private fun LocationSharingSettingsWindow(
    contacts: List<ChatViewModel.ContactPreview>,
    mode: ChatViewModel.LocationSharingMode,
    selectedRoutes: Set<String>,
    emergencyRoutes: Set<String>,
    onBack: () -> Unit,
    onLockApp: () -> Unit,
    onShareAll: () -> Unit,
    onShareSelected: (Set<String>) -> Unit,
    onShareEmergency: (Set<String>) -> Unit,
    onDisable: () -> Unit
) {
    var showEmergencyWindow by rememberSaveable { mutableStateOf(false) }
    var draftRoutes by remember(selectedRoutes) { mutableStateOf(selectedRoutes) }
    fun updateSelectedRoutes(routes: Set<String>) {
        draftRoutes = routes
        if (routes.isEmpty()) {
            onDisable()
        } else {
            onShareSelected(routes)
        }
    }
    WindowDispositionScaffold(
        title = "Localização",
        subtitle = locationSharingSubtitle(
            mode,
            if (mode == ChatViewModel.LocationSharingMode.EMERGENCY) emergencyRoutes.size else selectedRoutes.size
        ),
        onBack = onBack,
        windowColor = MaterialTheme.colorScheme.background,
        bottomActions = {
            IconButton(onClick = onLockApp) {
                Icon(
                    Icons.Filled.VpnKey,
                    contentDescription = "Trancar app",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle("Compartilhamento") }
            item {
                SettingsRow(
                    title = "Compartilhar com todos",
                    subtitle = "Todos os contatos aceitos podem calcular sua distância",
                    trailing = { RadioButton(selected = mode == ChatViewModel.LocationSharingMode.ALL, onClick = null) },
                    onClick = onShareAll
                )
            }
            item {
                SettingsRow(
                    title = "Não compartilhar",
                    subtitle = "Você verá distâncias só de quem compartilhar com você",
                    trailing = { RadioButton(selected = mode == ChatViewModel.LocationSharingMode.NONE, onClick = null) },
                    onClick = onDisable
                )
            }
            item {
                SettingsRow(
                    title = "Emergência",
                    subtitle = if (emergencyRoutes.isEmpty()) {
                        "Configure sua lista de confiança"
                    } else {
                        "${emergencyRoutes.size} contatos de confiança"
                    },
                    onClick = { showEmergencyWindow = true }
                )
            }
            item { SectionTitle("Selecionar contatos") }
            if (contacts.isEmpty()) {
                item {
                    Text(
                        text = "Nenhum contato aceito para selecionar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            } else {
                itemsIndexed(contacts.filter { it.accepted }, key = { _, item -> item.username }) { _, contact ->
                    SettingsRow(
                        title = contact.displayName,
                        subtitle = maskedRouteLabel(contact.username),
                        trailing = {
                            Checkbox(
                                checked = draftRoutes.contains(contact.username),
                                onCheckedChange = { checked ->
                                    updateSelectedRoutes(if (checked) {
                                        draftRoutes + contact.username
                                    } else {
                                        draftRoutes - contact.username
                                    })
                                }
                            )
                        },
                        onClick = {
                            updateSelectedRoutes(if (draftRoutes.contains(contact.username)) {
                                draftRoutes - contact.username
                            } else {
                                draftRoutes + contact.username
                            })
                        }
                    )
                }
            }
        }
    }
    if (showEmergencyWindow) {
        EmergencyTrustedContactsWindow(
            contacts = contacts,
            selectedRoutes = emergencyRoutes,
            onBack = { showEmergencyWindow = false },
            onLockApp = onLockApp,
            onShareEmergency = onShareEmergency
        )
    }
}

@Composable
private fun EmergencyTrustedContactsWindow(
    contacts: List<ChatViewModel.ContactPreview>,
    selectedRoutes: Set<String>,
    onBack: () -> Unit,
    onLockApp: () -> Unit,
    onShareEmergency: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    var draftRoutes by remember(selectedRoutes) {
        mutableStateOf(selectedRoutes.take(3).toSet())
    }
    var showEmergencyPassword by rememberSaveable { mutableStateOf(false) }
    var emergencyPasswordError by rememberSaveable { mutableStateOf("") }
    val acceptedContacts = remember(contacts) {
        contacts
            .filter { it.accepted }
    }
    val hasChanges = remember(draftRoutes, selectedRoutes) {
        draftRoutes != selectedRoutes
    }
    WindowDispositionScaffold(
        title = "Emergência",
        subtitle = if (draftRoutes.isEmpty()) {
            "Escolha até 3 contatos"
        } else {
            "${draftRoutes.size}/3 contatos de confiança"
        },
        onBack = onBack,
        windowColor = MaterialTheme.colorScheme.background,
        topActions = {
            TextButton(
                onClick = {
                    if (hasChanges) {
                        emergencyPasswordError = ""
                        showEmergencyPassword = true
                    } else {
                        onBack()
                    }
                }
            ) {
                Text("Feito")
            }
        },
        bottomActions = {
            IconButton(onClick = onLockApp) {
                Icon(
                    Icons.Filled.VpnKey,
                    contentDescription = "Trancar app",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SectionTitle("Contatos de confiança") }
            item {
                Text(
                    text = "Escolha até 3 contatos de confiança para este modo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
                )
            }
            if (acceptedContacts.isEmpty()) {
                item {
                    Text(
                        text = "Nenhum contato aceito para selecionar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            } else {
                itemsIndexed(acceptedContacts, key = { _, item -> item.username }) { _, contact ->
                    SettingsRow(
                        title = contact.displayName,
                        subtitle = maskedRouteLabel(contact.username),
                        trailing = {
                            Checkbox(
                                checked = draftRoutes.contains(contact.username),
                                enabled = draftRoutes.contains(contact.username) || draftRoutes.size < 3,
                                onCheckedChange = { checked ->
                                    draftRoutes = if (checked) {
                                        if (draftRoutes.size >= 3) draftRoutes else draftRoutes + contact.username
                                    } else {
                                        draftRoutes - contact.username
                                    }
                                }
                            )
                        },
                        onClick = {
                            draftRoutes = if (draftRoutes.contains(contact.username)) {
                                draftRoutes - contact.username
                            } else {
                                if (draftRoutes.size >= 3) draftRoutes else draftRoutes + contact.username
                            }
                        }
                    )
                }
            }
        }
    }
    if (showEmergencyPassword) {
        PasswordConfirmDialog(
            title = "Adicionar contatos de Emergência",
            message = "Confirme sua senha para adicionar novos contatos a esta lista de confiança.",
            error = emergencyPasswordError,
            baseThemeMode = ThemePreference.themeMode.value,
            onDismiss = {
                showEmergencyPassword = false
                emergencyPasswordError = ""
            },
            onConfirm = { password ->
                val result = AppSecurityManager.verifyPassword(context, password)
                if (result.isSuccess) {
                    onShareEmergency(draftRoutes)
                    showEmergencyPassword = false
                    emergencyPasswordError = ""
                    onBack()
                } else {
                    emergencyPasswordError = result.exceptionOrNull()?.message ?: "Senha incorreta"
                }
            }
        )
    }
}

@Composable
internal fun SettingsRow(
    title: String,
    subtitle: String,
    important: Boolean = false,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.widthIn(max = 220.dp)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            color = if (important) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
                if (trailing != null) {
                    Box(contentAlignment = Alignment.CenterEnd) {
                        trailing()
                    }
                } else {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            ListSeparator()
        }
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        color = Color.Transparent,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.widthIn(max = 220.dp)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)
                    )
                }
                Switch(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = onCheckedChange
                )
            }
            ListSeparator()
        }
    }
}

@Composable
internal fun ListSeparator(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
    )
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

internal fun pluralize(count: Int, singular: String, plural: String): String {
    return if (count == 1) "1 $singular" else "$count $plural"
}

@Composable
internal fun PasswordConfirmDialog(
    title: String,
    message: String,
    error: String,
    baseThemeMode: ThemeMode,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val systemDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val dialogColor = themeDialogColor(baseThemeMode, systemDarkTheme)
    val dialogContentColor = readableContentColor(dialogColor)
    val outlineColor = dialogContentColor.copy(alpha = 0.36f)
    var password by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (password.isNotBlank()) {
            onConfirm(password)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = title,
                color = dialogContentColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = dialogContentColor
                )
                CursorAwareOutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Senha") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    supportingText = {
                        Text(
                            text = "Entrada privada: sem sugestões do teclado.",
                            color = dialogContentColor.copy(alpha = 0.72f)
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = dialogContentColor,
                        unfocusedTextColor = dialogContentColor,
                        focusedLabelColor = dialogContentColor,
                        unfocusedLabelColor = dialogContentColor.copy(alpha = 0.74f),
                        cursorColor = dialogContentColor,
                        focusedBorderColor = dialogContentColor,
                        unfocusedBorderColor = outlineColor,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = dialogContentColor)
                )
                if (error.isNotBlank()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        containerColor = dialogColor,
        tonalElevation = 0.dp,
        confirmButton = {
            TextButton(
                onClick = submit,
                colors = ButtonDefaults.textButtonColors(contentColor = dialogContentColor)
            ) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = dialogContentColor)
            ) { Text("Cancelar") }
        }
    )
}

internal fun openNotificationSettings(context: Context) {
    val appContext = context.applicationContext
    val directIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${appContext.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val targetIntent = directIntent.takeIf { it.resolveActivity(appContext.packageManager) != null }
        ?: fallbackIntent
    runCatching {
        appContext.startActivity(targetIntent)
    }.onFailure {
        Toast.makeText(appContext, "Abra as notificacoes do app nas configuracoes", Toast.LENGTH_SHORT).show()
    }
}
