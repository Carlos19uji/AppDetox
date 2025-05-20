package com.example.detoxapp

import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
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
    val etapa = remember { mutableStateOf("") }

    var isPhaseValid by remember { mutableStateOf(false) }
    val (reductionConfig, setReductionConfig) = remember { mutableStateOf<ReductionConfig?>(null) }
    val (initialUsage, setInitialUsage) = remember { mutableStateOf<Long?>(null) }
    val (averageUsage, setAverageUsage) = remember { mutableStateOf<Long?>(null) }

    var topAppDetails by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {

            phase.value = fetchUserPhase(groupId, userID)
            userName.value = fetchUserName(groupId, userID)
            setReductionConfig(fetchGroupReductionConfig(groupId))
            setInitialUsage(fetchPhaseAverageUsage(userID, 1, groupId))
            setAverageUsage(fetchPhaseAverageUsage(userID, phase.value, groupId))

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

            val groupDoc = db.collection("groups").document(groupId).get().await()
            val membersMap = groupDoc.get("members") as? Map<*, *>
            val userStageMap = membersMap?.get(userID) as? Map<*, *>
            val userStage = userStageMap?.get("etapa") as? String

            etapa.value = userStage ?: ""

            // Navegar si la etapa es "Previa"
            if (etapa.value == "Previa") {
                navController.navigate(Screen.Previa.route) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
                return@LaunchedEffect
            }

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

                        dayData.forEach { (packageName, usage) ->
                            val usageLong = (usage as? Number)?.toLong() ?: 0L
                            appsUsageMap[packageName] = (appsUsageMap[packageName] ?: 0L) + usageLong
                        }
                    }
                }

                val top = appsUsageMap.toList()
                    .sortedByDescending { it.second }
                    .take(3)

                topAppDetails = top
            }
        } catch (e: Exception) {
            Log.e("GroupMainScreen", "Error: ${e.message}")
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
            Text("Hola, ${userName.value}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Estás en la fase ${phase.value}", color = Color.White, fontSize = 18.sp)
            Text("${currentDay.value}/${totalDays.value} días en esta fase", color = Color.Gray, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = currentDay.value.toFloat() / totalDays.value.coerceAtLeast(1).toFloat(),
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

            Text("Uso medio diario: ${formatDuration(averageUsage ?: 0L)}", color = usageColor, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Objetivo diario: ${formatDuration((targetUsageMinutes ?: 0) * 60 * 1000L)}", color = Color(0xFFFFD54F), fontSize = 16.sp)
            Spacer(modifier = Modifier.height(64.dp))

            if (topAppDetails.isNotEmpty()) {
                Text(
                    "Top 3 apps que más has usado desde que empezó esta fase:",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                topAppDetails.map { (packageName, usageTime) ->
                    AppUsage(appName = packageName, usageTime = usageTime)
                }.forEach { appUsage ->
                    AppUsageRow(app = appUsage, context = context)
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