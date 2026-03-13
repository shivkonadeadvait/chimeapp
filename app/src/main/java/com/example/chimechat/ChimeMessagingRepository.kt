package com.example.chimechat
import aws.sdk.kotlin.services.chimesdkmessaging.ChimeSdkMessagingClient
import aws.sdk.kotlin.services.chimesdkmessaging.model.ChannelMessagePersistenceType
import aws.sdk.kotlin.services.chimesdkmessaging.model.ChannelMessageSummary
import aws.sdk.kotlin.services.chimesdkmessaging.model.ChannelMessageType
import aws.sdk.kotlin.services.chimesdkmessaging.model.GetMessagingSessionEndpointRequest
import aws.sdk.kotlin.services.chimesdkmessaging.model.ListChannelMessagesRequest
import aws.sdk.kotlin.services.chimesdkmessaging.model.SendChannelMessageRequest
import aws.sdk.kotlin.services.chimesdkmessaging.model.SortOrder
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.net.url.Url
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import okhttp3.*
import kotlin.time.Duration.Companion.minutes

class ChimeMessagingRepository(
    private val credentials: Credentials
) {

    private lateinit var chimeClient: ChimeSdkMessagingClient

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

    private fun generateSignedWebSocketUrl(
        baseUrl: String,
        userArn: String,
        sessionId: String
    ): String {
        val separator = if ("?" in baseUrl) "&" else "?"
        val signedBase = "$baseUrl${separator}userArn=$userArn&sessionId=$sessionId"
        return signedBase
    }

    suspend fun connectToRealTimeChat(userArn: String) {
        val endpointResponse = chimeClient.getMessagingSessionEndpoint(
            GetMessagingSessionEndpointRequest {}
        )
        val baseWebSocketUrl = endpointResponse.endpoint?.url ?: return

        val sessionId = java.util.UUID.randomUUID().toString()

        val signedUrl = generateSignedWebSocketUrl(
            baseUrl = baseWebSocketUrl,
            userArn = userArn,
            sessionId = sessionId
        )

        val client = OkHttpClient()
        val request = Request.Builder().url(signedUrl).build()

        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                println("Successfully connected to AWS Chime WebSockets!")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                println("New Message Received: $text")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                println("WebSocket Error: ${t.message}")
            }
        })
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