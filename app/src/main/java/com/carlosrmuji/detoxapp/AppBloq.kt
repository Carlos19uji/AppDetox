package com.carlosrmuji.detoxapp

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Space
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun AppBloqScreen(navController: NavController) {
    val context = LocalContext.current
    var bloquedApps by remember { mutableStateOf(listOf<BlockedApp>()) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showScheduledialog by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var selectedRestrictions by remember { mutableStateOf<List<AppRestriction>>(emptyList()) }
    var restrictionEditError by remember { mutableStateOf<String?>(null) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }

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

            // Lista o mensaje vacío
            if (bloquedApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aún no se han aplicado restricciones a ninguna aplicación.",
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(bloquedApps) { blockedApp ->
                        val isRestrictedNow = isRestrictedNow(blockedApp.restrictions)
                        BlockedAppRow(blockedApp, isRestrictedNow) {
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
                                restrictionEditError = "No puedes editar esta aplicación durante su horario de restricción. Espera a que finalice."
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
                    .padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                        top = 4.dp
                    )
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
                    showAppPicker = false
                    showScheduledialog = true
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
                    val existingRestrictions = bloquedApps
                        .find { it.app.packageName == selectedApp!!.packageName }
                        ?.restrictions
                        ?.filterNot { restriction ->
                            restriction.days.intersect(newRestriction.days).isNotEmpty()
                        } ?: emptyList()

                    val updatedRestrictions = existingRestrictions + newRestriction

                    val newBlockedApp = BlockedApp(selectedApp!!, updatedRestrictions)

                    bloquedApps = bloquedApps
                        .filterNot { it.app.packageName == selectedApp!!.packageName }
                        .plus(newBlockedApp)

                    saveBlockedAppToFirestore(userId, newBlockedApp)

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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DayOfWeek.values().forEach { day ->
                        val selected = selectedDays.contains(day)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedDays = if (selected) selectedDays - day else selectedDays + day
                            },
                            label = { Text(day.name.take(3), color = Color.White) },
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

fun saveBlockedAppToFirestore(userId: String, blockedApp: BlockedApp) {
    val db = Firebase.firestore

    // Agrupa por día y conserva solo la última restricción para cada día
    val dayToTimes = mutableMapOf<DayOfWeek, Pair<String, String>>()
    blockedApp.restrictions.forEach { restriction ->
        restriction.days.forEach { day ->
            dayToTimes[day] = restriction.from.toString() to restriction.to.toString()
        }
    }

    val restrictionData = dayToTimes.map { (day, times) ->
        mapOf(
            "day" to day.name,
            "from" to times.first,
            "to" to times.second
        )
    }

    val appData = mapOf(
        "appName"       to blockedApp.app.appName,
        "packageName"   to blockedApp.app.packageName,
        "restrictions"  to restrictionData
    )

    db.collection("users")
        .document(userId)
        .collection("restrictions")
        .document(blockedApp.app.packageName)
        .set(appData)  // sobrescribe por completo
        .addOnSuccessListener {
            Log.d("Firestore", "Restricciones actualizadas para ${blockedApp.app.packageName}")
        }
        .addOnFailureListener { e ->
            Log.e("Firestore", "Error guardando restricciones", e)
        }
}

fun loadBlockedAppsFromFirestore(
    userId: String,
    onResult: (List<BlockedApp>) -> Unit,
    onError: (Exception) -> Unit
) {
    val db = Firebase.firestore

    db.collection("users")
        .document(userId)
        .collection("restrictions")
        .get()
        .addOnSuccessListener { documents ->
            val blockedApps = documents.mapNotNull { doc ->
                val appName = doc.getString("appName") ?: return@mapNotNull null
                val packageName = doc.getString("packageName") ?: return@mapNotNull null
                val restrictionsData = doc.get("restrictions") as? List<Map<String, String>> ?: emptyList()

                val restrictions = restrictionsData.mapNotNull { map ->
                    val dayName = map["day"] ?: return@mapNotNull null
                    val fromStr = map["from"] ?: return@mapNotNull null
                    val toStr = map["to"] ?: return@mapNotNull null

                    try {
                        val day = DayOfWeek.valueOf(dayName)
                        val from = LocalTime.parse(fromStr)
                        val to = LocalTime.parse(toStr)
                        AppRestriction(setOf(day), from, to)
                    } catch (e: Exception) {
                        null
                    }
                }

                // Crear InstalledAppInfo sin icono (por ahora)
                val appInfo = InstalledAppInfo(packageName, appName, icon = null)

                BlockedApp(appInfo, restrictions)
            }
            onResult(blockedApps)
        }
        .addOnFailureListener { e ->
            onError(e)
        }
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

suspend fun loadBlockedAppsFromFirestoreSuspend(userId: String): List<BlockedApp> {
    val db = Firebase.firestore

    val documents = db.collection("users")
        .document(userId)
        .collection("restrictions")
        .get()
        .await()

    return documents.mapNotNull { doc ->
        val appName = doc.getString("appName") ?: return@mapNotNull null
        val packageName = doc.getString("packageName") ?: return@mapNotNull null
        val restrictionsData = doc.get("restrictions") as? List<Map<String, String>> ?: emptyList()

        val restrictions = restrictionsData.mapNotNull { map ->
            val dayName = map["day"] ?: return@mapNotNull null
            val fromStr = map["from"] ?: return@mapNotNull null
            val toStr = map["to"] ?: return@mapNotNull null

            try {
                val day = DayOfWeek.valueOf(dayName)
                val from = LocalTime.parse(fromStr)
                val to = LocalTime.parse(toStr)
                AppRestriction(setOf(day), from, to)
            } catch (e: Exception) {
                null
            }
        }

        val appInfo = InstalledAppInfo(packageName, appName, icon = null)
        BlockedApp(appInfo, restrictions)
    }
}