package com.carlosrmuji.detoxapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.detoxapp.ui.theme.DetoxAppTheme
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import io.branch.referral.Branch
import io.branch.referral.validators.IntegrationValidator
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

private const val TAG = "DetoxApp"

class MainActivity : ComponentActivity() {

    private lateinit var navController: NavController
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001
    private val pendingGroupId = mutableStateOf<String?>(null)  // Estado observable
    private lateinit var billingViewModel: BillingViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity.onCreate START")
        billingViewModel = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))
            .get(BillingViewModel::class.java)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val acceptedTerms = prefs.getBoolean("accepted_terms", false)
        Log.d(TAG, "MainActivity: accepted_terms = $acceptedTerms")

        if (!acceptedTerms) {
            Log.d(TAG, "MainActivity: Terms not accepted, launching TermsActivity")
            startActivity(Intent(this, TermsActivity::class.java))
            finish()
            return
        }

        // ✅ Inicializa Branch SDK en onCreate (pero NO uses .init aquí)
        Branch.enableLogging()
        Branch.getAutoInstance(this)

        // ✅ Inicializa Firebase
        auth = FirebaseAuth.getInstance()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)


        // ✅ Inicializa AdMob
        MobileAds.initialize(this)

        // ✅ Configuración visual
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setupUsageStatsWorker()




        setContent {
            DetoxAppTheme {
                ProvideWindowInsets {
                    navController = rememberNavController()
                    MainApp(auth, pendingGroupId = pendingGroupId.value, onGoogleSignIn = {signInWithGoogle()})
                }
            }
        }
    }

    private fun setupUsageStatsWorker() {
        val calendar = Calendar.getInstance() // ✅ Hora local del dispositivo

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 1)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val now = Calendar.getInstance()
        var initialDelay = calendar.timeInMillis - now.timeInMillis

        if (initialDelay <= 0) {
            initialDelay += TimeUnit.DAYS.toMillis(1)
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<UsageStatsWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "DailyUsageStatsWorker",
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w("GoogleSignIn", "Google sign in failed", e)
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val userId = user?.uid

                    if (userId != null) {
                        val db = FirebaseFirestore.getInstance()

                        // Verifica si el documento 'tokens' dentro de la subcolección IA existe
                        val tokensDoc = db.collection("users").document(userId)
                            .collection("IA").document("tokens")

                        tokensDoc.get().addOnSuccessListener { snapshot ->
                            if (!snapshot.exists()) {
                                // Si no existe, lo creamos con valores por defecto
                                val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                                val now = Calendar.getInstance()
                                val today = dateFormat.format(now.time)

                                now.add(Calendar.DAY_OF_YEAR, 7)
                                val renovationDate = dateFormat.format(now.time)

                                val iaTokensData = mapOf(
                                    "tokens" to 5,
                                    "tokens_start" to today,
                                    "token_renovation" to renovationDate
                                )

                                tokensDoc.set(iaTokensData)
                                    .addOnSuccessListener {
                                        Log.d("Firestore", "IA tokens initialized for Google Sign-In")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("Firestore", "Error creating IA tokens: ${e.message}")
                                    }
                            } else {
                                Log.d("Firestore", "IA tokens already exist, no need to recreate")
                            }

                            val planDoc = db.collection("users").document(userId)
                                .collection("plan").document("plan")

                            planDoc.get().addOnSuccessListener { snapshot ->
                                if (!snapshot.exists()) {
                                    val plan = mapOf("plan" to "base_plan")

                                    planDoc.set(plan)
                                        .addOnSuccessListener {
                                            Log.d(
                                                "Firestore",
                                                "Plan initialized for Google Sign-In"
                                            )
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("Firestore", "Error creating plan: ${e.message}")
                                        }
                                } else {
                                    Log.d(
                                        "Firestore",
                                        "IA tokens already exist, no need to recreate"
                                    )
                                }
                            }
                        }
                    }

                    Log.d("FirebaseAuth", "signInWithCredential:success - ${user?.email}")
                } else {
                    Log.w("FirebaseAuth", "signInWithCredential:failure", task.exception)
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
    object Stats: Screen("stats")
    object Ranking: Screen("ranking")
    object PhaseIntroScreen: Screen("phase_intro")
    object PhaseEndScreen: Screen("phase_end")
    object Objectives: Screen("objectives")
    object Previa: Screen("previa")
    object Messages: Screen("messages")
    object EditProfile: Screen("edit_profile")
    object AIChat: Screen("ai_chat")
    object AppBloq: Screen("app_bloq")
    object PlansScreen: Screen("plans_screen")
}

@Composable
fun GoogleSignInButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black
        ),
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .padding(vertical = 8.dp)
            .height(42.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),          // usa todo el espacio del botón
            contentAlignment = Alignment.Center
        ) {
            // ① Texto siempre en una sola línea, centrado
            Text(
                text = text,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )

            // ② Icono a la izquierda dentro del mismo Box
            Image(
                painter = painterResource(id = R.drawable.logogoogle),
                contentDescription = "Google Logo",
                modifier = Modifier
                    .size(30.dp)
                    .align(Alignment.CenterStart)  // alineado al inicio del Box
                    .padding(start = 4.dp)
            )
        }
    }
}