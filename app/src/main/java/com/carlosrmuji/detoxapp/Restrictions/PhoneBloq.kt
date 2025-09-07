package com.carlosrmuji.detoxapp.Restrictions

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.TabRowDefaults.Divider
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.carlosrmuji.detoxapp.Billing.AdViewModel
import com.carlosrmuji.detoxapp.PhoneBlockRule
import com.carlosrmuji.detoxapp.Screen
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale



@Composable
fun PhoneBloqScreen(navController: NavController, adViewModel: AdViewModel) {
    val context = LocalContext.current
    val userId = getUserId()
    val coroutineScope = rememberCoroutineScope()

    var userPlan by remember { mutableStateOf("base_plan") }
    var blocks by remember { mutableStateOf<List<PhoneBlockRule>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedBlock by remember { mutableStateOf<PhoneBlockRule?>(null) }
    var showLimitMessage by remember { mutableStateOf(false) } // 🔹 Solo se activa cuando se intenta añadir y no se puede
    var showAccessibilityDialog by remember { mutableStateOf(false) }

    // 🔹 Ocultar mensaje de límite automáticamente después de unos segundos
    LaunchedEffect(showLimitMessage) {
        if (showLimitMessage) {
            delay(5000)
            showLimitMessage = false
        }
    }

    // Cargar plan del usuario
    LaunchedEffect(userId) {
        try {
            val planDeferred = async { loadUserPlan(userId) }
            userPlan = planDeferred.await()
        } catch (e: Exception) {
            Toast.makeText(context, "Error cargando plan: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Cargar bloqueos desde Firestore
    LaunchedEffect(userId) {
        try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .collection("phone_blocks")
                .get()
                .await()

            blocks = snapshot.documents.mapNotNull { doc ->
                doc.toObject(PhoneBlockRule::class.java)
            }
        } catch (e: Exception) {
            Log.e("PhoneBloqScreen", "Error cargando reglas", e)
            errorMessage = "Error cargando bloqueos: ${e.message}"
        }
    }

    // Lógica de límite por plan
    val restrictionLimit = when (userPlan) {
        "base_plan" -> 1
        "plus_plan" -> 3
        "premium_plan" -> Int.MAX_VALUE
        else -> 1
    }

    val limitReached = blocks.size >= restrictionLimit

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

            // 🔹 Título fijo
            Text(
                text = "Bloqueo del móvil",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 🔹 LazyColumn solo para restricciones
            if (blocks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No hay bloqueos configurados",
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(blocks) { block ->
                        PhoneBlockRow(
                            block = block,
                            onEdit = { clickedBlock ->
                                // ✅ Comprobar accesibilidad antes de editar
                                if (!isAccessibilityServiceEnabled(context, UnifiedBlockerService::class.java)) {
                                    showAccessibilityDialog = true
                                } else {
                                    selectedBlock = clickedBlock
                                    showEditDialog = true
                                }
                            },
                            onToggleActive = { updated ->
                                coroutineScope.launch {
                                    try {
                                        updatePhoneBlock(userId, updated)
                                        blocks = blocks.map { if (it.id == updated.id) updated else it }
                                    } catch (e: Exception) {
                                        Log.e("PhoneBloqScreen", "Error actualizando bloqueo", e)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        // ✅ Mensaje justo encima del botón flotante
        if (showLimitMessage) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp) // deja espacio suficiente para el FAB
            ) {
                Text(
                    text = "Has alcanzado el límite de restricciones para tu plan.\nMejora tu plan para añadir más restricciones.",
                    color = Color(0xFFFF4C4C),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { navController.navigate(Screen.PlansScreen.route) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A4F8D))
                    ) {
                        Text("Mejorar plan", color = Color.White)
                    }
                }
            }
        }

        // 🔹 Botón flotante para añadir nueva restricción
        FloatingActionButton(
            onClick = {
                // ✅ Primero comprobar accesibilidad
                if (!isAccessibilityServiceEnabled(context, UnifiedBlockerService::class.java)) {
                    showAccessibilityDialog = true
                } else {
                    // Luego verificar límite
                    if (!limitReached) {
                        showDialog = true
                    } else {
                        showLimitMessage = true
                    }
                }
            },
            backgroundColor = Color(0xFF5A4F8D),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Añadir bloqueo", tint = Color.White)
        }

        // 🔹 Diálogo de accesibilidad
        if (showAccessibilityDialog) {
            AccessibilityPermissionDialog(
                showDialog = showAccessibilityDialog,
                onDismiss = { showAccessibilityDialog = false },
                onGoToSettings = {
                    openAccessibilitySettings(context)
                    showAccessibilityDialog = false
                }
            )
        }

        // 🔹 Diálogo para añadir nueva restricción
        if (showDialog) {
            PhoneScheduleDialog(
                onConfirm = { label, fromLocal, toLocal, selectedDays ->
                    coroutineScope.launch {
                        try {
                            val phoneBlockMap = selectedDays.associate { day ->
                                day.name to mapOf(
                                    "from" to convertToSpanish(fromLocal.toString()),
                                    "to" to convertToSpanish(toLocal.toString())
                                )
                            }
                            val newBlock = PhoneBlockRule(
                                id = "",
                                label = label,
                                isActive = true,
                                phone_block = phoneBlockMap
                            )
                            savePhoneBlock(userId, newBlock)
                            blocks = blocks + newBlock
                        } catch (e: Exception) {
                            Log.e("PhoneBloqScreen", "Error guardando bloqueo", e)
                            errorMessage = "Error guardando bloqueo: ${e.message}"
                        }
                    }
                    showDialog = false
                },
                onDismiss = { showDialog = false },
                adViewModel
            )
        }

        // 🔹 Diálogo para editar restricción existente
        if (showEditDialog && selectedBlock != null) {
            PhoneBlockEditDialog(
                block = selectedBlock!!,
                onConfirmEdit = { updatedBlock ->
                    coroutineScope.launch {
                        try {
                            updatePhoneBlock(userId, updatedBlock)
                            blocks = blocks.map { if (it.id == updatedBlock.id) updatedBlock else it }
                        } catch (e: Exception) {
                            Log.e("PhoneBloqScreen", "Error editando bloqueo", e)
                        }
                    }
                    showEditDialog = false
                },
                onDelete = { blockToDelete ->
                    coroutineScope.launch {
                        try {
                            deletePhoneBlock(userId, blockToDelete.id)
                            blocks = blocks.filter { it.id != blockToDelete.id }
                        } catch (e: Exception) {
                            Log.e("PhoneBloqScreen", "Error eliminando bloqueo", e)
                        }
                    }
                    showEditDialog = false
                },
                onDismiss = { showEditDialog = false },
                adViewModel= adViewModel
            )
        }

        // 🔹 Mostrar errores
        errorMessage?.let {
            Text(
                text = it,
                color = Color(0xFFFF4C4C),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
            )
        }
    }
}

@Composable
fun PhoneBlockRow(
    block: PhoneBlockRule,
    onEdit: (PhoneBlockRule) -> Unit,
    onToggleActive: (PhoneBlockRule) -> Unit
) {
    val first = block.phone_block.entries.firstOrNull()
    val fromLocal = convertFromSpanishToLocal(first?.value?.get("from") ?: "00:00")
    val toLocal = convertFromSpanishToLocal(first?.value?.get("to") ?: "00:00")
    val daysText = formatDays(block.phone_block.keys)

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (block.isActive) Color.Gray else Color.DarkGray) // Verde clarito
                .padding(horizontal = 16.dp, vertical = 12.dp)

        ) {
            Column(modifier = Modifier.weight(1f)
                .clickable { onEdit(block) } ) {
                if (block.label.isNotBlank()) {
                    Text(
                        text = block.label,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Text(
                    text = "De $fromLocal a $toLocal",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )

                if (daysText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = daysText,
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
            }

            Switch(
                checked = block.isActive,
                onCheckedChange = { checked -> onToggleActive(block.copy(isActive = checked)) },
                modifier = Modifier.scale(1.4f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF76FF03), // Verde más fosforito
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.DarkGray
                )
            )
        }

        Divider(color = Color.Black, thickness = 8.dp) // separación visual entre bloques
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneBlockEditDialog(
    block: PhoneBlockRule,
    onConfirmEdit: (PhoneBlockRule) -> Unit,
    onDelete: (PhoneBlockRule) -> Unit,
    onDismiss: () -> Unit,
    adViewModel: AdViewModel
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(block.label) }
    var selectedDays by remember { mutableStateOf(
        block.phone_block.keys.mapNotNull { day ->
            runCatching { DayOfWeek.valueOf(day.uppercase()) }.getOrNull()
        }.toSet()
    ) }

    val firstEntry = block.phone_block.entries.firstOrNull()
    var from by remember { mutableStateOf(convertFromSpanishToLocal(firstEntry?.value?.get("from") ?: "00:00").let { LocalTime.parse(it) }) }
    var to by remember { mutableStateOf(convertFromSpanishToLocal(firstEntry?.value?.get("to") ?: "00:00").let { LocalTime.parse(it) }) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Editar bloqueo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(12.dp))

                // Etiqueta
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Etiqueta", color = Color.LightGray) },
                    placeholder = { Text("Ej: Estudio, Dormir", color = Color.Gray) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color(0xFF5A4F8D),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Días
                Text("Repetir en días", color = Color.LightGray, fontSize = 14.sp)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DayOfWeek.values().forEach { day ->
                        val isSelected = selectedDays.contains(day)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedDays = if (isSelected) selectedDays - day else selectedDays + day },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, Locale("es", "ES")), color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF5A4F8D),
                                containerColor = Color.DarkGray,
                                labelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Horario
                Text("Horario", color = Color.LightGray, fontSize = 14.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TimePickerField("Desde", from) { from = it }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        TimePickerField("Hasta", to) { to = it }
                    }
                }

                errorText?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = Color(0xFFFF6B6B), fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Botones Cancelar y Guardar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            when {
                                selectedDays.isEmpty() -> errorText = "Selecciona al menos un día"
                                !from.isBefore(to) -> errorText = "La hora de inicio debe ser anterior a la de fin"
                                else -> {

                                    val activity = context as? Activity
                                    val ad = adViewModel.restrictedPhoneAd   // 🔹 Usamos el anuncio correcto

                                    val updatedMap = selectedDays.associate { day ->
                                        day.name.uppercase() to mapOf(
                                            "from" to convertToSpanish(from.toString()),
                                            "to" to convertToSpanish(to.toString())
                                        )
                                    }
                                    val updatedBlock = block.copy(
                                        label = label,
                                        phone_block = updatedMap
                                    )

                                if (ad != null && activity != null) {
                                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                                        override fun onAdDismissedFullScreenContent() {
                                            adViewModel.clearRestrictedPhoneAd()
                                            adViewModel.loadRestrictedPhoneAd() // 🔹 recargar para próxima vez
                                            errorText = null
                                            onConfirmEdit(updatedBlock)
                                        }

                                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                            errorText = null
                                            onConfirmEdit(updatedBlock)
                                        }
                                    }
                                    ad.show(activity)
                                } else {
                                    errorText = null
                                    onConfirmEdit(updatedBlock)
                                }
                                        onDismiss()
                            }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A4F8D)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Guardar", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Botón Eliminar centrado
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Eliminar")
                }
            }
        }
    }

    // Confirmación de eliminación
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminar restricción") },
            text = { Text("¿Estás seguro que quieres eliminar esta restricción?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(block)
                    showDeleteConfirm = false
                    onDismiss()
                }) {
                    Text("Confirmar", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}


fun formatDays(days: Set<String>): String {
    val week = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val daysLower = days.map { it.lowercase() }

    val selectedIndices = week.mapIndexedNotNull { index, day ->
        if (daysLower.contains(day.lowercase())) index else null
    }.sorted()

    if (selectedIndices.size == 7) return "Todos los días de la semana"

    val ranges = mutableListOf<String>()
    var start = selectedIndices.firstOrNull() ?: return ""
    var prev = start

    for (i in 1 until selectedIndices.size) {
        val current = selectedIndices[i]
        if (current != prev + 1) {
            ranges.add(formatRange(week, start, prev))
            start = current
        }
        prev = current
    }
    ranges.add(formatRange(week, start, prev))

    return ranges.joinToString(", ")
}

private fun formatRange(week: List<String>, start: Int, end: Int): String {
    return if (start == end) translateDayToSpanish(week[start])
    else "${translateDayToSpanish(week[start])} a ${translateDayToSpanish(week[end])}"
}

fun translateDayToSpanish(day: String): String {
    return when (day.lowercase()) {
        "monday" -> "Lunes"
        "tuesday" -> "Martes"
        "wednesday" -> "Miércoles"
        "thursday" -> "Jueves"
        "friday" -> "Viernes"
        "saturday" -> "Sábado"
        "sunday" -> "Domingo"
        else -> day
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneScheduleDialog(
    onConfirm: (label: String, from: LocalTime, to: LocalTime, selectedDays: Set<DayOfWeek>) -> Unit,
    onDismiss: () -> Unit,
    adViewModel: AdViewModel
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf("") }
    var selectedDays by remember { mutableStateOf<Set<DayOfWeek>>(emptySet()) }
    var from by remember { mutableStateOf(LocalTime.of(21, 0)) }
    var to by remember { mutableStateOf(LocalTime.of(22, 0)) }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(2.dp)
        ) {
            Column(modifier = Modifier.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally){
                Spacer(modifier = Modifier.height(4.dp))
                Text("Nuevo bloqueo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(12.dp))

                Spacer(modifier = Modifier.width(4.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Etiqueta", color = Color.LightGray) },
                    placeholder = { Text("Ej: Estudio, Dormir", color = Color.Gray) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color(0xFF5A4F8D),
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.width(4.dp))

                Spacer(modifier = Modifier.height(16.dp))
                Text("Repetir en días", color = Color.LightGray, fontSize = 14.sp)

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DayOfWeek.values().forEach { day ->
                        val isSelected = selectedDays.contains(day)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedDays = if (isSelected) selectedDays - day else selectedDays + day },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, Locale("es", "ES")), color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF5A4F8D),
                                containerColor = Color.DarkGray,
                                labelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Horario", color = Color.LightGray, fontSize = 14.sp)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        TimePickerField("Desde", from) { from = it }
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        TimePickerField("Hasta", to) { to = it }
                    }
                }

                errorText?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = Color(0xFFFF6B6B), fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))
                // Botones Cancelar y Guardar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp), // Separación de los bordes
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            when {
                                selectedDays.isEmpty() -> errorText = "Selecciona al menos un día"
                                !from.isBefore(to) -> errorText =
                                    "La hora de inicio debe ser anterior a la de fin"

                                else -> {
                                    val activity = context as? Activity
                                    val ad = adViewModel.restrictedPhoneAd   // 🔹 Us

                                    if (ad != null && activity != null) {
                                        ad.fullScreenContentCallback =
                                            object : FullScreenContentCallback() {
                                                override fun onAdDismissedFullScreenContent() {
                                                    adViewModel.clearRestrictedPhoneAd()
                                                    adViewModel.loadRestrictedPhoneAd() // 🔹 recargar para próxima vez
                                                    errorText = null
                                                    onConfirm(label, from, to, selectedDays)
                                                }

                                                override fun onAdFailedToShowFullScreenContent(
                                                    adError: AdError
                                                ) {
                                                    errorText = null
                                                    onConfirm(label, from, to, selectedDays)
                                                }
                                            }
                                        ad.show(activity)
                                    } else {
                                        errorText = null
                                        onConfirm(label, from, to, selectedDays)
                                    }
                                    onDismiss()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A4F8D)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Guardar", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

suspend fun savePhoneBlock(userId: String, block: PhoneBlockRule) {
    val docRef = FirebaseFirestore.getInstance()
        .collection("users")
        .document(userId)
        .collection("phone_blocks")
        .document() // ID automático

    val blockWithId = block.copy(id = docRef.id)
    docRef.set(blockWithId).await()
}

suspend fun updatePhoneBlock(userId: String, block: PhoneBlockRule) {
    FirebaseFirestore.getInstance()
        .collection("users")
        .document(userId)
        .collection("phone_blocks")
        .document(block.id)
        .set(block)
        .await()

    FirebaseFirestore.getInstance()
        .collection("users")
        .document(userId)
        .collection("phone_blocks")
        .document(block.id)
        .update("isActive", block.isActive)
        .await()
}

// --- Funciones de conversión de horarios ---
fun convertToSpanish(localTime: String): String {
    val localZone = ZoneId.systemDefault()
    val spanishZone = ZoneId.of("Europe/Madrid")
    val local = LocalTime.parse(localTime).atDate(LocalDate.now()).atZone(localZone)
    return local.withZoneSameInstant(spanishZone).toLocalTime().toString()
}

fun convertFromSpanishToLocal(spanishTime: String): String {
    val localZone = ZoneId.systemDefault()
    val spanishZone = ZoneId.of("Europe/Madrid")
    val spanish = LocalTime.parse(spanishTime).atDate(LocalDate.now()).atZone(spanishZone)
    return spanish.withZoneSameInstant(localZone).toLocalTime().toString()
}

suspend fun deletePhoneBlock(userId: String, blockId: String) {
    FirebaseFirestore.getInstance()
        .collection("users")
        .document(userId)
        .collection("phone_blocks")
        .document(blockId)
        .delete()
        .await()
}

@Composable
fun AccessibilityPermissionDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit
) {
    if (!showDialog) return

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            backgroundColor = Color(0xFF1E1E1E)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Permiso requerido",
                    fontSize = 20.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = """
                        Esta app necesita el permiso de Accesibilidad para:
                        
                        • Bloquear el uso de otras apps según el horario o límite que configures.
                        • Bloquear el acceso completo al móvil durante un tiempo específico, como de 5 a 6 de la tarde, para que te concentres sin distracciones.
                        
                        Este permiso solo se usa para aplicar estas funciones, sin leer contraseñas, mensajes ni datos personales.
                        
                        Ruta: Ajustes > Accesibilidad > Aplicaciones instaladas > DetoxApp
                    """.trimIndent(),
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Color.White)
                    }
                    TextButton(onClick = onGoToSettings) {
                        Text("Ir a ajustes", color = Color.White)
                    }
                }
            }
        }
    }
}