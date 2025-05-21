package com.example.detoxapp

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class UsageStatsWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val context = applicationContext
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        // Establecer el inicio del día actual a las 00:00:00
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        val appUsages = usageStatsList
            .filter { it.totalTimeInForeground > 0 }
            .associate { it.packageName to it.totalTimeInForeground }

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.e("UsageStatsWorker", "Usuario no autenticado")
            return Result.failure()
        }

        val userId = user.uid

        // ✅ Usar la fecha basada en startTime para asegurarse de que es del día que se está midiendo
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(startTime))

        val firestore = Firebase.firestore
        val documentRef = firestore
            .collection("users")
            .document(userId)
            .collection("time_use")
            .document(today)

        documentRef.set(appUsages)
            .addOnSuccessListener {
                Log.d("UsageStatsWorker", "Datos subidos correctamente")
            }
            .addOnFailureListener {
                Log.e("UsageStatsWorker", "Error subiendo datos", it)
            }

        // Reprogramar para el día siguiente a las 23:59
        scheduleNextWorker(context)

        return Result.success()
    }
}

fun scheduleNextWorker(context: Context) {
    val now = Calendar.getInstance()
    val nextRun = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)

        if (before(now)) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    val delay = nextRun.timeInMillis - now.timeInMillis

    val request = OneTimeWorkRequestBuilder<UsageStatsWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .addTag("daily_usage_stats")
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        "daily_usage_stats_work",
        ExistingWorkPolicy.REPLACE,
        request
    )
}
