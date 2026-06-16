package com.null0x.chat.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
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
import com.null0x.chat.ui.common.SwipeToCloseContainer
import com.null0x.chat.ui.maskedRouteLabel
import com.null0x.chat.ui.theme.ThemeMode
import com.null0x.chat.viewmodel.ChatViewModel
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
    blockedContacts: List<ChatViewModel.ContactPreview>,
    onUnblockContact: (String) -> Unit,
    onContactsBackupRequested: () -> String,
    onLockApp: () -> Unit
) {
    val context = LocalContext.current
    val tokenLabel = publicRouteToken.ifBlank { "Aguardando token..." }
    var identityVersion by rememberSaveable { mutableStateOf(0) }
    var showTokensWindow by rememberSaveable { mutableStateOf(false) }
    var showAccountWindow by rememberSaveable { mutableStateOf(false) }
    var showBlockedWindow by rememberSaveable { mutableStateOf(false) }
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
            item { SectionTitle("Tema") }
            item {
                ThemeModeOptions(
                    currentMode = themeMode,
                    onModeSelected = onThemeModeChange
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
                        SensitiveClipboard.copy(context, "Token NullChat", token)
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
                    SensitiveClipboard.copy(context, "Envio publico NullChat", publicSendPackage)
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
                    exportContactsBackupLauncher.launch("nullchat-contatos.json")
                }
            )
        }
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
    onExportContactsBackup: () -> Unit
) {
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
    }
}

@Composable
internal fun SettingsWindowScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onLockApp: () -> Unit,
    hideKeyboard: Boolean = true,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    DisposableEffect(hideKeyboard) {
        if (hideKeyboard) {
            keyboardController?.hide()
        }
        onDispose { }
    }
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        SwipeToCloseContainer(onClose = onBack) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        shape = RoundedCornerShape(0.dp),
                        color = TitleBarColor,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(HomeHeaderHeight)
                                .padding(horizontal = 0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Voltar",
                                    tint = Color.White
                                )
                            }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                            IconButton(onClick = onLockApp) {
                                Icon(
                                    imageVector = Icons.Filled.VpnKey,
                                    contentDescription = "Trancar app",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 14.dp,
                            end = 12.dp,
                            bottom = 110.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = content
                    )
                }
            }
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
    val title: String,
    val subtitle: String
)

@Composable
internal fun ThemeModeOptions(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    val options = remember {
        listOf(
            ThemeOption(ThemeMode.BLUE, "Azul", "Elegante, limpo e principal"),
            ThemeOption(ThemeMode.LIGHT, "Claro", "Leve, limpo e sempre legível"),
            ThemeOption(ThemeMode.DARK, "Escuro", "Contraste suave para uso noturno"),
            ThemeOption(ThemeMode.PINK, "Rosa", "Blush elegante com toque sofisticado"),
            ThemeOption(ThemeMode.SYSTEM, "Sistema", "Segue a configuração do aparelho")
        )
    }

    var expanded by rememberSaveable { mutableStateOf(false) }
    val selectedOption = remember(currentMode, options) {
        options.firstOrNull { it.mode == currentMode } ?: options.first()
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = RoundedCornerShape(18.dp),
            color = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.widthIn(max = 240.dp)) {
                        Text(
                            text = selectedOption.title,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = selectedOption.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
                ListSeparator()
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.98f)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(option.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = option.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onModeSelected(option.mode)
                    }
                )
            }
        }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.widthIn(max = 220.dp)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            color = if (important) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
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
                        style = MaterialTheme.typography.bodySmall,
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
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (password.isNotBlank()) {
            onConfirm(password)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        Text("Entrada privada: sem sugestões do teclado.")
                    },
                    shape = RoundedCornerShape(14.dp)
                )
                if (error.isNotBlank()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

internal fun openBatteryOptimizationSettings(context: Context) {
    val appContext = context.applicationContext
    if (isBatteryOptimizationIgnored(appContext)) {
        Toast.makeText(appContext, "O app ja esta fora da economia de bateria", Toast.LENGTH_SHORT).show()
        return
    }

    val directIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${appContext.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } else {
        null
    }
    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val targetIntent = directIntent?.takeIf { it.resolveActivity(appContext.packageManager) != null }
        ?: fallbackIntent
    runCatching {
        appContext.startActivity(targetIntent)
    }.onFailure {
        Toast.makeText(appContext, "Abra Bateria nas configuracoes do Android", Toast.LENGTH_SHORT).show()
    }
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

internal fun isBatteryOptimizationIgnored(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val appContext = context.applicationContext
    val powerManager = appContext.getSystemService(PowerManager::class.java) ?: return false
    return powerManager.isIgnoringBatteryOptimizations(appContext.packageName)
}
