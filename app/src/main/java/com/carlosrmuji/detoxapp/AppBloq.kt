package com.carlosrmuji.detoxapp

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Space
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import com.carlosrmuji.detoxapp.RestrictionChecker.getUsageTodayFromSystem
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AppBloqScreen(navController: NavController) {
    val context = LocalContext.current
    var bloquedApps by remember { mutableStateOf(listOf<BlockedApp>()) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showScheduledialog by remember { mutableStateOf(false) }
    var showUsageLimitDialog by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var selectedRestrictions by remember { mutableStateOf<List<AppRestriction>>(emptyList()) }
    var restrictionEditError by remember { mutableStateOf<String?>(null) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }

    var selectedDaysForLimit by remember { mutableStateOf<Set<DayOfWeek>>(emptySet()) } // Nuevo
    var selectedLimit by remember { mutableStateOf<Duration?>(null) } // Nuevo

    val installedApps = remember { getInstalledApps(context) }
    val userId = getUserId()

    var userPlan by remember { mutableStateOf("base_plan") }
    var showLimitMessage by remember { mutableStateOf(false) }

    val limitReached = when (userPlan) {
        "base_plan" -> bloquedApps.size >= 2
        "plus_plan" -> bloquedApps.size >= 5
        "premium_plan" -> false
        else -> true
    }

    var selectedTab by remember { mutableStateOf(0) }


    LaunchedEffect(userId) {
        loadBlockedAppsFromFirestore(
            userId,
            onResult = { loadedApps ->
                bloquedApps = loadedApps.map { blockedApp ->
                    findInstalledAppByPackage(installedApps, blockedApp.app.packageName)
                        ?.let { blockedApp.copy(app = it) }
                        ?: blockedApp
                }
            },
            onError = { e ->
                Toast.makeText(context, "Error cargando restricciones: ${e.message}", Toast.LENGTH_LONG).show()
            }
        )
    }

    LaunchedEffect(userId) {
        try {
            // 1. Obtener el plan del usuario
            val planSnapshot = Firebase.firestore
                .collection("users")
                .document(userId)
                .collection("plan")
                .document("plan")
                .get()
                .await()

            userPlan = planSnapshot.getString("plan") ?: "base_plan"

            // 2. Obtener las apps bloqueadas
            val loadedApps = loadBlockedAppsFromFirestoreSuspend(userId)

            // 3. Aplicar el límite del plan
            val filteredApps = enforcePlanLimit(loadedApps, userPlan, userId)

            // 4. Mapear con apps instaladas
            bloquedApps = filteredApps.mapNotNull { blockedApp ->
                findInstalledAppByPackage(installedApps, blockedApp.app.packageName)
                    ?.let { blockedApp.copy(app = it) }
            }

        } catch (e: Exception) {
            Log.e("AppBloqScreen", "Error cargando apps o plan", e)
            Toast.makeText(context, "Error cargando restricciones: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Título
            Text(
                text = "Apps restringidas",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .padding(4.dp)
                    .background(Color.DarkGray, RoundedCornerShape(30.dp))
                    .padding(2.dp)
                    .fillMaxWidth()
            ) {
                listOf("Horario", "Límite de uso").forEachIndexed { index, option ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (selectedTab == index) Color.Gray else Color.DarkGray,
                                RoundedCornerShape(30.dp)
                            )
                            .clickable { selectedTab = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(option, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            val filteredApps = if (selectedTab == 0) {
                bloquedApps.filter { it.restrictions.isNotEmpty() }
            } else {
                bloquedApps.filter { it.limitUsageByDay.isNotEmpty() } // ✅ Cambiado
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lista o mensaje vacío
            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedTab == 0)
                            "Aún no se han aplicado restricciones por horario."
                        else
                            "Aún no se han aplicado restricciones por límite de uso.",
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filteredApps) { blockedApp ->
                        val isRestrictedNow = if (selectedTab == 0) {
                            isRestrictedNow(blockedApp.restrictions)
                        } else {
                            isUsageLimitRestrictedNow(context, blockedApp.app.packageName, blockedApp.limitUsageByDay)
                        }
                        BlockedAppRow(blockedApp, isRestrictedNow) {
                            if (selectedTab == 0) {
                                if (!isRestrictedNow) {
                                    if (isAccessibilityServiceEnabled(context, AppBlockerService::class.java)) {
                                        selectedApp = blockedApp.app
                                        selectedRestrictions = blockedApp.restrictions
                                        showScheduledialog = true
                                        restrictionEditError = null
                                    } else {
                                        showAccessibilityDialog = true
                                    }
                                } else {
                                    restrictionEditError = "No puedes editar esta aplicación durante su horario de restricción."
                                }
                            } else {
                                if (!isRestrictedNow) {
                                    // Límite de uso
                                    if (isAccessibilityServiceEnabled(context, AppBlockerService::class.java)) {
                                        selectedApp = blockedApp.app

                                        // Obtener los límites de uso reales de Firestore (ya cargados en memoria)
                                        val existingLimits = blockedApp.limitUsageByDay

                                        if (existingLimits.isNotEmpty()) {
                                            selectedDaysForLimit = existingLimits.keys
                                            selectedLimit = existingLimits.entries.first().value
                                        } else {
                                            selectedDaysForLimit = emptySet()
                                            selectedLimit = null
                                        }

                                        showUsageLimitDialog = true
                                    } else {
                                        showAccessibilityDialog = true
                                    }
                                } else{
                                    restrictionEditError = "No puedes editar esta aplicación durante su horario de restricción."
                                }
                            }
                        }
                    }
                }
            }

            // ✅ Mostrar mensaje de límite alcanzado
            if (showLimitMessage) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Has alcanzado el límite de apps a restringir para tu plan.\nSi quieres restringir más apps, mejora tu plan.",
                        color = Color(0xFFFF4C4C), // rojo más intenso
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { navController.navigate(Screen.PlansScreen.route) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A4F8D)) // mismo que "Restringir app"
                    ) {
                        Text("Mejorar plan", color = Color.White)
                    }
                }
            }


            // ✅ Estilo unificado para el mensaje de error de edición
            restrictionEditError?.let { errorMsg ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = errorMsg,
                        color = Color(0xFFFF4C4C), // mismo rojo intenso
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botón
            Button(
                onClick = {
                    if (limitReached) {
                        showLimitMessage = true
                    } else {
                        showLimitMessage = false
                        if (isAccessibilityServiceEnabled(context, AppBlockerService::class.java)) {
                            showAppPicker = true
                        } else {
                            showAccessibilityDialog = true
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A4F8D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            ) {
                Text("Restringir app", color = Color.White)
            }
        }

        // App picker
        if (showAppPicker) {
            AppPickerDialog(
                apps = installedApps,
                onAppSelected = { app ->
                    selectedApp = app
                    selectedRestrictions = emptyList()
                    selectedDaysForLimit = emptySet()
                    selectedLimit = null
                    showAppPicker = false
                    if (selectedTab == 0) showScheduledialog = true else showUsageLimitDialog = true
                },
                onDismiss = { showAppPicker = false }
            )
        }

        // Schedule dialog
        if (showScheduledialog && selectedApp != null) {
            ScheduleDialog(
                appName = selectedApp!!.appName,
                appPackage = selectedApp!!.packageName,
                navController = navController,
                initialRestrictions = selectedRestrictions,
                onConfirm = { newRestriction ->
                    // Buscamos la app existente en memoria
                    val existingBlockedApp = bloquedApps.find { it.app.packageName == selectedApp!!.packageName }

                    // Filtramos restricciones que no se solapen con la nueva
                    val existingRestrictions = existingBlockedApp?.restrictions
                        ?.filterNot { restriction ->
                            restriction.days.intersect(newRestriction.days).isNotEmpty()
                        } ?: emptyList()

                    val updatedRestrictions = existingRestrictions + newRestriction

                    // Creamos la nueva instancia de BlockedApp manteniendo los límites de uso existentes
                    val newBlockedApp = BlockedApp(
                        app = selectedApp!!,
                        restrictions = updatedRestrictions,
                        limitUsageByDay = existingBlockedApp?.limitUsageByDay ?: emptyMap()
                    )

                    // Actualizamos la lista en memoria
                    bloquedApps = bloquedApps
                        .filterNot { it.app.packageName == selectedApp!!.packageName }
                        .plus(newBlockedApp)

                    // Guardamos en Firestore
                    saveBlockedAppToFirestore(userId, newBlockedApp)

                    // Limpiamos estado de UI
                    showScheduledialog = false
                    selectedRestrictions = emptyList()
                    selectedApp = null
                    restrictionEditError = null
                },
                onDismiss = {
                    showScheduledialog = false
                    selectedRestrictions = emptyList()
                    selectedApp = null
                }
            )
        }

        // Usage limit dialog
        if (showUsageLimitDialog && selectedApp != null) {
            UsageLimitDialog(
                appName = selectedApp!!.appName,
                initialLimit = selectedLimit,
                initialDays = selectedDaysForLimit,
                onConfirm = { limitDuration, days ->

                    // Crear o actualizar el mapa de límites por día
                    val newLimits: Map<DayOfWeek, Duration> = days.associateWith { limitDuration }

                    // Buscar la app existente en memoria
                    val existingBlockedApp = bloquedApps.find { it.app.packageName == selectedApp!!.packageName }

                    // Crear la nueva instancia sin tocar las restricciones originales
                    val updatedBlockedApp = BlockedApp(
                        app = selectedApp!!,
                        restrictions = existingBlockedApp?.restrictions ?: emptyList(), // mantener las restricciones reales
                        limitUsageByDay = newLimits
                    )

                    // Actualizar la lista de apps bloqueadas en memoria
                    bloquedApps = bloquedApps
                        .filterNot { it.app.packageName == selectedApp!!.packageName }
                        .plus(updatedBlockedApp)

                    // Guardar en Firestore
                    val mapToStore: Map<String, Long> = updatedBlockedApp.limitUsageByDay
                        .mapKeys { it.key.name }
                        .mapValues { (_, duration) -> duration.toMinutes() }

                    val docRef = FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(userId)
                        .collection("restrictions")
                        .document(selectedApp!!.packageName)

                    docRef.get().addOnSuccessListener { snapshot ->
                        val currentData = snapshot.data?.toMutableMap() ?: mutableMapOf()

                        currentData["appName"] = selectedApp!!.appName
                        currentData["packageName"] = selectedApp!!.packageName
                        currentData["limit_usage"] = mapToStore

                        docRef.set(currentData)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Límite de uso guardado", Toast.LENGTH_SHORT).show()
                                showUsageLimitDialog = false
                                selectedApp = null
                                selectedLimit = null
                                selectedDaysForLimit = emptySet()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Error guardando límite: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }
                },
                onDismiss = {
                    showUsageLimitDialog = false
                    selectedApp = null
                    selectedLimit = null
                    selectedDaysForLimit = emptySet()
                }
            )
        }

        // 🔔 Dialogo de acceso a accesibilidad
        if (showAccessibilityDialog) {
            Dialog(onDismissRequest = { showAccessibilityDialog = false }) {
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
                            text = "Para bloquear aplicaciones, debes activar el servicio de accesibilidad.\n\n" +
                                    "Ruta: Ajustes > Accesibilidad > Aplicaciones instaladas > DetoxApp",
                            color = Color.LightGray,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = { showAccessibilityDialog = false }) {
                                Text("Cancelar", color = Color.White)
                            }
                            TextButton(onClick = {
                                openAccessibilitySettings(context)
                                showAccessibilityDialog = false
                            }) {
                                Text("Ir a ajustes", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getUserId(): String {
    return Firebase.auth.currentUser?.uid
        ?: throw IllegalStateException("Usuario no autenticado")
}


@Composable
fun BlockedAppRow(
    app: BlockedApp,
    isRestrictedNow: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        backgroundColor = if (isRestrictedNow) Color.Gray else Color.DarkGray
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            app.app.icon?.let {
                Image(
                    bitmap = it,
                    contentDescription = app.app.appName,
                    modifier = Modifier.size(40.dp)
                )
            } ?: Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.LightGray, shape = CircleShape)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(app.app.appName, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    if (isRestrictedNow) "En horario de restricción" else "Editable",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
        }
    }
}


@Composable
fun AppPickerDialog(
    apps: List<InstalledAppInfo>,
    onAppSelected: (InstalledAppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF2C2C2C),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f) // Altura del 70% de la pantalla
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Selecciona una app",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn {
                    items(apps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAppSelected(app) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            app.icon?.let {
                                Image(
                                    bitmap = it,
                                    contentDescription = app.appName,
                                    modifier = Modifier.size(40.dp)
                                )
                            } ?: Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.Gray, shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(app.appName, color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A4F8D)),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancelar", color = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleDialog(
    appName: String,
    appPackage: String,
    navController: NavController,
    initialRestrictions: List<AppRestriction>,
    onConfirm: (AppRestriction) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val combinedDays = initialRestrictions.flatMap { it.days }.toSet()
    val initialFrom = initialRestrictions.firstOrNull()?.from ?: LocalTime.of(18, 0)
    val initialTo = initialRestrictions.firstOrNull()?.to ?: LocalTime.of(22, 0)

    var selectedDays by remember { mutableStateOf(combinedDays) }
    var from by remember { mutableStateOf(initialFrom) }
    var to by remember { mutableStateOf(initialTo) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Restricción para $appName", color = Color.White, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(8.dp))
                Text("Selecciona días", color = Color.LightGray)

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DayOfWeek.values().forEach { day ->
                        val selected = selectedDays.contains(day)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedDays = if (selected) selectedDays - day else selectedDays + day
                            },
                            label = {
                                Text(
                                    day.getDisplayName(TextStyle.SHORT, Locale("es", "ES")),
                                    color = Color.White
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF5A4F8D),
                                containerColor = Color.DarkGray,
                                labelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Horario", color = Color.LightGray)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimePickerField("Desde", from) { from = it }
                    TimePickerField("Hasta", to) { to = it }
                }

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorText!!,
                        color = Color(0xFFFF6B6B),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF5A4F8D))
                    ) {
                        Text("Cancelar", color = Color.White)
                    }

                    Button(
                        onClick = {
                            if (selectedDays.isEmpty()) {
                                errorText = "Selecciona al menos un día"
                            } else if (!from.isBefore(to)) {
                                errorText = "La hora de inicio debe ser anterior a la hora de fin"
                            } else {
                                errorText = null
                                onConfirm(AppRestriction(selectedDays, from, to))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A4F8D))
                    ) {
                        Text("Añadir restricción", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
                    enabled = !isDeleting
                ) {
                    Text("Eliminar restricciones", color = Color.Gray)
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirmDialog = false },
            title = {
                Text("¿Eliminar restricciones?", color = Color.White)
            },
            text = {
                Text(
                    "¿Estás seguro de que quieres cancelar las restricciones para $appName?",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleting = true
                        val docRef = FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(userId)
                            .collection("restrictions")
                            .document(appPackage)

                        docRef.delete()
                            .addOnSuccessListener {
                                Toast.makeText(context, "Restricciones eliminadas", Toast.LENGTH_SHORT).show()
                                isDeleting = false
                                showDeleteConfirmDialog = false
                                onDismiss()
                                navController.navigate(Screen.AppBloq.route)
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Error eliminando restricciones: ${e.message}", Toast.LENGTH_LONG).show()
                                isDeleting = false
                            }
                    },
                    enabled = !isDeleting
                ) {
                    Text("Confirmar", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!isDeleting) showDeleteConfirmDialog = false },
                    enabled = !isDeleting
                ) {
                    Text("Cancelar", color = Color.White)
                }
            },
            backgroundColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(12.dp)
        )
    }
}


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UsageLimitDialog(
    appName: String,
    initialLimit: Duration? = null,
    initialDays: Set<DayOfWeek> = emptySet(),
    onConfirm: (Duration, Set<DayOfWeek>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDays by remember { mutableStateOf(initialDays) }

    // Usamos strings para manejar mejor la entrada
    var hoursText by remember {
        mutableStateOf(
            initialLimit?.toHours()?.toInt()?.coerceIn(0, 23)?.toString() ?: "1"
        )
    }
    var minutesText by remember {
        mutableStateOf(
            initialLimit?.let { ((it.toMinutes() % 60).toInt()).coerceIn(0, 59) }?.toString() ?: "0"
        )
    }

    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Limitar uso de $appName",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("Selecciona días", color = Color.LightGray)

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DayOfWeek.values().forEach { day ->
                        val selected = selectedDays.contains(day)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedDays = if (selected) selectedDays - day else selectedDays + day
                            },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, Locale("es", "ES")), color = Color.White) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF5A4F8D),
                                containerColor = Color.DarkGray,
                                labelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text("Duración máxima de uso", color = Color.LightGray)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Horas
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Horas", color = Color.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val h = hoursText.toIntOrNull() ?: 0
                                    if (h > 0) hoursText = (h - 1).toString()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Menos", tint = Color.White, modifier = Modifier.size(18.dp))
                            }

                            TextField(
                                value = hoursText,
                                onValueChange = { value ->
                                    // Permite borrar y escribir libremente, max 2 dígitos
                                    val clean = value.filter { it.isDigit() }.take(2)
                                    hoursText = if (clean.isEmpty()) "" else clean.toInt().coerceIn(0, 23).toString()
                                },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp
                                ),
                                colors = TextFieldDefaults.textFieldColors(
                                    containerColor = Color.DarkGray,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.width(50.dp) // más pequeño
                            )

                            IconButton(
                                onClick = {
                                    val h = hoursText.toIntOrNull() ?: 0
                                    if (h < 23) hoursText = (h + 1).toString()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Más", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // Minutos
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Minutos", color = Color.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val m = minutesText.toIntOrNull() ?: 0
                                    if (m > 0) minutesText = (m - 1).toString()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Menos", tint = Color.White, modifier = Modifier.size(18.dp))
                            }

                            TextField(
                                value = minutesText,
                                onValueChange = { value ->
                                    val clean = value.filter { it.isDigit() }.take(2)
                                    minutesText = if (clean.isEmpty()) "" else clean.toInt().coerceIn(0, 59).toString()
                                },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp
                                ),
                                colors = TextFieldDefaults.textFieldColors(
                                    containerColor = Color.DarkGray,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.width(50.dp)
                            )

                            IconButton(
                                onClick = {
                                    val m = minutesText.toIntOrNull() ?: 0
                                    if (m < 59) minutesText = (m + 1).toString()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Más", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorText!!, color = Color(0xFFFF6B6B))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF5A4F8D))
                    ) {
                        Text("Cancelar", color = Color.White)
                    }

                    Button(
                        onClick = {
                            val h = hoursText.toIntOrNull() ?: 0
                            val m = minutesText.toIntOrNull() ?: 0
                            if (selectedDays.isEmpty()) {
                                errorText = "Selecciona al menos un día"
                            } else if (h == 0 && m == 0) {
                                errorText = "La duración debe ser mayor a 0"
                            } else {
                                errorText = null
                                onConfirm(Duration.ofHours(h.toLong()).plusMinutes(m.toLong()), selectedDays)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A4F8D))
                    ) {
                        Text("Añadir restrición", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun TimePickerField(label: String, time: LocalTime, onTimeSelected: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    OutlinedButton(onClick = {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                onTimeSelected(LocalTime.of(hour, minute))
            },
            time.hour, time.minute, true
        ).show()
    }) {
        Text("$label: ${time.format(timeFormatter)}", color = Color.White)
    }
}


fun isRestrictedNow(restrictions: List<AppRestriction>): Boolean {
    val now = LocalTime.now()
    val today = LocalDate.now().dayOfWeek
    return restrictions.any { today in it.days && now in it.from..it.to }
}

fun getInstalledApps(context: Context): List<InstalledAppInfo> {
    val pm = context.packageManager
    val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

    return apps
        .filter { appInfo ->
            // Opcional: filtrar solo apps de usuario (excluye apps del sistema)
            pm.getLaunchIntentForPackage(appInfo.packageName) != null
        }
        .map { appInfo ->
            val name = pm.getApplicationLabel(appInfo).toString()
            val iconBitmap = try {
                appInfo.loadIcon(pm).toBitmap().asImageBitmap()
            } catch (e: Exception) {
                null
            }
            InstalledAppInfo(
                packageName = appInfo.packageName,
                appName = name,
                icon = iconBitmap
            )
        }
        .sortedBy { it.appName.lowercase() }
}


fun findInstalledAppByPackage(
    installedApps: List<InstalledAppInfo>,
    packageName: String
): InstalledAppInfo? {
    return installedApps.find { it.packageName == packageName }
}

fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val serviceId = "${context.packageName}/${serviceClass.name}"
    return enabledServices.contains(serviceId)
}

fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(intent)
}

fun isUsageLimitRestrictedNow(
    context: Context,
    packageName: String,
    limitUsageByDay: Map<DayOfWeek, Duration>
): Boolean {
    val today = LocalDate.now().dayOfWeek
    val limit = limitUsageByDay[today] ?: return false
    val usedDuration = getUsageTodayFromSystem(context, packageName)
    return usedDuration >= limit
}

suspend fun enforcePlanLimit(
    apps: List<BlockedApp>,
    userPlan: String,
    userId: String
): List<BlockedApp> {
    val allowed = when (userPlan) {
        "base_plan" -> 2
        "plus_plan" -> 5
        "premium_plan" -> Int.MAX_VALUE
        else -> 2
    }

    if (apps.size <= allowed) return apps

    // Ordena alfabéticamente para eliminar las últimas (puedes usar otro criterio si prefieres)
    val sortedApps = apps.sortedBy { it.app.appName }
    val toKeep = sortedApps.take(allowed)
    val toRemove = sortedApps.drop(allowed)

    // Eliminar de Firestore
    val db = Firebase.firestore
    toRemove.forEach { blockedApp ->
        db.collection("users")
            .document(userId)
            .collection("restrictions")
            .document(blockedApp.app.packageName)
            .delete()
    }

    return toKeep
}