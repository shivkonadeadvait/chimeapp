package com.example.chimechat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import aws.sdk.kotlin.services.chimesdkmessaging.model.ChannelMessageSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChimeMessagingRepository,
    private val channelArn: String,
    private val myUserArn: String
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText = _inputText.asStateFlow()

    var currentNextToken: String? = null
    private var isLoading = false

    init {
        loadMessages()
        startRealTime()
    }

    fun onInputTextChanged(newText: String) {
        _inputText.value = newText
    }

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            try {
                repository.sendMessage(
                    channelArn = channelArn,
                    userArn = myUserArn,
                    text = text
                )
                _inputText.value = ""
            } catch (e: Exception) {
            }
        }
    }

    private fun startRealTime() {
        viewModelScope.launch {
            repository.connectToRealTimeChat(
                userArn = myUserArn,
                channelArn = channelArn
            )
        }
        repository.realtimeMessages
            .onEach { newMessage ->
                if (_messages.value.none { it.messageId == newMessage.messageId }) {
                    _messages.value = listOf(newMessage) + _messages.value
                }
            }
            .launchIn(viewModelScope)
    }

    fun loadMessages() {
        if (isLoading) return
        isLoading = true

        viewModelScope.launch {
            val (awsMessages, nextToken) = repository.getChannelMessages(
                channelArn = channelArn,
                userArn = myUserArn,
                paginationToken = currentNextToken
            )

            val newUiMessages = awsMessages.map { mapAwsMessageToUI(it, myUserArn) }

            _messages.value = _messages.value + newUiMessages

            currentNextToken = nextToken
            isLoading = false
        }
    }

    fun mapAwsMessageToUI(
        awsMessage: ChannelMessageSummary,
        myUserArn: String
    ): ChatMessage {
        return ChatMessage(
            messageId = awsMessage.messageId ?: "",
            senderName = awsMessage.sender?.name ?: "Unknown",
            content = awsMessage.content ?: "",
            isMe = awsMessage.sender?.arn == myUserArn
        )
    }
}