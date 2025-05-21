package com.example.detoxapp

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch


class GroupViewModel : ViewModel() {
    private val _groupId = mutableStateOf<String?>(null)
    val groupId: State<String?> get() = _groupId


    fun setGroupId(groupId: String?) {
        _groupId.value = groupId
    }
}

class LinkViewModel : ViewModel() {
    private val _inviteLink = mutableStateOf("")
    val inviteLink: State<String> get() = _inviteLink

    fun loadInviteLink(context: Context, currentGroupId: String) {
        viewModelScope.launch {
            try {
                val link = generateDynamicInviteLink(context, currentGroupId)
                _inviteLink.value = link
                Log.d("LinkViewModel", "Invite link generado: $link")
            } catch (e: Exception) {
                _inviteLink.value = ""
                Log.e("LinkViewModel", "Error generando link: ${e.message}", e)
            }
        }
    }
}

@Composable
fun MainApp(
    auth: FirebaseAuth,
    pendingGroupId: String? = null,
    onGoogleSignIn: () -> Unit) {

    val navController = rememberNavController()
    val groupViewModel = remember { GroupViewModel() }
    val adViewModel: AdViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory(LocalContext.current.applicationContext as Application)
    )

    val firebaseUserState = remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }

    // 2️⃣ Registramos un AuthStateListener para actualizar ese estado
    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            firebaseUserState.value = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    // 3️⃣ Ahora sí, navegamos en cuanto firebaseUserState cambie a no-null
    LaunchedEffect(firebaseUserState.value) {
        if (firebaseUserState.value != null) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        }
    }

    //Navegar a unirse al grupo si hay pendingGroupId y el usuario esta logueado

    LaunchedEffect(pendingGroupId, auth.currentUser) {
        if (!pendingGroupId.isNullOrEmpty() && auth.currentUser != null) {
            groupViewModel.setGroupId(pendingGroupId)
            navController.navigate("your_name_join/$pendingGroupId") {
                popUpTo(Screen.Splash.route) { inclusive = true }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            val currentScreen = navController.currentBackStackEntryAsState().value?.destination?.route
            when (currentScreen) {
                Screen.Group.route -> {
                    TopBarGroup(
                        navController = navController,
                        groupViewModel = groupViewModel,
                        auth = auth,
                        modifier = Modifier.statusBarsPadding()
                    )
                }
                in listOf(
                    Screen.Home.route,
                    Screen.Stats.route,
                    Screen.Messages.route,
                    Screen.Objectives.route,
                    Screen.Ranking.route,
                    Screen.Previa.route,
                    Screen.PhaseIntroScreen.route,
                    Screen.PhaseEndScreen.route,
                    Screen.EditProfile.route
                ) -> {
                    HomeTopBar(
                        navController = navController,
                        auth = auth,
                        modifier = Modifier.statusBarsPadding()
                    )
                }
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
                    Screen.PhaseEndScreen.route,
                    Screen.EditProfile.route
                )
            ) {
                BottomBar(
                    navController = navController,
                    groupViewModel = groupViewModel,
                    auth = auth,
                    modifier = Modifier.navigationBarsPadding()
                )
            }
        }
    )  { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(innerPadding)
        ){
            composable(Screen.Splash.route){
                SplashScreen(navController, auth)
            }
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
                    onForgotPasswordClick = {navController.navigate(Screen.Password.route)},
                    onGoogleSignIn
                )
            }
            composable(Screen.CreateAccount.route){
                CreateAccount(
                    onLoginClick = {navController.navigate(Screen.Login.route)},
                    navController,
                    auth,
                    onGoogleSignIn
                )
            }
            composable(Screen.Password.route){
                PasswordRecovery(auth, navController)
            }
            composable(Screen.Home.route){
                HomeScreen(navController, auth, groupViewModel, adViewModel)
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
                    YourName(navController, groupName, auth, groupViewModel, adViewModel)
                }
            }
            composable(
                route = "your_name_join/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.StringType})
            ){ backStackEntry ->
                val passedGroupId = backStackEntry.arguments?.getString("groupId")
                val groupId = passedGroupId ?: groupViewModel.groupId.value

                if (groupId != null){
                    YourNameJoin(navController, groupId, auth, groupViewModel, adViewModel)
                }

            }
            composable(
                route = "group/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) { backStackEntry ->

                val groupId = backStackEntry.arguments?.getString("groupId")
                if (groupId != null) {
                    GroupMainScreen(navController, groupViewModel, auth)
                }
            }
            composable(Screen.Stats.route){
                Statistics(navController, auth, groupViewModel)
            }
            composable(Screen.Ranking.route){
                Ranking(auth, groupViewModel)

            }
            composable(Screen.Objectives.route){
                Objectives(navController, groupViewModel, auth, adViewModel)
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
                Messages(navController, groupViewModel, auth)
            }
            composable(Screen.EditProfile.route) {
                EditProfile(navController, groupViewModel, auth, adViewModel)
            }
        }
    }
}