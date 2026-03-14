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
            val channelArn = "arn:aws:chime:us-east-1:855076735732:app-instance/0b31fd29-7170-4dfc-9743-b96324770d72/channel/13ec7575-fc0c-423e-b97f-3e6a5c1c5f97"
            val userArn = "arn:aws:chime:us-east-1:855076735732:app-instance/0b31fd29-7170-4dfc-9743-b96324770d72/user/u-391cfdf5-937b-4478-b68a-1142bf950708"

            val credentials = remember {
                Credentials(
                    accessKeyId = "", secretAccessKey = ""
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