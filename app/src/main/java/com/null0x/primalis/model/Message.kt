package com.null0x.primalis.model
data class Message(
    val text: String,
    val isMine: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)