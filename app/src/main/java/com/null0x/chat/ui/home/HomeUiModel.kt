package com.null0x.chat.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal enum class HomeTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    Chats("Chats", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    Contacts("Contatos", Icons.Filled.Contacts, Icons.Outlined.Contacts),
    Profile("Perfil", Icons.Filled.Person, Icons.Outlined.Person),
    Settings("Ajustes", Icons.Filled.Settings, Icons.Outlined.Settings)
}

internal val HomeHeaderHeight = 86.dp
internal val TitleBarColor = Color.Black
internal val ShareCardBackground = Color(0xFF87CEEB)
