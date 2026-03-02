package com.example.llamaapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.llamaapp.ui.chat.ChatViewModel
import com.example.llamaapp.ui.screens.ChatScreen
import com.example.llamaapp.ui.screens.ConversationListScreen
import com.example.llamaapp.ui.screens.ModelPickerScreen
import com.example.llamaapp.ui.screens.SettingsScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val chatViewModel: ChatViewModel = hiltViewModel()

    NavHost(
        navController = navController,
        startDestination = "conversations"
    ) {
        composable("conversations") {
            ConversationListScreen(
                viewModel = chatViewModel,
                onNavigateToChat = { conversationId ->
                    navController.navigate("chat/$conversationId")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable(
            route = "chat/{conversationId}",
            arguments = listOf(
                navArgument("conversationId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getLong("conversationId") ?: -1L

            LaunchedEffect(conversationId) {
                if (conversationId != -1L) {
                    chatViewModel.selectConversation(conversationId)
                }
            }

            ChatScreen(
                viewModel = chatViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToModelPicker = { navController.navigate("model_picker") }
            )
        }

        composable("model_picker") {
            ModelPickerScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                viewModel = hiltViewModel(),
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
