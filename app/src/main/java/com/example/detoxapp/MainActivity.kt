package com.example.detoxapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.detoxapp.ui.theme.DetoxAppTheme
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    private lateinit var navController: NavController
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        enableEdgeToEdge()
        setContent {
            DetoxAppTheme {
                navController = rememberNavController()
                MainApp(auth)
            }
        }
    }
}

sealed class Screen(val route: String){
    object Start : Screen("start")
    object Login: Screen("login_screen")
    object CreateAccount: Screen("create_account")
    object Password: Screen("password_recovery")
    object Home: Screen("home_screen")
    object NameGroup: Screen("name_group")
    object YourName: Screen("your_name/{groupName}"){
        fun createRoue(groupName: String): String{
            return "your_name/$groupName"
        }
    }
    object YourNameJoin: Screen("your_name_join/{groupId}"){
        fun createRoute(groupId: String): String{
            return "your_name_join/$groupId"
        }
    }
    object Group: Screen("group/{groupId}"){
        fun createRoute(groupId: String): String{
            return "group/$groupId"
        }
    }
    object Stats: Screen("stats")
    object Ranking: Screen("ranking")
    object PhaseIntroScreen: Screen("phase_intro")
    object PhaseEndScreen: Screen("phase_end")
    object Objectives: Screen("objectives")
    object Previa: Screen("previa")
    object Messages: Screen("messages")
}