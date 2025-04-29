package com.example.detoxapp

import android.icu.util.LocaleData
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.internal.format
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.pow

@Composable
fun PhaseEndScreen(
    navController: NavController,
    groupViewModel: GroupViewModel,
    auth: FirebaseAuth
) {
    val userId = auth.currentUser?.uid ?: return
    val groupId = groupViewModel.groupId.value ?: return
    val coroutineScope = rememberCoroutineScope()

    val (averageUsage, setAverageUsage) = remember { mutableStateOf<Long?>(null) }
    val (initialUsage, setInitialUsage) = remember { mutableStateOf<Long?>(null) }
    val (currentPhase, setCurrentPhase) = remember { mutableStateOf<Int?>(null) }
    val (reductionConfig, setReductionConfig) = remember { mutableStateOf<ReductionConfig?>(null) }
    val (isLoading, setIsLoading) = remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val phase = fetchUserPhase(groupId, userId)
            val config = fetchGroupReductionConfig(groupId)
            val avgUsage = fetchPhaseAverageUsage(userId, phase)
            val initialAvg = fetchPhaseAverageUsage(userId, 1)

            setCurrentPhase(phase)
            setAverageUsage(avgUsage)
            setReductionConfig(config)
            setInitialUsage(initialAvg)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            setIsLoading(false)
        }
    }

    if (isLoading || currentPhase == null || averageUsage == null || reductionConfig == null || initialUsage == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF5A4F8D))
        }
        return
    }

    // Convertir milisegundos a horas y minutos
    fun formatDuration(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000 // Convertir milisegundos a segundos
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return "${hours} horas y ${minutes} minutos"
    }

    val reductionWeekly = reductionConfig.porcentajeSemanal / 100.0
    val initialHours = (initialUsage ?: 0L) / 1000.0 / 3600  // Convertir milisegundos a horas
    val averageSeconds = averageUsage ?: 0L  // Asegúrate de que averageUsage es en milisegundos
    val nextPhase = currentPhase + 1

    val targetSecondsList = listOf(
        (initialHours * (1 - reductionWeekly) * 3600 * 1000).toLong(),  // Convertir a milisegundos
        (initialHours * (1 - reductionWeekly).pow(2) * 3600 * 1000).toLong(),
        (initialHours * (1 - reductionWeekly).pow(3) * 3600 * 1000).toLong()
    )

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

    val decisionText = when (decision) {
        "next" -> "¡Has logrado el objetivo! Pasas a la fase $nextPhase."
        "back" -> "Tu uso ha aumentado. Volverás a la fase ${currentPhase - 1}."
        "stay" -> "Aún no alcanzas el objetivo para pasar de fase pero estás en el camino correcto. Repetirás la fase $currentPhase para seguir progresando."
        "end" -> "¡Has completado el reto! Has reducido tu uso un ${reductionConfig.porcentajeTotal}% desde el inicio del reto."
        else -> ""
    }

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

        if (decision != "end") {
            Button(
                onClick = {
                    coroutineScope.launch {
                        savePhaseForUser(userId, currentPhase, averageUsage)
                        val newPhase = when (decision) {
                            "next" -> currentPhase + 1
                            "back" -> currentPhase - 1
                            else -> currentPhase
                        }
                        val db = FirebaseFirestore.getInstance()

                        val memberField = "members.$userId.etapa"
                        db.collection("groups")
                            .document(groupId)
                            .update(memberField, "Intro")
                        updateUserPhase(groupId, userId, newPhase)
                        navController.navigate(Screen.PhaseIntroScreen.route)
                    }
                },
                colors = ButtonDefaults.buttonColors(Color(0xFF5A4F8D)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Empezar fase $nextPhase", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
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


suspend fun fetchPhaseAverageUsage(userId: String, phaseNumber: Int): Long? {
    val db = FirebaseFirestore.getInstance()
    val formatterPhase = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    val formatterFirestore = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    return try{
        val phaseDoc = db.collection("users")
            .document(userId)
            .collection("phases")
            .document("phase$phaseNumber")
            .get()
            .await()

        val startString = phaseDoc.getString("fecha_inicio")
        val endString = phaseDoc.getString("fecha_fin")

        if (startString == null || endString == null) return null

        val startDate = LocalDate.parse(startString, formatterPhase)
        val endDate = LocalDate.parse(endString, formatterPhase).minusDays(1)

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

