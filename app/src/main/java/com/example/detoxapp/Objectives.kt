package com.example.detoxapp

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt


@Composable
fun Objectives(navController: NavController, groupViewModel: GroupViewModel, auth: FirebaseAuth) {
    val userId = auth.currentUser?.uid ?: return
    val groupId = groupViewModel.groupId.value ?: return
    val coroutineScope = rememberCoroutineScope()

    val phase = remember { mutableStateOf(1) }
    val challengeCheckList = remember { mutableStateMapOf<Int, Boolean>() }

    val showDialog = remember { mutableStateOf(false) }
    val selectedChallenge = remember { mutableStateOf<Challenge?>(null) }
    val selectedIndex = remember { mutableStateOf<Int?>(null) }

    var isPhaseValid by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        phase.value = fetchUserPhase(groupId, userId)

        val db = FirebaseFirestore.getInstance()
        try {
            val phaseDocRef = db.collection("users").document(userId)
                .collection("phases")
                .document("phase${phase.value}")

            val phaseDoc = phaseDocRef.get().await()

            if (phaseDoc.exists()) {
                val fechaFinString = phaseDoc.getString("fecha_fin")

                if (fechaFinString != null) {
                    // Parsear el String a Date
                    val format = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                    val fechaFinDate = format.parse(fechaFinString)

                    if (fechaFinDate != null) {
                        val now = Calendar.getInstance()
                        val fechaFin = Calendar.getInstance().apply { time = fechaFinDate }

                        if (now.get(Calendar.YEAR) > fechaFin.get(Calendar.YEAR) ||
                            (now.get(Calendar.YEAR) == fechaFin.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) >= fechaFin.get(Calendar.DAY_OF_YEAR))
                        ) {
                            val memberField = "members.$userId.etapa"
                            db.collection("groups")
                                .document(groupId)
                                .update(memberField, "End")

                            navController.navigate(Screen.PhaseEndScreen.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        } else {
                            isPhaseValid = true
                        }
                    } else {
                        Log.e("Objectives", "Error parseando fecha_fin")
                        isPhaseValid = true
                    }
                } else {
                    Log.e("Objectives", "fecha_fin no encontrada o es null")
                    isPhaseValid = true
                }
            } else {
                Log.e("Objectives", "Documento de fase no encontrado")
                isPhaseValid = true
            }
        } catch (e: Exception) {
            Log.e("Objectives", "Error obteniendo datos de fase: ${e.message}")
            isPhaseValid = true
        }
    }

    if (isPhaseValid) {
        val phaseChallenges = getPhaseChallenges(phase.value)

        Column(modifier = Modifier.padding(16.dp)) {
            Text("Retos de la Fase ${phase.value}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            phaseChallenges.forEachIndexed { index, challenge ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            selectedChallenge.value = challenge
                            selectedIndex.value = index
                            showDialog.value = true
                        }
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Checkbox(
                            checked = challengeCheckList[index] ?: false,
                            onCheckedChange = { isChecked ->
                                challengeCheckList[index] = isChecked
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(challenge.title)
                    }
                }
            }

            if (showDialog.value && selectedChallenge.value != null && selectedIndex.value != null) {
                showChallengeDialog(
                    challenge = selectedChallenge.value!!,
                    index = selectedIndex.value!!,
                    cheklist = challengeCheckList,
                    onDismiss = { showDialog.value = false }
                )
            }
        }
    }
}


@Composable
fun showChallengeDialog(
    challenge: Challenge,
    index: Int,
    cheklist: MutableMap<Int, Boolean>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(challenge.title) },
        text = { Text(challenge.description) },
        confirmButton = {
            TextButton(
                onClick = {
                    cheklist[index] = true
                    onDismiss()
                }
            ) {
                Text("Marcar como hecho")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}




suspend fun fetchUserPhase(groupId: String, userId: String): Int {
    val doc = FirebaseFirestore.getInstance().collection("groups").document(groupId).get().await()
    val members = doc.get("members") as? Map<*, *> ?: return 1
    val userMap = members[userId] as? Map<*, *> ?: return 1
    return (userMap["phase"] as? Long)?.toInt() ?: 1
}

suspend fun updateUserPhase(groupId: String, userId: String, newPhase: Int) {
    FirebaseFirestore.getInstance().collection("groups")
        .document(groupId)
        .update("members.$userId.phase", newPhase)
}



suspend fun savePhaseForUser(userId: String, phase: Int, mediaFaseAnterior: Long?) {
    val db = FirebaseFirestore.getInstance()
    val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    val today = Calendar.getInstance()
    val fechaInicio = sdf.format(today.time)

    val fechaFin = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_MONTH, 7)
    }
    val fechaFinFormatted = sdf.format(fechaFin.time)

    val phaseData = hashMapOf(
        "fase" to phase,
        "fecha_inicio" to fechaInicio,
        "fecha_fin" to fechaFinFormatted
    )

    if (phase > 1 && mediaFaseAnterior != null) {
        phaseData["media_fase_anterior"] = mediaFaseAnterior
    }

    db.collection("users")
        .document(userId)
        .collection("phases")
        .document("phase$phase")
        .set(phaseData)
}

suspend fun updateUserStageInGroup(groupId: String, userId: String) {
    val db = FirebaseFirestore.getInstance()

    val memberField = "members.$userId.etapa"
    db.collection("groups")
        .document(groupId)
        .update(memberField, "Objectives")
}

fun getPhaseDetails(phase: Int): PhaseInfo {
    return when (phase) {
        1 -> PhaseInfo(1, 5, "Primera fase: Recopilar datos de uso y empezar el detox implementando pequeños cambios.")
        2 -> PhaseInfo(2, 7, "Empieza el reto real: Menos notificaciones, y generar hábitos offline.")
        3 -> PhaseInfo(3, 7, "Reduce uso nocturno, elimina redes sociales del móvil.")
        4 -> PhaseInfo(4, 7, "Eliminación completa de redes excepto WhatsApp, mantenimiento de hábitos.")
        else -> PhaseInfo(1, 5, "Fase desconocida")
    }
}

fun getPhaseChallenges(phase: Int): List<Challenge> {
    return when (phase) {
        1 -> listOf(
            Challenge("Mover apps", "Mueve las apps que más usas fuera de la pantalla principal."),
            Challenge("Modo oscuro o blanco y negro", "Activa modo oscuro o pon el móvil en blanco y negro."),
            Challenge("Eliminar app ligera", "Elimina una app que uses de vez en cuando: juego, periódico...")
        )
        2 -> listOf(
            Challenge("Desactivar notificaciones", "Desactiva todas salvo llamadas y WhatsApp familiar/laboral."),
            Challenge("Evitar uso tras despertar", "No uses el móvil durante la primera media hora del día."),
            Challenge("Eliminar 2 apps ligeras", "Elimina 2 apps de uso medio: juegos, compras, noticias...")
        )
        3 -> listOf(
            Challenge("Evitar uso antes de dormir", "Pon el móvil en modo avión y escribe/reflexiona antes de dormir."),
            Challenge("Eliminar red social clave", "Borra una red social como TikTok/Instagram del móvil.")
        )
        4 -> listOf(
            Challenge("Eliminar todas las redes", "Borra todas las redes excepto WhatsApp y úsalas solo en PC."),
            Challenge("Reflexión diaria", "Comparte algo que hiciste que reemplazó el uso de redes.")
        )
        else -> emptyList()
    }
}
