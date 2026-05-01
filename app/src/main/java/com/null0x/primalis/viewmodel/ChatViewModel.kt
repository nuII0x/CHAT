package com.null0x.primalis.viewmodel
import com.null0x.primalis.network.TcpClient
import com.null0x.primalis.model.Message 
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    private val client: TcpClient
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    init {
        client.connect()

        viewModelScope.launch {
            client.incoming.collect { text ->
                _messages.update { it + Message(text = text, isMine = false) }
            }
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return

        // eco local imediato
        _messages.update { it + Message(text = text, isMine = true) }

        viewModelScope.launch {
            client.send(text)
        }
    }

    override fun onCleared() {
        client.close()
        super.onCleared()
    }
}