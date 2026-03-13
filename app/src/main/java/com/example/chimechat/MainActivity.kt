package com.example.chimechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import com.example.chimechat.ui.GroupChatScreen
import com.example.chimechat.ui.theme.ChimeChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val channelArn = "REPLACE_WITH_CHANNEL_ARN"
            val userArn = "REPLACE_WITH_USER_ARN"

            val credentials = remember {
                Credentials(
                    "REPLACE_ACCESS_KEY_ID",
                    "REPLACE_SECRET_ACCESS_KEY",
                    "REPLACE_SESSION_TOKEN"
                )
            }

            val repository = remember { ChimeMessagingRepository(credentials) }
            val viewModel = remember {
                ChatViewModel(
                    repository = repository,
                    channelArn = channelArn,
                    myUserArn = userArn
                )
            }

            ChimeChatTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GroupChatScreen(
                        viewModel,
                        Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GroupChatPreview() {
    ChimeChatTheme {
        val previewCredentials = Credentials(
            "PREVIEW_ACCESS_KEY_ID",
            "PREVIEW_SECRET_ACCESS_KEY",
            "PREVIEW_SESSION_TOKEN"
        )
        val dummyRepository = ChimeMessagingRepository(previewCredentials)
        val dummyViewModel = ChatViewModel(
            repository = dummyRepository,
            channelArn = "dummy-channel",
            myUserArn = "dummy-user"
        )
        GroupChatScreen(viewModel = dummyViewModel)
    }
}