package com.carlosrmuji.detoxapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime


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
        val now = LocalDateTime.now()
        val app = cachedBlockedApps.find { it.app.packageName == packageName } ?: return false

        return app.restrictions.any { includesNow(it, now) }
    }

    fun getNextAvailableTime(context: Context, packageName: String): String {
        loadIfNeeded(context)
        val now = LocalDateTime.now()
        val app = cachedBlockedApps.find { it.app.packageName == packageName } ?: return "más tarde"

        val activeRestriction = app.restrictions
            .filter { includesNow(it, now) }
            .maxByOrNull { it.to.toSecondOfDay() }

        return activeRestriction?.to?.toString() ?: "más tarde"
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
}