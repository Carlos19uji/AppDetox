package com.example.detoxapp

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale
import kotlin.math.pow

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
@Composable
fun GroupMainScreen(
    navController: NavController,
    groupViewModel: GroupViewModel,
    auth: FirebaseAuth
) {
    val userID = auth.currentUser?.uid ?: return
    val groupId = groupViewModel.groupId.value ?: return
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    val phase = remember { mutableStateOf(1) }
    val userName = remember { mutableStateOf("Usuario") }
    val currentDay = remember { mutableStateOf(1) }
    val totalDays = remember { mutableStateOf(7) }

    var isPhaseValid by remember { mutableStateOf(false) }
    val (reductionConfig, setReductionConfig) = remember { mutableStateOf<ReductionConfig?>(null) }
    val (initialUsage, setInitialUsage) = remember { mutableStateOf<Long?>(null) }
    val (averageUsage, setAverageUsage) = remember { mutableStateOf<Long?>(null) }

    var topApps by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            // Fetch phase number, user name and reduction config
            phase.value = fetchUserPhase(groupId, userID)
            userName.value = fetchUserName(groupId, userID)
            setReductionConfig(fetchGroupReductionConfig(groupId))
            setInitialUsage(fetchPhaseAverageUsage(userID, 1, groupId))
            setAverageUsage(fetchPhaseAverageUsage(userID, phase.value, groupId))

            // Fetch phase document to get date info
            val phaseDocRef = db.collection("users").document(userID)
                .collection("groups").document(groupId)
                .collection("phases")
                .document("phase${phase.value}")

            val phaseDoc = phaseDocRef.get().await()

            if (phaseDoc.exists()) {
                val fechaInicioString = phaseDoc.getString("fecha_inicio")
                val fechaFinString = phaseDoc.getString("fecha_fin")

                val format = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                val fechaInicio = fechaInicioString?.let { format.parse(it) }
                val fechaFin = fechaFinString?.let { format.parse(it) }

                if (fechaInicio != null && fechaFin != null) {
                    val today = Calendar.getInstance().time
                    val diffInicio = ((today.time - fechaInicio.time) / (1000 * 60 * 60 * 24)).toInt() + 1
                    val diffTotal = ((fechaFin.time - fechaInicio.time) / (1000 * 60 * 60 * 24)).toInt() + 1

                    currentDay.value = diffInicio.coerceAtMost(diffTotal)
                    totalDays.value = diffTotal - 1

                    if (today >= fechaFin) {
                        val memberField = "members.$userID.etapa"
                        db.collection("groups").document(groupId).update(memberField, "End")

                        navController.navigate(Screen.PhaseEndScreen.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    } else {
                        isPhaseValid = true
                    }
                } else {
                    isPhaseValid = true
                }
            } else {
                isPhaseValid = true
            }

            // --- Aquí empieza la lectura del uso por app desde Firebase ---

            val formatterPhase = DateTimeFormatter.ofPattern("dd-MM-yyyy")
            val formatterFirestore = DateTimeFormatter.ofPattern("yyyy-MM-dd")

            val startString = phaseDoc.getString("fecha_inicio")
            val endString = phaseDoc.getString("fecha_fin")

            if (startString != null && endString != null) {
                val startDate = LocalDate.parse(startString, formatterPhase)
                val endDate = LocalDate.parse(endString, formatterPhase)
                val days = ChronoUnit.DAYS.between(startDate, endDate).toInt()

                val appsUsageMap = mutableMapOf<String, Long>()

                for (i in 0 until days) {
                    val date = startDate.plusDays(i.toLong())
                    val docId = date.format(formatterFirestore)

                    val usageDoc = db.collection("users")
                        .document(userID)
                        .collection("time_use")
                        .document(docId)
                        .get()
                        .await()

                    if (usageDoc.exists()) {
                        val dayData = usageDoc.data ?: emptyMap<String, Any>()

                        dayData.forEach { (appName, usage) ->
                            val usageLong = (usage as? Number)?.toLong() ?: 0L
                            appsUsageMap[appName] = (appsUsageMap[appName] ?: 0L) + usageLong
                        }
                    }
                }

                // Ordena las apps por tiempo de uso descendente y toma las 3 primeras
                topApps = appsUsageMap.toList()
                    .sortedByDescending { it.second }
                    .take(3)
            }
        } catch (e: Exception) {
            Log.e("GroupMainScreen", "Error al obtener datos: ${e.message}")
            isPhaseValid = true
        }
    }

    val reductionWeekly = reductionConfig?.porcentajeSemanal?.div(100.0) ?: 0.1
    val initialHours = (initialUsage ?: 0L) / 1000.0 / 3600

    val targetMinutesList = List(10) { i ->
        (initialHours * (1 - reductionWeekly).pow(i + 1) * 60).toInt()
    }

    val targetUsageMinutes = if (phase.value > 1 && phase.value - 2 < targetMinutesList.size) {
        targetMinutesList[phase.value - 2]
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Hola, ${userName.value}",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Estás en la fase ${phase.value}",
                color = Color.White,
                fontSize = 18.sp
            )

            Text(
                text = "${currentDay.value}/${totalDays.value} días en esta fase",
                color = Color(0xFFCCCCCC),
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = currentDay.value.toFloat() / totalDays.value.toFloat().coerceAtLeast(1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = Color(0xFF8E24AA),
                backgroundColor = Color.DarkGray
            )

            Spacer(modifier = Modifier.height(32.dp))

            val usageColor = if ((averageUsage ?: 0L) <= ((targetUsageMinutes ?: 0) * 60 * 1000L)) {
                Color(0xFF81C784)
            } else {
                Color(0xFFFF6B6B)
            }

            Text(
                text = "Uso medio diario: ${formatDuration(averageUsage ?: 0L)}",
                color = usageColor,
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Objetivo diario: ${formatDuration((targetUsageMinutes ?: 0) * 60 * 1000L)}",
                color = Color(0xFFFFD54F),
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(64.dp))

            if (topApps.isNotEmpty()) {

                Text(
                    text = "Top 3 apps que más has usado desde que empezó esta fase:",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                topApps.forEach { (appName, usageTime) ->
                    val hours = (usageTime / (1000 * 60 * 60)).toInt()
                    val minutes = ((usageTime / (1000 * 60)) % 60).toInt()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(Color(0xFF222222), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Text(text = appName, color = Color.White, fontSize = 16.sp)
                            Text(text = "${hours}h ${minutes}min", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

suspend fun fetchUserName(groupId: String, userId: String): String {
    val db = FirebaseFirestore.getInstance()
    val groupDoc = db.collection("groups").document(groupId).get().await()

    val members = groupDoc.get("members") as? Map<*, *> ?: return "Usuario"
    val userData = members[userId] as? Map<*, *> ?: return "Usuario"

    return userData["name"] as? String ?: "Usuario"
}