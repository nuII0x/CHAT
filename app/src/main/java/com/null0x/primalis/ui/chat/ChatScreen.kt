package com.null0x.primalis.ui.chat
import com.null0x.primalis.viewmodel.ChatViewModel
import com.null0x.primalis.model.Message
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChatScreen(vm: ChatViewModel) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {

        // Lista de mensagens
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(8.dp)
        ) {
            items(messages.reversed()) { msg ->
                MessageBubble(msg)
            }
        }

        // Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Mensagem...") }
            )

            Spacer(Modifier.width(8.dp))

            Button(onClick = {
                vm.send(input)
                input = ""
            }) {
                Text("Enviar")
            }
        }
    }
}

@Composable
fun MessageBubble(msg: Message) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (msg.isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (msg.isMine)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = msg.text,
                modifier = Modifier.padding(10.dp),
                color = if (msg.isMine)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }
    }
}