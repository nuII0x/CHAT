package com.null0x.chat
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.null0x.chat.notification.MessageNotifier
import com.null0x.chat.ui.chat.ChatScreen
import com.null0x.chat.ui.home.HomeScreen
import com.null0x.chat.ui.theme.ChatTheme
import com.null0x.chat.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    private var openChatUsername: String? = null

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
        openChatUsername = intent.getStringExtra(MessageNotifier.EXTRA_OPEN_CHAT_USERNAME)
        requestNotificationPermission()
        startChatService()

        val vmFactory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return ChatViewModel(application) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }

        setContent {
            ChatTheme {
                val vm: ChatViewModel = viewModel(factory = vmFactory)
                openChatUsername?.let { username ->
                    if (username.isNotBlank()) {
                        vm.selectTarget(username)
                    }
                    openChatUsername = null
                }
                if (vm.inChat) {
                    ChatScreen(vm, onBack = { vm.openHome() })
                } else {
                    HomeScreen(vm) { peer -> vm.selectTarget(peer) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val username = intent.getStringExtra(MessageNotifier.EXTRA_OPEN_CHAT_USERNAME)
        if (!username.isNullOrBlank()) {
            openChatUsername = username
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            return
        }

        ActivityCompat.requestPermissions(this, arrayOf(permission), 1001)
    }

    private fun startChatService() {
        ChatBackgroundService.startHidden(this)
    }
}
