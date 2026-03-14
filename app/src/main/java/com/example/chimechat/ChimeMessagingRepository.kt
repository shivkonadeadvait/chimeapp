package com.example.chimechat

import aws.sdk.kotlin.services.chimesdkmessaging.ChimeSdkMessagingClient
import aws.sdk.kotlin.services.chimesdkmessaging.model.ChannelMessagePersistenceType
import aws.sdk.kotlin.services.chimesdkmessaging.model.ChannelMessageSummary
import aws.sdk.kotlin.services.chimesdkmessaging.model.ChannelMessageType
import aws.sdk.kotlin.services.chimesdkmessaging.model.GetMessagingSessionEndpointRequest
import aws.sdk.kotlin.services.chimesdkmessaging.model.ListChannelMessagesRequest
import aws.sdk.kotlin.services.chimesdkmessaging.model.SendChannelMessageRequest
import aws.sdk.kotlin.services.chimesdkmessaging.model.SortOrder
import aws.sdk.kotlin.services.chimesdkmessaging.model.ChimeSdkMessagingException
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import org.json.JSONObject

class ChimeMessagingRepository(
    private val credentials: Credentials
) {

    private lateinit var chimeClient: ChimeSdkMessagingClient

    private val _realtimeMessages = MutableSharedFlow<ChatMessage>(replay = 0, extraBufferCapacity = 64)
    /** Emits new messages received over the WebSocket (CREATE_CHANNEL_MESSAGE) for the subscribed channel. */
    val realtimeMessages: Flow<ChatMessage> = _realtimeMessages.asSharedFlow()

    init {
        initializeClient()
    }

    private fun initializeClient() {
        chimeClient = ChimeSdkMessagingClient {
            region = "us-east-1"
            this.credentialsProvider = StaticCredentialsProvider(credentials)
        }
    }

    suspend fun sendMessage(channelArn: String, userArn: String, text: String) {
        try {
            val request = SendChannelMessageRequest {
                this.channelArn = channelArn
                this.chimeBearer = userArn
                this.content = text
                this.type = ChannelMessageType.Standard
                this.persistence = ChannelMessagePersistenceType.Persistent
            }

            val response = chimeClient.sendChannelMessage(request)
            println("Message sent successfully with ID: ${response.messageId}")
        } catch (e: Exception) {
            println("Failed to send message: ${e.message}")
        }
    }

    /**
     * Parses Chime WebSocket message JSON and emits a [ChatMessage] to [realtimeMessages]
     * when event type is CREATE_CHANNEL_MESSAGE and the message is for [channelArn].
     */
    private fun handleRealtimeMessage(
        rawJson: String,
        channelArn: String,
        myUserArn: String
    ) {
        try {
            val root = JSONObject(rawJson)
            val headers = root.optJSONObject("Headers") ?: return
            val eventType = headers.optString("x-amz-chime-event-type", "")
            if (eventType != "CREATE_CHANNEL_MESSAGE") return

            val payloadStr = root.optString("Payload")
            if (payloadStr.isBlank()) return
            val payload = JSONObject(payloadStr)
            val messageChannelArn = payload.optString("ChannelArn", "")
            if (messageChannelArn != channelArn) return

            val content = payload.optString("Content", "")
            val messageId = payload.optString("MessageId", "")
            val sender = payload.optJSONObject("Sender")
            val senderName = sender?.optString("Name", "Unknown") ?: "Unknown"
            val senderArn = sender?.optString("Arn", "") ?: ""

            val chatMessage = ChatMessage(
                messageId = messageId,
                senderName = senderName,
                content = content,
                isMe = senderArn == myUserArn
            )
            _realtimeMessages.tryEmit(chatMessage)
        } catch (e: Exception) {
            println("Failed to parse realtime message: ${e.message}")
        }
    }

    /** Extracts host (no scheme, no path) from endpoint URL for Chime WebSocket. */
    private fun endpointUrlToHost(rawUrl: String): String {
        val withoutScheme = when {
            rawUrl.startsWith("wss://") -> rawUrl.removePrefix("wss://")
            rawUrl.startsWith("ws://") -> rawUrl.removePrefix("ws://")
            rawUrl.startsWith("https://") -> rawUrl.removePrefix("https://")
            rawUrl.startsWith("http://") -> rawUrl.removePrefix("http://")
            else -> rawUrl
        }
        return withoutScheme.split("/").first().trim()
    }

    suspend fun connectToRealTimeChat(
        userArn: String,
        channelArn: String
    ) {
        try {
            val endpointResponse = chimeClient.getMessagingSessionEndpoint(
                GetMessagingSessionEndpointRequest {}
            )
            val rawUrl = endpointResponse.endpoint?.url ?: run {
                println("Real-time: no endpoint URL in response")
                return
            }
            val host = endpointUrlToHost(rawUrl)
            val sessionId = java.util.UUID.randomUUID().toString()
            val signedUrl = ChimeWebSocketSigner.signConnectUrl(
                host = host,
                userArn = userArn,
                sessionId = sessionId,
                credentials = credentials,
                region = "us-east-1"
            )

            val client = OkHttpClient()
            val request = Request.Builder().url(signedUrl).build()

            client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    println("Successfully connected to AWS Chime WebSockets!")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleRealtimeMessage(text, channelArn, userArn)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    println("WebSocket Error: ${t.message}")
                }
            })
        } catch (e: ChimeSdkMessagingException) {
            println("Real-time chat unavailable: ${e.message}. Add IAM permission chime:GetMessagingSessionEndpoint to enable.")
        } catch (e: Exception) {
            println("Real-time chat error: ${e.message}")
        }
    }


    suspend fun getChannelMessages(
        channelArn: String,
        userArn: String,
        paginationToken: String? = null
    ): Pair<List<ChannelMessageSummary>, String?> {
        return try {
            val request = ListChannelMessagesRequest {
                this.channelArn = channelArn
                this.chimeBearer = userArn
                this.maxResults = 50
                this.sortOrder = SortOrder.Descending
                this.nextToken = paginationToken
            }

            val response = chimeClient.listChannelMessages(request)

            Pair(response.channelMessages ?: emptyList(), response.nextToken)
        } catch (e: Exception) {
            println("Failed to fetch messages: ${e.message}")
            Pair(emptyList(), null)
        }
    }
}