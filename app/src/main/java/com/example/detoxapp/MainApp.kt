package com.example.detoxapp

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.detoxapp.ui.theme.CreateAccount
import com.example.detoxapp.ui.theme.FirstScreen
import com.example.detoxapp.ui.theme.LoginScreen
import com.example.detoxapp.ui.theme.PasswordRecovery
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.runtime.State
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore


class GroupViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val _groupId = mutableStateOf<String?>(null)
    val groupId: State<String?> get() = _groupId


    fun setGroupId(groupId: String?) {
        _groupId.value = groupId
    }
}

@Composable
fun MainApp(auth: FirebaseAuth){
    val navController = rememberNavController()
    val groupViewModel = remember { GroupViewModel() }

    Scaffold(
        topBar = {
            val currentScreen = navController.currentBackStackEntryAsState().value?.destination?.route
            if (currentScreen in listOf(
                Screen.Group.route
            )){
                TopBarGroup(navController, groupViewModel)
            }
        },
        bottomBar = {
            val currentScreen = navController.currentBackStackEntryAsState().value?.destination?.route
            if (currentScreen in listOf(
                Screen.Group.route,
                Screen.Stats.route,
                Screen.Messages.route,
                Screen.Objectives.route,
                Screen.Ranking.route,
                Screen.Previa.route,
                Screen.PhaseIntroScreen.route,
                Screen.PhaseEndScreen.route
            )){
                BottomBar(navController, groupViewModel, auth)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Start.route,
            modifier = Modifier.padding(innerPadding)
        ){
            composable(Screen.Start.route){
                FirstScreen(
                    onLoginClick = {navController.navigate(Screen.Login.route)},
                    onCreateAccountClick = {navController.navigate(Screen.CreateAccount.route)}
                )
            }
            composable(Screen.Login.route){
                LoginScreen(
                    auth,
                    navController,
                    onCreateAccountClick = {navController.navigate(Screen.CreateAccount.route)},
                    onForgotPasswordClick = {navController.navigate(Screen.Password.route)}
                )
            }
            composable(Screen.CreateAccount.route){
                CreateAccount(
                    onLoginClick = {navController.navigate(Screen.Login.route)},
                    navController,
                    auth
                )
            }
            composable(Screen.Password.route){
                PasswordRecovery(auth, navController)
            }
            composable(Screen.Home.route){
                HomeScreen(navController, auth, groupViewModel)
            }
            composable(Screen.NameGroup.route){
                CreateGroupScreen(navController, auth)
            }
            composable(
                route = "your_name/{groupName}",
                arguments = listOf(navArgument("groupName") { type = NavType.StringType })
            ){ backStackEntry ->
                val groupName = backStackEntry.arguments?.getString("groupName")
                if (groupName != null) {
                    YourName(navController, groupName, auth, groupViewModel)
                }
            }
            composable(
                route = "your_name_join/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.StringType})
            ){ backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId")
                if (groupId != null){
                    YourNameJoin(navController, groupId, auth, groupViewModel)
                }

            }
            composable(
                route = "group/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) { backStackEntry ->

                val groupId = backStackEntry.arguments?.getString("groupId")
                if (groupId != null) {
                    GroupMainScreen(navController, groupId, groupViewModel)
                }
            }
            composable(Screen.Stats.route){
                Statistics(navController, auth, groupViewModel)
            }
            composable(Screen.Ranking.route){
                Ranking(navController, auth, groupViewModel)

            }
            composable(Screen.Objectives.route){
                Objectives(navController, groupViewModel, auth)
            }
            composable(Screen.PhaseIntroScreen.route) {
                PhaseIntroScreen(navController, groupViewModel, auth)
            }
            composable(Screen.PhaseEndScreen.route){
                PhaseEndScreen(navController, groupViewModel, auth)
            }
            composable(Screen.Previa.route) {
                Previa(navController, groupViewModel, auth)
            }
            composable(Screen.Messages.route){
                Messages(navController, groupViewModel)
            }
        }
    }
}