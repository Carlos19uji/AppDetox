package com.example.detoxapp

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.max

class UsageStatsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: return@withContext Result.retry()
        val userRef = firestore.collection("users").document(user.uid)

        // ✅ Usa zona horaria local del dispositivo
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1) // Día anterior
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val date = calendar.time
        val dateKey = dateFormat.format(date)

        return@withContext try {
            val usageMap = getUsageStatsForDate(applicationContext, date)
            val usageRef = userRef.collection("time_use").document(dateKey)

            usageRef.set(usageMap).await()
            Log.d("UsageStatsWorker", "Estadísticas subidas para $dateKey")
            Result.success()
        } catch (e: Exception) {
            Log.e("UsageStatsWorker", "Error subiendo estadísticas: ${e.message}")
            Result.retry()
        }
    }
}

fun getUsageStatsForDate(context: Context, date: Date): Map<String, Any> {
    val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    val calendar = Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val startTime = calendar.timeInMillis
    calendar.add(Calendar.DAY_OF_YEAR, 1)
    val endTime = calendar.timeInMillis

    val events = usageStatsManager.queryEvents(startTime, endTime)
    val usageMap = mutableMapOf<String, Long>()

    var currentForegroundApp: String? = null
    var lastForegroundTimestamp: Long = 0L

    val event = UsageEvents.Event()
    while (events.hasNextEvent()) {
        events.getNextEvent(event)

        when (event.eventType) {
            UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                currentForegroundApp = event.packageName
                lastForegroundTimestamp = event.timeStamp
            }

            UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                val duration = event.timeStamp - lastForegroundTimestamp
                val packageName = currentForegroundApp
                if (packageName != null && duration > 0) {
                    usageMap[packageName] = usageMap.getOrDefault(packageName, 0L) + duration
                }
                currentForegroundApp = null
                lastForegroundTimestamp = 0L
            }
        }
    }

    val result = mutableMapOf<String, Any>()
    result["timestamp"] = Timestamp.now()
    result["date"] = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)

    // ✅ Cada paquete como campo independiente
    for ((packageName, duration) in usageMap) {
        result[packageName] = duration
    }

    return result
}
