package com.carlosrmuji.detoxapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.pow

@Composable
fun PhaseIntroScreen(
    navController: NavController,
    groupViewModel: GroupViewModel,
    auth: FirebaseAuth
) {
    val db = FirebaseFirestore.getInstance()
    val userId = auth.currentUser?.uid ?: return
    val groupId = groupViewModel.groupId.value ?: return
    val coroutineScope = rememberCoroutineScope()

    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    val startDate = today.format(formatter)
    val endDate = today.plusDays(7).format(formatter)

    val (phaseInfo, setPhaseInfo) = remember { mutableStateOf<PhaseInfo?>(null) }
    val (userName, setUserName) = remember { mutableStateOf("Usuario") }
    val (currentPhase, setCurrentPhase) = remember { mutableStateOf(1) }
    val (mediaFaseAnterior, setMediaFaseAnterior) = remember { mutableStateOf<Double?>(null) }
    val (targetUsage, setTargetUsage) = remember { mutableStateOf<Double?>(null) }
    val (mediaInicioReto, setMediaInicioReto) = remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(Unit) {
        val groupDoc = db.collection("groups").document(groupId).get().await()
        val members = groupDoc.get("members") as? Map<*, *> ?: return@LaunchedEffect
        val userMap = members[userId] as? Map<*, *> ?: return@LaunchedEffect

        setUserName(userMap["name"] as? String ?: "Usuario")
        val userPhase = (userMap["phase"] as? Long)?.toInt() ?: 1
        setCurrentPhase(userPhase)
        setPhaseInfo(getPhaseDetails(userPhase))

        val mediaInicio = fetchPhaseAverageUsage(userId, 1, groupId)?.toDouble()
        setMediaInicioReto(mediaInicio)

        if (userPhase > 1) {
            val media = fetchPhaseAverageUsage(userId, userPhase - 1, groupId)?.toDouble()
            setMediaFaseAnterior(media)

            val configSnapshot = db.collection("groups").document(groupId)
                .collection("reto").document("config").get().await()
            val porcentajeSemanal = configSnapshot.getDouble("porcentajeSemanal") ?: 25.0

            val factor = 1 - (porcentajeSemanal / 100.0)
            val exponent = userPhase - 1
            val target = (mediaInicio ?: 0.0) * factor.pow(exponent)
            setTargetUsage(target)
        }
    }

    fun formatMillisToTime(millis: Double): String {
        val seconds = (millis / 1000).toLong()
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return "${hours} horas ${minutes} minutos"
    }

    fun calculateReductionPercentage(oldValue: Double?, newValue: Double?): Float {
        if (oldValue == null || newValue == null || oldValue == 0.0) return 0f
        return (((oldValue - newValue) / oldValue) * 100).toFloat()
    }

    phaseInfo?.let { phase ->
        val reductionPhasePercentage = calculateReductionPercentage(mediaFaseAnterior, targetUsage)
        val reductionStartPercentage = calculateReductionPercentage(mediaInicioReto, targetUsage)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .padding(bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Hola $userName",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Estás entrando en la fase ${phase.phase}",
                    fontSize = 20.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Duración: ${phase.duration} días",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = phase.description,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))

                if (currentPhase > 1 && mediaFaseAnterior != null && targetUsage != null) {
                    Text(
                        text = "Tu uso medio diario en la fase anterior fue de ${formatMillisToTime(mediaFaseAnterior)}.",
                        fontSize = 16.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Tu objetivo esta semana es no superar ${formatMillisToTime(targetUsage)} diarios.",
                        fontSize = 16.sp,
                        color = Color(0xFF9CA3DB),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (reductionPhasePercentage > 0f) {
                        Text(
                            text = "Cumpliendo esta fase habrás reducido al menos un ${"%.0f".format(reductionPhasePercentage)}% el uso del movil respecto a la fase anterior.",
                            fontSize = 16.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (currentPhase > 2 && reductionStartPercentage > 0f) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Esto supone haber reducido en al menos un ${"%.0f".format(reductionStartPercentage)}% respecto al inicio del reto.",
                            fontSize = 16.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        updateUserStageInGroup(groupId, userId)
                        val userRef = db.collection("users").document(userId).collection("groups").document(groupId)

                        val phaseData = mapOf(
                            "fase" to currentPhase,
                            "fecha_inicio" to startDate,
                            "fecha_fin" to endDate
                        )
                        userRef.collection("phases").document("phase${currentPhase}").set(phaseData)
                        navController.navigate(Screen.Objectives.route)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(Color(0xFF5A4F8D)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.8f)
                    .padding(bottom = 24.dp)
                    .shadow(8.dp, shape = RoundedCornerShape(16.dp))
            ) {
                Text("Aceptar retos y empezar fase", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}