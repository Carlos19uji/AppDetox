package com.example.detoxapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.example.detoxapp.ui.theme.DetoxAppTheme
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.android.gms.ads.MobileAds
import com.google.firebase.auth.FirebaseAuth
import io.branch.referral.Branch
import io.branch.referral.validators.IntegrationValidator
import kotlinx.coroutines.launch
import org.json.JSONObject


class MainActivity : ComponentActivity() {

    private lateinit var navController: NavController
    private lateinit var auth: FirebaseAuth
    private val pendingGroupId = mutableStateOf<String?>(null)  // Estado observable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ Inicializa Branch SDK en onCreate (pero NO uses .init aquí)
        Branch.enableLogging()
        Branch.getAutoInstance(this)
        // ✅ Inicializa Firebase
        auth = FirebaseAuth.getInstance()

        // ✅ Inicializa AdMob
        MobileAds.initialize(this)

        // ✅ Configuración visual
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            DetoxAppTheme {
                ProvideWindowInsets {
                    navController = rememberNavController()
                    MainApp(auth, pendingGroupId = pendingGroupId.value)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // Validar integración (opcional pero recomendado)
        IntegrationValidator.validate(this)

        Branch.sessionBuilder(this)
            .withCallback { branchUniversalObject, linkProperties, error ->
                if (error != null) {
                    Log.e("BranchSDK", "branch init failed: ${error.message}")
                } else {
                    Log.i("BranchSDK", "branch init complete!")

                    // Manejo de pendingGroupId a partir de BranchUniversalObject metadata
                    branchUniversalObject?.contentMetadata?.let { metadata ->
                        val groupId = metadata.customMetadata["group_id"]
                        if (!groupId.isNullOrEmpty()) {
                            pendingGroupId.value = groupId
                            Log.i("BranchSDK", "pendingGroupId set from metadata: $groupId")
                        }
                    }

                    // Alternativamente también puedes revisar params directamente
                    // params = BranchUniversalObject + LinkProperties combinados

                    // Logs informativos
                    branchUniversalObject?.let {
                        Log.i("BranchSDK", "title: ${it.title}")
                        Log.i("BranchSDK", "CanonicalIdentifier: ${it.canonicalIdentifier}")
                        Log.i("BranchSDK", "metadata: ${it.contentMetadata.convertToJson()}")
                    }
                    linkProperties?.let {
                        Log.i("BranchSDK", "Channel: ${it.channel}")
                        Log.i("BranchSDK", "Control params: ${it.controlParams}")
                    }
                }
            }
            .withData(intent?.data)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.putExtra("branch_force_new_session", true)  // Añade el flag que necesita Branch
        setIntent(intent)

        Branch.sessionBuilder(this)
            .withCallback { params, error ->
                if (error != null) {
                    Log.e("BranchSDK", "Branch reInit error: ${error.message}")
                } else if (params != null) {
                    Log.i("BranchSDK", "Branch reInit success: $params")

                    val groupId = params.optString("group_id")
                    if (!groupId.isNullOrEmpty()) {
                        pendingGroupId.value = groupId
                        Log.i("BranchSDK", "pendingGroupId set from reInit params: $groupId")
                    }
                }
            }
            .reInit()
    }
}


sealed class Screen(val route: String){
    object Splash: Screen("splash")
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
    object EditProfile: Screen("edit_profile")
}