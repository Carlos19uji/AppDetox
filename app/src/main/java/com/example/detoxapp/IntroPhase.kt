package com.example.detoxapp

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

    val (phaseInfo, setPhaseInfo) = remember { mutableStateOf<PhaseInfo?>(null) }
    val (userName, setUserName) = remember { mutableStateOf("Usuario") }
    val (currentPhase, setCurrentPhase) = remember { mutableStateOf(1) }
    val (mediaFaseAnterior, setMediaFaseAnterior) = remember { mutableStateOf<Long?>(null) }
    val (targetUsage, setTargetUsage) = remember { mutableStateOf<Long?>(null) }
    val (mediaInicioReto, setMediaInicioReto) = remember { mutableStateOf<Long?>(null) } // Nueva variable

    LaunchedEffect(Unit) {
        val groupDoc = db.collection("groups").document(groupId).get().await()
        val members = groupDoc.get("members") as? Map<*, *> ?: return@LaunchedEffect
        val userMap = members[userId] as? Map<*, *> ?: return@LaunchedEffect

        setUserName(userMap["name"] as? String ?: "Usuario")
        val userPhase = (userMap["phase"] as? Long)?.toInt() ?: 1
        setCurrentPhase(userPhase)
        setPhaseInfo(getPhaseDetails(userPhase))

        // Obtener la media de la fase anterior
        if (userPhase > 1) {
            val media = fetchPhaseAverageUsage(userId, userPhase - 1)
            setMediaFaseAnterior(media)

            val mediaInicio = fetchPhaseAverageUsage(userId, 1) // Suponiendo que la fase 1 es el inicio del reto
            setMediaInicioReto(mediaInicio)

            val configSnapshot = db.collection("groups").document(groupId)
                .collection("reto").document("config").get().await()
            val porcentajeSemanal = configSnapshot.getLong("porcentajeSemanal")?.toInt() ?: 25
            val factor = 1 - (porcentajeSemanal / 100.0)
            val target = (media ?: 0L) * factor
            setTargetUsage(target.toLong())
        }
    }

    fun formatMillisToTime(millis: Long): String {
        val seconds = millis / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return "${hours} horas ${minutes} minutos"
    }

    fun calculateReductionPercentage(oldValue: Long?, newValue: Long?): Float {
        if (oldValue == null || newValue == null || oldValue == 0L) return 0f
        return ((oldValue - newValue).toFloat() / oldValue) * 100
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
                            text = "Cumpliendo esta fase habrás reducido al menos un ${reductionPhasePercentage.toInt()}% el uso del móvil respecto a la fase anterior.",
                            fontSize = 16.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (currentPhase > 2) {
                        Spacer(modifier = Modifier.height(16.dp))
                        if (reductionStartPercentage > 0f) {
                            Text(
                                text = "Esto supone haber reducido en al menos un ${reductionStartPercentage.toInt()}% respecto a cuando empezaste el reto.",
                                fontSize = 16.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        if (currentPhase > 1) {
                            savePhaseForUser(userId, currentPhase, mediaFaseAnterior)
                        }
                        updateUserStageInGroup(groupId, userId)
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