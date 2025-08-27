package com.carlosrmuji.detoxapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.TextView
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId


class AppBlockerService : AccessibilityService() {

    override fun onServiceConnected() {
        Log.d("AppBlockerService", "Servicio de accesibilidad conectado")
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        Log.d("AppBlockerService", "App activa: $packageName")

        if (RestrictionChecker.isAppBlockedNow(this, packageName)) {
            Log.d("AppBlockerService", "$packageName está bloqueada. Mostrando pantalla de bloqueo.")
            val intent = Intent(this, BlockedAppActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("BLOCKED_PACKAGE", packageName)
            }
            startActivity(intent)
        }
    }

    override fun onInterrupt() {
        Log.d("AppBlockerService", "Servicio interrumpido")
    }
}

class BlockedAppActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("BlockedAppActivity", "Activity de bloqueo iniciada")
        setContentView(R.layout.activity_blocked_app)

        val packageName = intent.getStringExtra("BLOCKED_PACKAGE") ?: "Aplicación"
        val appName = RestrictionChecker.getAppNameFromPackage(this, packageName)
        val nextAvailable = RestrictionChecker.getNextAvailableTime(this, packageName)

        val blockMessage = findViewById<TextView>(R.id.block_message)
        blockMessage.text = "No puedes usar $appName en este momento. \n\nEstará disponible a partir de las $nextAvailable."

        val appIconView = findViewById<ImageView>(R.id.app_icon)
        try {
            val appIcon = packageManager.getApplicationIcon(packageName)
            appIconView.setImageDrawable(appIcon)
        } catch (e: Exception) {
            Log.e("BlockedAppActivity", "No se pudo obtener el icono de la app: $packageName", e)
            appIconView.setImageResource(R.drawable.ic_launcher_foreground) // Asegúrate de tener este recurso
        }
    }

    override fun onBackPressed() {
        // Ignorar botón atrás para que el usuario no pueda salir
        Log.d("BlockedAppActivity", "Botón atrás presionado, ignorado")
    }

    override fun onUserLeaveHint() {
        // Volver a lanzar la pantalla de bloqueo si el usuario intenta irse
        val packageName = intent.getStringExtra("BLOCKED_PACKAGE") ?: return
        if (RestrictionChecker.isAppBlockedNow(this, packageName)) {
            startActivity(Intent(this, BlockedAppActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra("BLOCKED_PACKAGE", packageName)
            })
        }
        super.onUserLeaveHint()
    }
}

object RestrictionChecker {
    private var cachedBlockedApps: List<BlockedApp> = emptyList()

    fun isAppBlockedNow(context: Context, packageName: String): Boolean {
        loadIfNeeded(context)
        val blockedApp = cachedBlockedApps.find { it.app.packageName == packageName } ?: return false
        val now = LocalDateTime.now()

        // 1) Restricción por horarios
        if (blockedApp.restrictions.any { it.includes(now) }) return true

        // 2) Restricción por límite de uso por día
        val totalUsedToday = getUsageTodayFromSystem(context, packageName)
        blockedApp.limitUsageByDay[now.dayOfWeek]?.let { limit ->
            if (totalUsedToday >= limit) return true
        }

        return false
    }

    fun getNextAvailableTime(context: Context, packageName: String): String {
        loadIfNeeded(context)
        val now = LocalDateTime.now()
        val app = cachedBlockedApps.find { it.app.packageName == packageName } ?: return "más tarde"

        // Si está bloqueada por horario, devolvemos el final de la restricción activa
        val activeRestriction = app.restrictions
            .filter { includesNow(it, now) }
            .maxByOrNull { it.to.toSecondOfDay() }

        if (activeRestriction != null) {
            return activeRestriction.to.toString()
        }

        // Prioridad 2: límite de uso diario
        val limitForToday: Duration? = app.limitUsageByDay[now.dayOfWeek]
        if (limitForToday != null) {
            val usedToday = getUsageTodayFromSystem(context, packageName)
            if (usedToday >= limitForToday) {
                return "00:00 (mañana)" // disponible al día siguiente
            }
        }

        return "más tarde"
    }

    fun getAppNameFromPackage(context: Context, packageName: String): String {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            "esta aplicación"
        }
    }

    private fun includesNow(restriction: AppRestriction, now: LocalDateTime): Boolean {
        val day = now.dayOfWeek
        val time = now.toLocalTime()
        return day in restriction.days && time >= restriction.from && time <= restriction.to
    }

    fun loadIfNeeded(context: Context) {
        runBlocking {
            val userId = getUserId()
            loadBlockedAppsFromFirestore(
                userId = userId,
                onResult = { cachedBlockedApps = it },
                onError = { cachedBlockedApps = emptyList() }
            )
        }
    }

    fun getUsageTodayFromSystem(context: Context, packageName: String): Duration {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()

        val startOfDay = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val stats: List<UsageStats> =
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)

        val usageForApp = stats.find { it.packageName == packageName }
        val totalTimeMs = usageForApp?.totalTimeInForeground ?: 0L

        Log.d("RestrictionChecker", "[$packageName] usado hoy: ${totalTimeMs / 1000}s")

        return Duration.ofMillis(totalTimeMs)
    }
}

fun saveBlockedAppToFirestore(userId: String, blockedApp: BlockedApp) {
    val db = Firebase.firestore
    val docRef = db.collection("users")
        .document(userId)
        .collection("restrictions")
        .document(blockedApp.app.packageName)

    docRef.get().addOnSuccessListener { snapshot ->
        val currentData = snapshot.data?.toMutableMap() ?: mutableMapOf()

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

        val updatedData = currentData.toMutableMap().apply {
            this["appName"] = blockedApp.app.appName
            this["packageName"] = blockedApp.app.packageName
            this["restrictions"] = restrictionData

            // Mantener limit_usage si ya existía o crear uno nuevo a partir de limitUsageByDay
            if (blockedApp.limitUsageByDay.isNotEmpty()) {
                val mapToStore: Map<String, Long> =
                    blockedApp.limitUsageByDay.mapKeys { it.key.name }
                        .mapValues { (_, duration) -> duration.toMinutes() }
                this["limit_usage"] = mapToStore
            } else if ((currentData["limit_usage"] as? Map<*, *>)?.isNotEmpty() == true) {
                    this["limit_usage"] = currentData["limit_usage"]
            }
        }

        docRef.set(updatedData)
            .addOnSuccessListener {
                Log.d("Firestore", "Restricciones actualizadas para ${blockedApp.app.packageName}")
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error guardando restricciones", e)
            }
    }.addOnFailureListener { e ->
        Log.e("Firestore", "Error obteniendo documento previo", e)
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

                val limitUsageByDay = mutableMapOf<DayOfWeek, Duration>()
                val limitUsageField = doc.get("limit_usage")
                if (limitUsageField is Map<*, *>) {
                    // estructura: { "MONDAY": 90, "WEDNESDAY": 120 }
                    limitUsageField.forEach { (k, v) ->
                        try {
                            val dayName = k as? String ?: return@forEach
                            val minutesNumber = when (v) {
                                is Number -> v.toLong()
                                is String -> v.toLongOrNull() ?: return@forEach
                                else -> return@forEach
                            }
                            val day = DayOfWeek.valueOf(dayName)
                            limitUsageByDay[day] = Duration.ofMinutes(minutesNumber)
                        } catch (_: Exception) { /* ignora entradas inválidas */ }
                    }
                } else if (limitUsageField is Number) {
                    // legacy: un único valor en minutos -> aplicamos a todos los días listados en restrictions
                    val minutes = limitUsageField.toLong()
                    // si hay restricciones con días, aplicar a esas; si no, aplicamos a todos los días
                    val targetDays = if (restrictions.isNotEmpty()) {
                        restrictions.flatMap { it.days }.toSet()
                    } else {
                        DayOfWeek.values().toSet()
                    }
                    targetDays.forEach { d -> limitUsageByDay[d] = Duration.ofMinutes(minutes) }
                }

                val appInfo = InstalledAppInfo(packageName, appName, icon = null)
                // legacy limitUsage como Duration si existe primera entrada (comodidad)
                val singleLimit = limitUsageByDay.values.firstOrNull()

                BlockedApp(
                    appInfo,
                    restrictions,
                    limitUsageByDay = limitUsageByDay
                )
            }
            onResult(blockedApps)
        }
        .addOnFailureListener { e ->
            onError(e)
        }
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

        val limitUsageByDay = mutableMapOf<DayOfWeek, Duration>()
        val limitUsageField = doc.get("limit_usage")
        if (limitUsageField is Map<*, *>) {
            limitUsageField.forEach { (k, v) ->
                try {
                    val dayName = k as? String ?: return@forEach
                    val minutesNumber = when (v) {
                        is Number -> v.toLong()
                        is String -> v.toLongOrNull() ?: return@forEach
                        else -> return@forEach
                    }
                    val day = DayOfWeek.valueOf(dayName)
                    limitUsageByDay[day] = Duration.ofMinutes(minutesNumber)
                } catch (_: Exception) { /* ignora */ }
            }
        } else if (limitUsageField is Number) {
            val minutes = limitUsageField.toLong()
            val targetDays = if (restrictions.isNotEmpty()) {
                restrictions.flatMap { it.days }.toSet()
            } else {
                DayOfWeek.values().toSet()
            }
            targetDays.forEach { d -> limitUsageByDay[d] = Duration.ofMinutes(minutes) }
        }

        val appInfo = InstalledAppInfo(packageName, appName, icon = null)
        val singleLimit = limitUsageByDay.values.firstOrNull()

        BlockedApp(
            appInfo,
            restrictions,
            limitUsageByDay = limitUsageByDay
        )
    }
}