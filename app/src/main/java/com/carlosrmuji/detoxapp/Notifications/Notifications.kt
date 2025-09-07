package com.carlosrmuji.detoxapp.Notifications

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.carlosrmuji.detoxapp.MainActivity
import com.carlosrmuji.detoxapp.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.app.Service
import android.os.IBinder


class UsageNotificationService : Service() {

    private val CHANNEL_ID = "USAGE_FOREGROUND_SERVICE"
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildServiceNotification())
        Log.d("UsageService", "Foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        Thread {
            var lastApp: String? = null
            while (isRunning) {
                val foregroundApp = UsageUtils.getForegroundPackage(this)
                if (foregroundApp != null &&
                    foregroundApp != packageName &&
                    foregroundApp != "com.sec.android.app.launcher" && // Excluir Inicio de One UI
                    foregroundApp != lastApp
                ) {
                    lastApp = foregroundApp

                    // Obtener tiempo de uso real en minutos
                    val usoMillis = UsageUtils.obtenerTiempoUsoHoy(this, foregroundApp)
                    val usoMin = TimeUnit.MILLISECONDS.toMinutes(usoMillis)

                    val thresholds = listOf(30L, 60L, 120L, 180L, 240L, 300L, 360L, 420L, 480L, 540L, 600L, 660L, 720L, 780L, 840L, 900L, 960L, 1020L, 1080L, 1140L, 1200L)
                    for (i in thresholds.indices) {
                        val threshold = thresholds[i]
                        val nextThreshold = if (i + 1 < thresholds.size) thresholds[i + 1] else Long.MAX_VALUE
                        if (usoMin >= threshold && usoMin < nextThreshold) {
                            val appName = getAppName(this, foregroundApp)
                            val message = when (threshold) {
                                30L -> "Has usado $appName más de 30 minutos hoy."
                                60L -> "Has usado $appName más de 1 hora hoy."
                                else -> "Has usado $appName más de ${threshold / 60} horas hoy."
                            }
                            NotificationHelper.mostrarNotificacion(
                                this,
                                "Aviso de uso: $appName",
                                message,
                                (foregroundApp.hashCode() and 0xffff).toInt() + threshold.toInt()
                            )
                            break
                        }
                    }
                }
                Thread.sleep(3000)
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }

    // Notificación obligatoria de foreground service
    private fun buildServiceNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DetoxApp activo")
            .setContentText("Monitorizando el uso de aplicaciones para tus avisos diarios")
            .setSmallIcon(R.drawable.logodetox)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service",
                NotificationManager.IMPORTANCE_MIN // Silencioso
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun getAppName(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }
}

object UsageUtils {

    // Devuelve packageName de la app que está en foreground en este momento, o null si no se puede determinar
    @SuppressLint("WrongConstant")
    fun getForegroundPackage(context: Context): String? {
        val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 5 * 60 * 1000L // revisar últimos 5 minutos de eventos

        val events = usageManager.queryEvents(start, end)
        var lastForeground: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForeground = event.packageName
            }
            if (event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                // opcion: podrias trackear, pero nos quedamos con el último MOVE_TO_FOREGROUND
            }
        }
        return lastForeground
    }

    // Tiempo en primer plano hoy (milis). Si packageName == null retorna tiempo total del dispositivo (suma).
    @SuppressLint("WrongConstant")
    fun obtenerTiempoUsoHoy(context: Context, packageName: String? = null): Long {
        val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val end = System.currentTimeMillis()

        val stats = usageManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        if (stats.isNullOrEmpty()) return 0L

        return if (packageName == null) {
            stats.sumOf { it.totalTimeInForeground }
        } else {
            stats.find { it.packageName == packageName }?.totalTimeInForeground ?: 0L
        }
    }
}

object NotificationHelper {
    private const val CHANNEL_ID = "USO_APPS_CHANNEL"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notificaciones de uso",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Avisos sobre tiempo de uso de apps y dispositivo" }
            nm?.createNotificationChannel(channel)
        }
    }

    fun mostrarNotificacion(context: Context, title: String, text: String, id: Int) {
        ensureChannel(context)

        if (!hasNotificationPermission(context)) {
            Log.w("Notification", "Permiso POST_NOTIFICATIONS no concedido. No se muestra la notificación.")
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logodetox)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (se: SecurityException) {
            Log.w("Notification", "No se pudo mostrar notificación: permiso denegado", se)
        }
    }
}

fun hasNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true // En Android < 13 siempre está concedido
    }
}
