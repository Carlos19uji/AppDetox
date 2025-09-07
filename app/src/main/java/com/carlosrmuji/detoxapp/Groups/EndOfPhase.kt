package com.carlosrmuji.detoxapp.Groups

import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.carlosrmuji.detoxapp.GroupViewModel
import com.carlosrmuji.detoxapp.ReductionConfig
import com.carlosrmuji.detoxapp.Screen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.pow

private const val TAG = "PhaseEndScreen"

@Composable
fun PhaseEndScreen(
    navController: NavController,
    groupViewModel: GroupViewModel,
    auth: FirebaseAuth
) {
    val userId = auth.currentUser?.uid ?: run {
        Log.e(TAG, "User ID is null. User not authenticated.")
        return
    }

    val groupId = groupViewModel.groupId.value ?: run {
        Log.e(TAG, "Group ID is null. No group selected.")
        return
    }

    val coroutineScope = rememberCoroutineScope()

    val (averageUsage, setAverageUsage) = remember { mutableStateOf<Long?>(null) }
    val (initialUsage, setInitialUsage) = remember { mutableStateOf<Long?>(null) }
    val (currentPhase, setCurrentPhase) = remember { mutableStateOf<Int?>(null) }
    val (reductionConfig, setReductionConfig) = remember { mutableStateOf<ReductionConfig?>(null) }
    val (isLoading, setIsLoading) = remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        Log.d(TAG, "LaunchedEffect triggered")
        try {
            val phase = fetchUserPhase(groupId, userId)
            Log.d(TAG, "Fetched user phase: $phase")
            val config = fetchGroupReductionConfig(groupId)
            Log.d(TAG, "Fetched reduction config: $config")
            val avgUsage = fetchPhaseAverageUsage(userId, phase, groupId)
            Log.d(TAG, "Fetched average usage for phase $phase: $avgUsage")
            val initialAvg = fetchPhaseAverageUsage(userId, 1, groupId)
            Log.d(TAG, "Fetched initial average usage: $initialAvg")

            setCurrentPhase(phase)
            setAverageUsage(avgUsage)
            setReductionConfig(config)
            setInitialUsage(initialAvg)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching phase data", e)
        } finally {
            setIsLoading(false)
            Log.d(TAG, "Finished data fetch - isLoading set to false")
        }
    }

    if (isLoading || currentPhase == null || averageUsage == null || reductionConfig == null || initialUsage == null) {
        Log.d(TAG, "Still loading or some data is null. Showing loading screen.")
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF5A4F8D))
        }
        return
    }

    Log.d(TAG, "Rendering UI with phase: $currentPhase")

    val reductionWeekly = reductionConfig.porcentajeSemanal / 100.0
    val initialHours = (initialUsage ?: 0L) / 1000.0 / 3600
    val averageSeconds = averageUsage ?: 0L
    val nextPhase = currentPhase + 1

    val targetSecondsList = listOf(
        (initialHours * (1 - reductionWeekly) * 3600 * 1000).toLong(),
        (initialHours * (1 - reductionWeekly).pow(2) * 3600 * 1000).toLong(),
        (initialHours * (1 - reductionWeekly).pow(3) * 3600 * 1000).toLong()
    )
    Log.d(TAG, "Target seconds per phase: $targetSecondsList")

    val decision = when (currentPhase) {
        1 -> "next"
        2 -> if (averageSeconds <= targetSecondsList[0]) "next" else "stay"
        3 -> when {
            averageSeconds <= targetSecondsList[1] -> "next"
            averageSeconds >= targetSecondsList[0] -> "back"
            else -> "stay"
        }

        4 -> when {
            averageSeconds <= targetSecondsList[2] -> "end"
            averageSeconds >= targetSecondsList[1] -> "back"
            else -> "stay"
        }

        else -> "stay"
    }

    Log.d(TAG, "Decision for phase $currentPhase: $decision")

    val targetDurationCurrent = when (currentPhase) {
        1 -> null
        2 -> targetSecondsList[0]
        3 -> targetSecondsList[1]
        4 -> targetSecondsList[2]
        else -> null
    }

    val targetDurationPrev = when (currentPhase) {
        3 -> targetSecondsList[0]
        4 -> targetSecondsList[1]
        else -> null
    }

    val decisionText = when (decision) {
        "next" -> "¡Has completado la fase $currentPhase! Pasas a la fase $nextPhase."
        "back" -> "Tu uso de móvil ha aumentado de ${formatDuration(targetDurationPrev!!)} en la fase ${currentPhase - 1} a ${
            formatDuration(
                averageSeconds
            )
        } en la fase $currentPhase. Volverás a la fase ${currentPhase - 1}."

        "stay" -> "Aún no alcanzas el objetivo de usar menos de ${
            formatDuration(
                targetDurationCurrent!!
            )
        } de media diaria para pasar de fase, pero estás en el camino correcto. Repetirás la fase $currentPhase para seguir progresando."

        "end" -> "¡Has completado el reto! Has reducido tu uso un ${reductionConfig.porcentajeTotal}% desde el inicio del reto."
        else -> ""
    }

    Log.d(TAG, "Displaying text: $decisionText")

    if (decision != "end") {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Fase $currentPhase finalizada",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Tu uso medio diario fue de ${formatDuration(averageSeconds)}.",
                fontSize = 16.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = decisionText,
                fontSize = 16.sp,
                color = when (decision) {
                    "next", "end" -> Color(0xFF8ADBB2)
                    "back" -> Color(0xFFDB8A8A)
                    "stay" -> Color(0xFFDBD28A)
                    else -> Color.White
                },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            val newPhase = when (decision) {
                "next" -> currentPhase + 1
                "back" -> currentPhase - 1
                else -> currentPhase
            }
            Button(
                onClick = {
                    coroutineScope.launch {
                        try {
                            Log.d(
                                TAG,
                                "Updating Firestore with new phase $newPhase for user $userId"
                            )
                            val db = FirebaseFirestore.getInstance()
                            val memberField = "members.$userId.etapa"
                            db.collection("groups")
                                .document(groupId)
                                .update(memberField, "Intro")
                            updateUserPhase(groupId, userId, newPhase)
                            savePhaseForUser(userId, newPhase, groupId)
                            Log.d(TAG, "Navigation to PhaseIntroScreen")
                            navController.navigate(Screen.PhaseIntroScreen.route)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating user phase or navigating", e)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(Color(0xFF5A4F8D)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Empezar fase $newPhase", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        var initialUsage by remember { mutableStateOf<Long?>(null) }
        var finalUsage by remember { mutableStateOf<Long?>(null) }

        LaunchedEffect(Unit) {
            try {
                val usageInitial = fetchPhaseAverageUsage(userId, 1, groupId)
                val usageFinal = fetchPhaseAverageUsage(userId, 4, groupId)

                initialUsage = usageInitial
                finalUsage = usageFinal
            } catch (e: Exception) {
                Log.e("FinishScreen", "Error fetching usage data: ${e.message}")
            }
        }

        val initial = initialUsage
        val final = finalUsage

        if (initial != null && final != null) {
            val reductionPercent = if (initial > 0) {
                ((initial - final).toDouble() / initial * 100).toInt()
            } else 0

            val initialFormatted = formatDuration(initial)
            val finalFormatted = formatDuration(final)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "🎉 ¡Enhorabuena! 🎉",
                        color = Color(0xFF81C784),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Has conseguido reducir el uso del móvil en un $reductionPercent% desde que empezó el reto.",
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Cuando empezó el reto usabas el móvil una media de $initialFormatted al día.",
                        color = Color(0xFFDDDDDD),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Ahora, al finalizar la fase 4, lo estás usando $finalFormatted al día.",
                        color = Color(0xFFDDDDDD),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Mantén los hábitos que has adoptado para continuar reduciendo tu uso del móvil o al menos mantener tu progreso.",
                        color = Color(0xFFBBBBBB),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            }
    }
}

fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return "${hours} horas y ${minutes} minutos"
}

suspend fun fetchGroupReductionConfig(groupId: String): ReductionConfig? {
    val db = FirebaseFirestore.getInstance()
    return try {
        val snapshot = db.collection("groups")
            .document(groupId)
            .collection("reto")
            .document("config")
            .get()
            .await()

        val total = snapshot.getLong("porcentajeTotal")?.toInt()
        val semanal = snapshot.getLong("porcentajeSemanal")?.toInt()

        if (total != null && semanal != null) {
            ReductionConfig(total, semanal)
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}


suspend fun fetchPhaseAverageUsage(userId: String, phaseNumber: Int, groupId: String): Long? {
    val db = FirebaseFirestore.getInstance()
    val formatterPhase = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    val formatterFirestore = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    return try{
        val phaseDoc = db.collection("users")
            .document(userId)
            .collection("groups")
            .document(groupId)
            .collection("phases")
            .document("phase$phaseNumber")
            .get()
            .await()

        val startString = phaseDoc.getString("fecha_inicio")
        val endString = phaseDoc.getString("fecha_fin")

        if (startString == null || endString == null) return null

        val startDate = LocalDate.parse(startString, formatterPhase)
        val endDate = LocalDate.parse(endString, formatterPhase)

        val days = ChronoUnit.DAYS.between(startDate, endDate).toInt()
        if (days <= 0) return null

        var totalUsage = 0L
        var countDays = 0

        for (i in 0 until days){
            val date = startDate.plusDays(i.toLong())
            val docId = date.format(formatterFirestore)

            val usageDoc = db.collection("users")
                .document(userId).collection("time_use")
                .document(docId).get().await()

            if (usageDoc.exists()){
                val dayTotal = usageDoc.data?.values
                    ?.mapNotNull { it as? Long }
                    ?.sum() ?: 0L

                totalUsage += dayTotal
                countDays++
            }
        }

        if (countDays > 0) totalUsage / countDays else null

    } catch (e:Exception){
        e.printStackTrace()
        null
    }
}

