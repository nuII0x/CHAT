package com.null0x.primalis
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember

import com.null0x.primalis.network.TcpClient
import com.null0x.primalis.viewmodel.ChatViewModel
import com.null0x.primalis.ui.chat.ChatScreen

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MainActivity : ComponentActivity() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val client = TcpClient(
            host = "SEU_IP_AQUI",
            port = 12345,
            scope = appScope
        )

        setContent {
            val vm = remember { ChatViewModel(client) }
            ChatScreen(vm)
        }
    }
}