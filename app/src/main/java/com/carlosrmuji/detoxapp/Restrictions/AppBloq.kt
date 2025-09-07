package com.carlosrmuji.detoxapp.Restrictions

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import com.carlosrmuji.detoxapp.Billing.AdViewModel
import com.carlosrmuji.detoxapp.AppRestriction
import com.carlosrmuji.detoxapp.BlockedApp
import com.carlosrmuji.detoxapp.InstalledAppInfo
import com.carlosrmuji.detoxapp.Restrictions.RestrictionChecker.getUsageTodayFromSystem
import com.carlosrmuji.detoxapp.Screen
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AppBloqScreen(navController: NavController, adViewModel: AdViewModel) {
    val context = LocalContext.current
    var bloquedApps by remember { mutableStateOf(listOf<BlockedApp>()) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showScheduledialog by remember { mutableStateOf(false) }
    var showUsageLimitDialog by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var selectedRestrictions by remember { mutableStateOf<List<AppRestriction>>(emptyList()) }
    var restrictionEditError by remember { mutableStateOf<String?>(null) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var selectedDaysForLimit by remember { mutableStateOf<Set<DayOfWeek>>(emptySet()) }
    var selectedLimit by remember { mutableStateOf<Duration?>(null) }
    var userPlan by remember { mutableStateOf("base_plan") }
    var showLimitMessage by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var appPickerApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }

    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    val userId = getUserId()

    // Cargar apps instaladas
    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { appInfo ->
                    InstalledAppInfo(
                        packageName = appInfo.packageName,
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        icon = appInfo.loadIcon(pm).toBitmap().asImageBitmap()
                    )
                }
                .sortedBy { it.appName.lowercase() }
        }
    }

    // Cargar plan y apps bloqueadas
    LaunchedEffect(userId, installedApps) {
        if (installedApps.isEmpty()) return@LaunchedEffect
        try {
            val planDeferred = async { loadUserPlan(userId) }
            val appsDeferred = async { loadBlockedAppsFromFirestoreSuspend(userId) }

            val plan = planDeferred.await()
            val loadedApps = appsDeferred.await()

            userPlan = plan
            bloquedApps = enforcePlanLimit(loadedApps, plan, userId).mapNotNull { blockedApp ->
                findInstalledAppByPackage(installedApps, blockedApp.app.packageName)
                    ?.let { blockedApp.copy(app = it) }
            }
        } catch (e: Exception) {
            Log.e("AppBloqScreen", "Error cargando apps o plan", e)
            Toast.makeText(context, "Error cargando restricciones: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // 🔥 efecto para ocultar automáticamente el mensaje de límite
    LaunchedEffect(showLimitMessage) {
        if (showLimitMessage) {
            delay(5000) // 5 segundos
            showLimitMessage = false
        }
    }

// 🔥 efecto para ocultar automáticamente el mensaje de error de edición
    LaunchedEffect(restrictionEditError) {
        if (restrictionEditError != null) {
            delay(5000) // 5 segundos
            restrictionEditError = null
        }
    }

    // Contar apps únicas para límite
    val uniqueBlockedAppsCount = bloquedApps.map { it.app.packageName }.distinct().size
    val limitReached = when (userPlan) {
        "base_plan" -> uniqueBlockedAppsCount >= 2
        "plus_plan" -> uniqueBlockedAppsCount >= 5
        "premium_plan" -> false
        else -> true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        if (installedApps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
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

                // Tabs
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

                Spacer(modifier = Modifier.height(16.dp))

                val filteredApps = if (selectedTab == 0) {
                    bloquedApps.filter { it.restrictions.isNotEmpty() }
                } else {
                    bloquedApps.filter { it.limitUsageByDay.isNotEmpty() }
                }

                Spacer(modifier = Modifier.height(16.dp))

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
                        items(
                            items = filteredApps,
                            key = { it.app.packageName } // 👈 clave única por app
                        ) { blockedApp ->
                            val iconBitmap by produceState<ImageBitmap?>(initialValue = blockedApp.app.icon, key1 = blockedApp.app.packageName) {
                                if (value == null) {
                                    val pm = context.packageManager
                                    val info = pm.getApplicationInfo(blockedApp.app.packageName, 0)
                                    value = info.loadIcon(pm).toBitmap().asImageBitmap()
                                }
                            }

                            val appWithIcon = blockedApp.copy(app = blockedApp.app.copy(icon = iconBitmap))

                            val isRestrictedNow = if (selectedTab == 0) {
                                isRestrictedNow(blockedApp.restrictions)
                            } else {
                                isUsageLimitRestrictedNow(
                                    context,
                                    blockedApp.app.packageName,
                                    blockedApp.limitUsageByDay
                                )
                            }

                            BlockedAppRow(appWithIcon, isRestrictedNow) {
                                if (!isRestrictedNow) {
                                    if (isAccessibilityServiceEnabled(context, UnifiedBlockerService::class.java)) {
                                        selectedApp = blockedApp.app
                                        if (selectedTab == 0) {
                                            selectedRestrictions = blockedApp.restrictions
                                            showScheduledialog = true
                                        } else {
                                            val existingLimits = blockedApp.limitUsageByDay
                                            selectedDaysForLimit = existingLimits.keys
                                            selectedLimit = existingLimits.entries.firstOrNull()?.value
                                            showUsageLimitDialog = true
                                        }
                                        restrictionEditError = null
                                    } else {
                                        showAccessibilityDialog = true
                                    }
                                } else {
                                    restrictionEditError =
                                        "No puedes editar esta aplicación durante su horario de restricción."
                                }
                            }
                        }
                    }
                }
            }

            // ✅ Mensajes justo encima del botón
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            ) {
                if (showLimitMessage) {
                    Text(
                        text = "Has alcanzado el límite de apps a restringir para tu plan.\nSi quieres restringir más apps, mejora tu plan.",
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

                restrictionEditError?.let { errorMsg ->
                    Text(
                        text = errorMsg,
                        color = Color(0xFFFF4C4C),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        // ✅ Primero comprobar accesibilidad
                        if (!isAccessibilityServiceEnabled(context, UnifiedBlockerService::class.java)) {
                            showAccessibilityDialog = true
                        } else {
                            // ✅ Luego comprobar límite del plan
                            if (limitReached) {
                                showLimitMessage = true
                            } else {
                                // ✅ Filtrar apps disponibles solo si no se ha alcanzado el límite
                                val blockedPackageNames = if (selectedTab == 0) {
                                    bloquedApps.filter { it.restrictions.isNotEmpty() }
                                        .map { it.app.packageName }
                                } else {
                                    bloquedApps.filter { it.limitUsageByDay.isNotEmpty() }
                                        .map { it.app.packageName }
                                }

                                val availableApps = installedApps.filter { it.packageName !in blockedPackageNames }

                                if (availableApps.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        "No hay apps disponibles para agregar",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    showAppPicker = true
                                    appPickerApps = availableApps
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A4F8D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restringir app", color = Color.White)
                }
            }
        }

        // App picker
        if (showAppPicker) {
            AppPickerDialog(
                apps = appPickerApps,
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
                },
                adViewModel
            )
        }

        // Usage limit dialog
        if (showUsageLimitDialog && selectedApp != null) {
            UsageLimitDialog(
                appName = selectedApp!!.appName,
                appPackage = selectedApp!!.packageName,
                navController = navController,
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
                        currentData["unlock_day_paid"] = false

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
                },
                adViewModel
            )
        }

        // 🔔 Dialogo de acceso a accesibilidad
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
    onDismiss: () -> Unit,
    adViewModel: AdViewModel
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
                                val activity = context as? Activity
                                val ad = adViewModel.restrictAppAd   // 🔹 Usamos el anuncio correcto

                                if (ad != null && activity != null) {
                                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                                        override fun onAdDismissedFullScreenContent() {
                                            adViewModel.clearRestrictionAppAd()
                                            adViewModel.loadRestrictAppsAd() // 🔹 recargar para próxima vez
                                            errorText = null
                                            onConfirm(AppRestriction(selectedDays, from, to))
                                        }

                                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                            errorText = null
                                            onConfirm(AppRestriction(selectedDays, from, to))
                                        }
                                    }
                                    ad.show(activity)
                                } else {
                                    errorText = null
                                    onConfirm(AppRestriction(selectedDays, from, to))
                                }
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
    appPackage: String,
    navController: NavController,
    initialLimit: Duration? = null,
    initialDays: Set<DayOfWeek> = emptySet(),
    onConfirm: (Duration, Set<DayOfWeek>) -> Unit,
    onDismiss: () -> Unit,
    adViewModel: AdViewModel
) {
    val context = LocalContext.current
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
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
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

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
                modifier = Modifier.padding(4.dp),
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

                Column(modifier = Modifier.fillMaxWidth()) {
                    // Fila superior: Cancelar y Añadir
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF5A4F8D)),
                            modifier = Modifier.weight(0.625f)
                        ) {
                            Text("Cancelar", color = Color.White)
                        }

                        Spacer(modifier = Modifier.width(4.dp)) // pequeño espacio entre los botones

                        Button(
                            onClick = {
                                val h = hoursText.toIntOrNull() ?: 0
                                val m = minutesText.toIntOrNull() ?: 0
                                if (selectedDays.isEmpty()) {
                                    errorText = "Selecciona al menos un día"
                                } else if (h == 0 && m == 0) {
                                    errorText = "La duración debe ser mayor a 0"
                                } else {
                                    val activity = context as? Activity
                                    val ad = adViewModel.restrictAppAd
                                    if (ad != null && activity != null) {
                                        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                                            override fun onAdDismissedFullScreenContent() {
                                                adViewModel.clearRestrictionAppAd()
                                                adViewModel.loadRestrictAppsAd()
                                                errorText = null
                                                onConfirm(Duration.ofHours(h.toLong()).plusMinutes(m.toLong()), selectedDays)
                                            }

                                            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                                errorText = null
                                                onConfirm(Duration.ofHours(h.toLong()).plusMinutes(m.toLong()), selectedDays)
                                            }
                                        }
                                        ad.show(activity)
                                    } else {
                                        errorText = null
                                        onConfirm(Duration.ofHours(h.toLong()).plusMinutes(m.toLong()), selectedDays)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A4F8D)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Añadir restricción", color = Color.White, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp)) // separación entre filas

                    // Fila inferior: Eliminar centrado
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteConfirmDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
                        ) {
                            Text("Eliminar restricción", color = Color.Gray)
                        }
                    }
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

suspend fun loadUserPlan(userId: String): String = withContext(Dispatchers.IO) {
    val doc = FirebaseFirestore.getInstance()
        .collection("users")
        .document(userId)
        .collection("plan")
        .document("plan")
        .get()
        .await()

    doc.getString("plan") ?: "base_plan"
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

