package com.carlosrmuji.detoxapp

import android.app.Activity
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToInt



@Composable
fun Objectives(
    navController: NavController,
    groupViewModel: GroupViewModel,
    auth: FirebaseAuth,
    adViewModel: AdViewModel
) {
    var loadingState by remember { mutableStateOf(true) }
    val userId = auth.currentUser?.uid ?: return
    val groupId = groupViewModel.groupId.value ?: return

    val phase = remember { mutableStateOf(1) }
    val challengeCheckList = remember { mutableStateMapOf<Int, Boolean>() }
    val showDialog = remember { mutableStateOf(false) }
    val selectedChallenge = remember { mutableStateOf<Challenge?>(null) }
    val selectedIndex = remember { mutableStateOf<Int?>(null) }
    var isPhaseValid by remember { mutableStateOf(false) }
    var reflectionSaved by remember { mutableStateOf(false) }

    val (reductionConfig, setReductionConfig) = remember { mutableStateOf<ReductionConfig?>(null) }
    val (initialUsage, setInitialUsage) = remember { mutableStateOf<Long?>(null) }

    val db = FirebaseFirestore.getInstance()

    // Efecto de carga de datos
    LaunchedEffect(Unit) {
        // 1) Carga fase, config y uso inicial
        phase.value = fetchUserPhase(groupId, userId)
        setReductionConfig(fetchGroupReductionConfig(groupId))
        setInitialUsage(fetchPhaseAverageUsage(userId, 1, groupId))

        // 2) Validación de fecha de fin de fase
        try {
            val phaseDoc = db.collection("users").document(userId)
                .collection("groups").document(groupId)
                .collection("phases").document("phase${phase.value}")
                .get().await()

            if (phaseDoc.exists()) {
                val fechaFinString = phaseDoc.getString("fecha_fin")
                val fechaFinDate = fechaFinString
                    ?.let { SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).parse(it) }

                if (fechaFinDate != null) {
                    val now = Calendar.getInstance()
                    val fechaFin = Calendar.getInstance().apply { time = fechaFinDate }
                    if (now.after(fechaFin) || now.get(Calendar.DAY_OF_YEAR) == fechaFin.get(Calendar.DAY_OF_YEAR)) {
                        db.collection("groups").document(groupId)
                            .update("members.$userId.etapa", "End")
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
        } catch (_: Exception) {
            isPhaseValid = true
        }

        // 3) Recuperar retos completados
        val phaseChallenges = getPhaseChallenges(phase.value)
        val retosSnapshot = db.collection("users").document(userId)
            .collection("groups").document(groupId)
            .collection("phases").document("phase${phase.value}")
            .collection("retos").get().await()

        retosSnapshot.documents.forEach { doc ->
            val idx = phaseChallenges.indexOfFirst { it.title == doc.id }
            if (idx != -1) {
                challengeCheckList[idx] = doc.getBoolean("completed") ?: false
            }
        }

        // 4) Comprobar reflexión diaria
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val messages = db.collection("users").document(userId)
            .collection("groups").document(groupId)
            .collection("messages")
            .whereEqualTo("userId", userId)
            .get().await().documents

        messages.find { it.getString("timestamp")?.startsWith(today) == true }
            ?.let { idx ->
                getPhaseChallenges(phase.value).indexOfFirst { it.title == "Reflexión diaria" }
            }?.takeIf { it >= 0 }
            ?.let { challengeCheckList[it] = true }

        // 5) Fin de carga
        loadingState = false
    }

    // Raíz con fondo negro siempre
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (loadingState || !isPhaseValid || reductionConfig == null || initialUsage == null) {
            // Spinner blanco centrado mientras carga
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        } else {
            // Todo listo: dibuja tu UI de retos
            val reductionWeekly = reductionConfig.porcentajeSemanal / 100.0
            val phaseChallenges = getPhaseChallenges(phase.value)
            val initialHours = initialUsage / 1000.0 / 3600
            val targetList = listOf(
                (initialHours * (1 - reductionWeekly) * 3600 * 1000).toLong(),
                (initialHours * (1 - reductionWeekly).pow(2) * 3600 * 1000).toLong(),
                (initialHours * (1 - reductionWeekly).pow(3) * 3600 * 1000).toLong()
            )
            val targetCurrent = when (phase.value) {
                2 -> targetList[0]
                3 -> targetList[1]
                4 -> targetList[2]
                else -> null
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Retos de la Fase ${phase.value}",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(32.dp))

                Text(
                    text = getPhaseObjective(phase.value, targetCurrent ?: 0).description,
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.weight(1f))

                phaseChallenges.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (row.size == 1) Arrangement.Center else Arrangement.SpaceEvenly
                    ) {
                        row.forEachIndexed { _, challenge ->
                            val idx = phaseChallenges.indexOf(challenge)
                            ObjectiveCard(
                                challenge = challenge,
                                checked = challengeCheckList[idx] ?: false,
                                onCheckedChange = { checked ->
                                    challengeCheckList[idx] = checked
                                    if (challenge.isManuallyCheckable) {
                                        db.collection("users").document(userId)
                                            .collection("groups").document(groupId)
                                            .collection("phases").document("phase${phase.value}")
                                            .collection("retos").document(challenge.title)
                                            .set(mapOf("completed" to checked))
                                    }
                                },
                                onClick = {
                                    selectedChallenge.value = challenge
                                    selectedIndex.value = idx
                                    showDialog.value = true
                                },
                                onReflectionSaved = { reflectionSaved = it }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (showDialog.value && selectedChallenge.value != null) {
                    showChallengeDialog(
                        challenge = selectedChallenge.value!!,
                        onDismiss = { showDialog.value = false },
                        auth = auth,
                        groupViewModel = groupViewModel,
                        navController = navController,
                        adViewModel = adViewModel
                    )
                }
            }
        }
    }
}




@Composable
fun ObjectiveCard(
    challenge: Challenge,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onReflectionSaved: (Boolean) -> Unit // Callback para actualizar el estado cuando se guarda la reflexión
) {
    // Determinamos el color del icono y del texto
    val iconTintColor = when {
        challenge.title == "Reflexión diaria" -> if (checked) Color(0xFF4CAF50) else Color.Red // Reflexión diaria siempre en rojo si no está hecha
        checked -> Color(0xFF4CAF50) // Verde si está hecha
        challenge.isManuallyCheckable -> Color.Red // Rojo si se puede marcar y no está hecha
        else -> Color.White // Blanco si no se puede marcar y no está hecha
    }

    val textColor = when {
        challenge.title == "Reflexión diaria" -> if (checked) Color(0xFF4CAF50) else Color.Red // Reflexión diaria siempre en rojo si no está hecha
        checked -> Color(0xFF4CAF50) // Verde si está hecha
        challenge.isManuallyCheckable -> Color.Red // Rojo si se puede marcar y no está hecha
        else -> Color.White // Blanco si no se puede marcar y no está hecha
    }

    Card(
        modifier = Modifier
            .width(160.dp)
            .aspectRatio(1f)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1EF),
            contentColor = Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = challenge.title,
                tint = iconTintColor,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = challenge.title,
                color = textColor,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Checkbox(
                checked = checked,
                onCheckedChange = if (challenge.isManuallyCheckable) {
                    onCheckedChange
                } else null,
                enabled = challenge.isManuallyCheckable,
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF4CAF50),
                    uncheckedColor = if (challenge.isManuallyCheckable && !checked) Color.Red else Color.White
                )
            )
        }
    }

    // Si se trata de una reflexión diaria, cuando se guarda, se actualiza el estado
    if (challenge.title == "Reflexión diaria" && checked) {
        onReflectionSaved(true) // Esto actualizará el estado de la reflexión
    }
}


@Composable
fun showChallengeDialog(
    challenge: Challenge,
    onDismiss: () -> Unit,
    auth: FirebaseAuth,
    groupViewModel: GroupViewModel,
    navController: NavController,
    adViewModel: AdViewModel
) {
    val context = LocalContext.current
    val userId = auth.currentUser?.uid ?: return
    val db = FirebaseFirestore.getInstance()
    val coroutineScope = rememberCoroutineScope()
    val groupId = groupViewModel.groupId.value ?: return

    var reflectionText by remember { mutableStateOf("") }
    val isReflectionChallenge = challenge.title == "Reflexión diaria"
    var alreadyWritten by remember { mutableStateOf<Boolean?>(null) }

    // 🔍 Buscar si ya existe reflexión para hoy
    LaunchedEffect(Unit) {
        val now = LocalDateTime.now()
        val dateOnly = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        val messagesRef = db.collection("users").document(userId)
            .collection("groups").document(groupId)
            .collection("messages")

        val existing = messagesRef
            .whereEqualTo("userId", userId)
            .get().await()
            .documents
            .find { it.getString("timestamp")?.startsWith(dateOnly) == true }

        alreadyWritten = existing != null
    }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        confirmButton = {
            Row {
                if (isReflectionChallenge && alreadyWritten == false) {
                    TextButton(onClick = {
                        coroutineScope.launch {
                            val now = LocalDateTime.now()
                            val timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                            val messageId = UUID.randomUUID().toString()

                            val message = mapOf(
                                "id" to messageId,
                                "userId" to userId,
                                "timestamp" to timestamp,
                                "text" to reflectionText.trim()
                            )

                            db.collection("users").document(userId)
                                .collection("groups").document(groupId)
                                .collection("messages")
                                .document(messageId)
                                .set(message)
                                .await()

                            adViewModel.messageSaveInterstitialAd?.let { ad ->
                                ad.show(context as Activity)
                                adViewModel.clearMessageSaveAd()
                                adViewModel.loadMessageSaveAd()
                            }

                            onDismiss()
                            navController.navigate(Screen.Messages.route)
                        }
                    }) {
                        Text("Guardar", color = Color.White)
                    }
                } else if (isReflectionChallenge && alreadyWritten == true) {
                    TextButton(onClick = {
                        onDismiss()
                        navController.navigate(Screen.Messages.route)
                    }) {
                        Text("Ir a reflexiones", color = Color.White)
                    }
                }

                TextButton(onClick = { onDismiss() }) {
                    Text("Cerrar", color = Color.White)
                }
            }
        },
        title = {
            Text(
                text = challenge.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column {
                Text(text = challenge.description, color = Color.LightGray)
                Spacer(modifier = Modifier.height(12.dp))
                if (isReflectionChallenge && alreadyWritten == false) {
                    Text(text = "Escribe tu reflexión del día:", color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = reflectionText,
                        onValueChange = { reflectionText = it },
                        placeholder = { Text("Hoy aprendí que...") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF2E2E3F),
                            unfocusedContainerColor = Color(0xFF2E2E3F),
                            cursorColor = Color.White,
                            focusedIndicatorColor = Color.White,
                            unfocusedIndicatorColor = Color.Gray
                        ),
                        textStyle = LocalTextStyle.current.copy(color = Color.White)
                    )
                } else if (isReflectionChallenge && alreadyWritten == true) {
                    Spacer(modifier= Modifier.height(32.dp))
                    Text(
                        text = "Ya escribiste tu reflexión de hoy. Puedes verla en la sección de reflexiones.",
                        color = Color.White
                    )
                }
            }
        },
        containerColor = Color(0xFF1E1E2F),
        shape = RoundedCornerShape(16.dp)
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



suspend fun savePhaseForUser(userId: String, phase: Int, groupId: String) {
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

    db.collection("users")
        .document(userId)
        .collection("groups")
        .document(groupId)
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

fun getPhaseObjective(phase: Int, targetDuration: Long): PhaseInfo{
    return when (phase) {
            1 -> PhaseInfo(1, 5, "Estas en la fase 1 del reto, tu objetivo para esta semana es ir aplicando pequeños cambios en tu uso de movil mientras empezamos a recopilar datos sobre tu tiempo de uso diario.")
            2 -> PhaseInfo(2, 7, "Estas en la fase 2 del reto, tu objetivo para esta semana es reducir el tiempo de uso a un maximo de ${formatDuration(targetDuration)} al día. Para lograr este objetivo manten los retos que hiciste en fases anteriores e implementa los nuevos habitos/retos de esta fase.")
            3 -> PhaseInfo(3, 7, "Estas en la fase 3 del reto, tu objetivo para esta semana es reducir el tiempo de uso a un maximo de ${formatDuration(targetDuration)} al día. Para lograr este objetivo manten los retos que hiciste en fases anteriores e implementa los nuevos habitos/retos de esta fase.")
            4 -> PhaseInfo(4, 7, "Estas en la fase 4 del reto, tu objetivo para esta semana es reducir el tiempo de uso a un maximo de ${formatDuration(targetDuration)} al día. Para lograr este objetivo manten los retos que hiciste en fases anteriores e implementa los nuevos habitos/retos de esta fase.")
            else -> PhaseInfo(1, 5, "")
    }
}

fun getPhaseChallenges(phase: Int): List<Challenge> {
    return when (phase) {
        1 -> listOf(
            Challenge("Mover apps", "Mueve las apps que más usas fuera de la pantalla principal cuanto mas alejada de la pantalla principal este la pantalla a la qe las mueves mejor.\n\nUna vez lo hayas hecho marca el check para esta tarea en la app", true),
            Challenge("Modo oscuro o blanco y negro", "Activa el modo oscuro en tu movil y en todas las aplicaciones que te lo permitan o pon el móvil en blanco y negro si es posible.\n\nUna vez lo hayas hecho marca el check para esta tarea en la app", true),
            Challenge("Eliminar app ligera", "Elimina una app que uses habitualmente y no sea una red social (juego, compras, noticias...)\n\nUna vez lo hayas hecho marca el check para esta tarea en la app", true),
            Challenge("Reflexión diaria", "Comparte con los miembros del grupo alguna dificultad que hayas tenido para reducir el uso del movil / algun beneficio que has notado gracias a usar menos el movil / algo que hayas hecho en lugar de estar perdiendo el tiempo con el movil.", false)
        )
        2 -> listOf(
            Challenge("Desactivar notificaciones", "Desactiva todas las notificaciones salvo llamadas y WhatsApp familiar/laboral.\n\nUna vez lo hayas hecho marca el check para esta tarea en la app", true),
            Challenge("Evitar uso tras despertar", "No uses el móvil durante la primera media hora del día. Al despertarte no uses el movil, antes haz habitos como desayunar, vestirte, ducharte...", false),
            Challenge("Eliminar 2 apps ligeras", "Elimina 2 apps que uses habitalmente y no sean redes sociales (juegos, compras, noticias...) \n\nUna vez lo hayas hecho marca el check para esta tarea en la app", true),
            Challenge("Reflexión diaria", "Comparte con los miembros del grupo alguna dificultad que hayas tenido para reducir el uso del movil / algun beneficio que has notado gracias a usar menos el movil / algo que hayas hecho en lugar de estar perdiendo el tiempo con el movil.", false)
        )
        3 -> listOf(
            Challenge("Evitar uso antes de dormir", "Media hora antes de ir a dormir pon el móvil en modo avión o dejalo en otra habitación y lee media hora (en fisico) o escribe en un papel las cosas que tienes que hacer al dia siguiente. Cuando acabes ves a dormir.", false),
            Challenge("Eliminar red social clave", "Borra una red social como TikTok/Instagram del móvil y si has de usarla hazlo pero solo desde el navegador o el PC, no vuelvas a descargar la app. \n\nUna vez lo hayas hecho marca el check para esta tarea en la app", true),
            Challenge("Reflexión diaria", "Comparte con los miembros del grupo alguna dificultad que hayas tenido para reducir el uso del movil / algun beneficio que has notado gracias a usar menos el movil / algo que hayas hecho en lugar de estar perdiendo el tiempo con el movil.", false)
        )
        4 -> listOf(
            Challenge("Eliminar todas las redes", "Borra todas las redes excepto WhatsApp y si has de usarla hazlo pero solo desde el navegador o el PC, no vuelvas a descargarlas. \n\nUna vez lo hayas hecho marca el check para esta tarea en la app", true),
            Challenge("Reflexión diaria", "Comparte con los miembros del grupo alguna dificultad que hayas tenido para reducir el uso del movil / algun beneficio que has notado gracias a usar menos el movil / algo que hayas hecho en lugar de estar perdiendo el tiempo con el movil.", false)
        )
        else -> emptyList()
    }
}
