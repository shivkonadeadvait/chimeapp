package com.example.chimechat

data class ChatMessage(
    val messageId: String,
    val senderName: String,
    val content: String,
    val isMe: Boolean
)