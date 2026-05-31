package com.null0x.chat.model

import androidx.compose.runtime.Immutable

@Immutable
data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isMine: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val delivery: DeliveryState = if (isMine) DeliveryState.Sent else DeliveryState.Delivered
)

enum class DeliveryState {
    Pending,
    Sent,
    Delivered,
    Failed
}
