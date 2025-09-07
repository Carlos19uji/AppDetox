package com.carlosrmuji.detoxapp

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.runtime.State
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carlosrmuji.detoxapp.AI.AIChat
import com.carlosrmuji.detoxapp.Billing.AdViewModel
import com.carlosrmuji.detoxapp.Billing.BillingManager
import com.carlosrmuji.detoxapp.Billing.BillingScreen
import com.carlosrmuji.detoxapp.Groups.CreateGroupScreen
import com.carlosrmuji.detoxapp.Groups.HomeScreen
import com.carlosrmuji.detoxapp.Groups.Messages
import com.carlosrmuji.detoxapp.Groups.Objectives
import com.carlosrmuji.detoxapp.Groups.PhaseEndScreen
import com.carlosrmuji.detoxapp.Groups.PhaseIntroScreen
import com.carlosrmuji.detoxapp.Groups.Previa
import com.carlosrmuji.detoxapp.Groups.Ranking
import com.carlosrmuji.detoxapp.Groups.YourName
import com.carlosrmuji.detoxapp.Groups.YourNameJoin
import com.carlosrmuji.detoxapp.Groups.generateDynamicInviteLink
import com.carlosrmuji.detoxapp.Restrictions.AppBloqScreen
import com.carlosrmuji.detoxapp.Restrictions.DayPassBillingManager
import com.carlosrmuji.detoxapp.Restrictions.DayPassBillingScreen
import com.carlosrmuji.detoxapp.Restrictions.PhoneBloqScreen
import com.example.detoxapp.BottomBarGroups
import com.example.detoxapp.BottomBarIndividual
import com.example.detoxapp.GroupTopBar
import com.example.detoxapp.HomeTopBar
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

class BillingViewModel(application: Application) : AndroidViewModel(application) {

    private val billingManager = BillingManager(application.applicationContext)

    private val _userPlan = mutableStateOf("base_plan")
    val userPlan: State<String> = _userPlan

    private val _purchaseStatus = mutableStateOf<String?>(null)
    val purchaseStatus: State<String?> = _purchaseStatus

    init {
        billingManager.onPlanUpdated = { plan, isNewPruchase ->
            _userPlan.value = plan
            if (isNewPruchase) {
                _purchaseStatus.value = "Compra exitosa: $plan"
            }
        }

        billingManager.onPurchaseError = { error ->
            _purchaseStatus.value = error
        }

        billingManager.startBillingClient()

        fetchPlanFromFirestore()
    }

    fun clearPurchaseStatus() {
        _purchaseStatus.value = null
    }

    fun refreshUserPlanFromBilling() {
        billingManager.startBillingClient()
    }

    fun launchPurchase(activity: Activity, basePlanId: String) {
        val googlePlayPlanId = basePlanId.replace("_", "-") // Convertimos para Google Play
        val productDetails = billingManager.getProductDetails(googlePlayPlanId)

        if (productDetails != null) {
            billingManager.launchBillingFlow(activity, productDetails, googlePlayPlanId)
        } else {
            _purchaseStatus.value = "Plan no encontrado para: $basePlanId"
        }
    }

    private fun fetchPlanFromFirestore() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("plan")
            .document("plan")
            .addSnapshotListener { document, error ->
                if (error != null) {
                    Log.e("BillingViewModel", "Error al escuchar plan: ${error.message}")
                    return@addSnapshotListener
                }

                if (document != null && document.exists()) {
                    val firestorePlan = document.getString("plan")
                    if (!firestorePlan.isNullOrBlank()) {
                        _userPlan.value = firestorePlan.lowercase()
                    } else {
                        Log.d("BillingViewModel", "Firestore plan vacío, no se actualiza.")
                    }
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
    val billingViewModel: BillingViewModel = viewModel()
    val adViewModel: AdViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory(LocalContext.current.applicationContext as Application)
    )

    val context = LocalContext.current
    val dayPassBillingManager = remember { DayPassBillingManager(context) }


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

    LaunchedEffect(billingViewModel.userPlan.value) {
        val currentPlan = billingViewModel.userPlan.value
        if (currentPlan == "base_plan") {
            adViewModel.loadAllAds()
        } else {
            adViewModel.clearAllAds()
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
            if (currentScreen in listOf(
                    Screen.Home.route,
                    Screen.Stats.route,
                    Screen.EditProfile.route,
                    Screen.AIChat.route,
                    Screen.AppBloq.route,
                    Screen.PhoneBloq.route
            )) {
                HomeTopBar(
                    navController = navController,
                    auth = auth,
                    modifier = Modifier.statusBarsPadding()
                )
            }

            if ( currentScreen in listOf(
                    Screen.Messages.route,
                    Screen.Objectives.route,
                    Screen.Ranking.route,
                    Screen.Previa.route,
                    Screen.PhaseIntroScreen.route,
                    Screen.PhaseEndScreen.route,
            )){
                GroupTopBar(navController, auth, modifier = Modifier.statusBarsPadding())
            }
        },
        bottomBar = {
            val currentScreen = navController.currentBackStackEntryAsState().value?.destination?.route
            if (currentScreen in listOf(
                    Screen.Messages.route,
                    Screen.Objectives.route,
                    Screen.Ranking.route,
                    Screen.Previa.route,
                    Screen.PhaseIntroScreen.route,
                    Screen.PhaseEndScreen.route,
                )
            ) {
                BottomBarGroups(
                    navController = navController,
                    groupViewModel = groupViewModel,
                    auth = auth,
                    modifier = Modifier.navigationBarsPadding()
                )
            }

            if (currentScreen in listOf(
                    Screen.AIChat.route,
                    Screen.Stats.route,
                    Screen.EditProfile.route,
                    Screen.AppBloq.route,
                    Screen.Home.route,
                    Screen.PhoneBloq.route
                )
            ) {
                BottomBarIndividual(
                    navController = navController,
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
            composable(Screen.AIChat.route) {
                AIChat(navController, adViewModel, auth)
            }
            composable(Screen.Stats.route){
                Statistics(auth)
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
                Previa(navController, groupViewModel)
            }
            composable(Screen.Messages.route){
                Messages(groupViewModel, auth, adViewModel)
            }
            composable(Screen.EditProfile.route) {
                EditProfile(navController, groupViewModel, auth, adViewModel)
            }
            composable(Screen.AppBloq.route) {
                AppBloqScreen(navController, adViewModel)
            }
            composable(Screen.PhoneBloq.route) {
                PhoneBloqScreen(navController, adViewModel)
            }
            composable(Screen.PlansScreen.route) {
                BillingScreen(
                    billingViewModel = billingViewModel,
                    onClose = { navController.popBackStack() }
                )
            }
            composable(
                route = "daypass/{packageName}",
                arguments = listOf(navArgument("packageName") { type = NavType.StringType })
            ) { backStackEntry ->
                val passedPackageName = backStackEntry.arguments?.getString("packageName") ?: ""

                if (passedPackageName.isNotEmpty()) {
                    DayPassBillingScreen(
                        packageName = passedPackageName,
                        dayPassBillingManager = dayPassBillingManager,
                        onCancel = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}