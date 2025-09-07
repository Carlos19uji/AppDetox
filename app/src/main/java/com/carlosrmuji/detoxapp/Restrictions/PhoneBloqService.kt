package com.carlosrmuji.detoxapp.Restrictions

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import com.carlosrmuji.detoxapp.PhoneBlockRule
import com.carlosrmuji.detoxapp.R
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class UnifiedBlockerService : AccessibilityService() {

    // evita relanzados continuos
    @Volatile
    private var lastPhoneBlockedLaunchMillis = 0L
    private val PHONE_LAUNCH_THROTTLE_MS = 800L

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("UnifiedBlockerService", "Broadcast recibido: ${intent?.action}")
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                context?.let {
                    Log.d("UnifiedBlockerService", "ACTION_USER_PRESENT -> comprobar bloqueo teléfono")
                    checkAndLaunchPhoneBlock(it)
                }
            }
        }
    }

    override fun onServiceConnected() {
        Log.d("UnifiedBlockerService", "Servicio de accesibilidad conectado")
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }

        registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
        Log.d("UnifiedBlockerService", "Receiver ACTION_USER_PRESENT registrado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: "unknown"
        val cls = event?.className?.toString()
        Log.d("UnifiedBlockerService", "Evento accesibilidad: pkg=$pkg, class=$cls, type=${event?.eventType}")

        // 1) bloqueo por app (si aplica)
        try {
            if (pkg != "com.carlosrmuji.detoxapp" && RestrictionChecker.isAppBlockedNow(this, pkg)) {
                Log.d("UnifiedBlockerService", "App bloqueada detectada: $pkg -> lanzar BlockedAppActivity")
                try {
                    startActivity(Intent(this, BlockedAppActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        putExtra("BLOCKED_PACKAGE", pkg)
                    })
                } catch (e: Exception) {
                    Log.e("UnifiedBlockerService", "Error lanzando BlockedAppActivity: ${e.message}", e)
                }
                return
            }
        } catch (t: Throwable) {
            Log.e("UnifiedBlockerService", "Error comprobando bloqueo app: ${t.message}", t)
        }

        // 2) bloqueo por teléfono (si aplica) - comprobamos en cada evento relevante
        try {
            // Llamar a checkAndLaunchPhoneBlock en cada evento; la función hará throttle
            checkAndLaunchPhoneBlock(this)
        } catch (t: Throwable) {
            Log.e("UnifiedBlockerService", "Error comprobando bloqueo teléfono: ${t.message}", t)
        }
    }

    private fun checkAndLaunchPhoneBlock(context: Context) {
        try {
            val blocked = RestrictionCheckerPhone.isPhoneBlockedNow(context)
            Log.d("UnifiedBlockerService", "isPhoneBlockedNow = $blocked")
            if (!blocked) return

            val now = System.currentTimeMillis()
            if (now - lastPhoneBlockedLaunchMillis < PHONE_LAUNCH_THROTTLE_MS) {
                Log.d("UnifiedBlockerService", "Throttle activo, omitiendo relanzado (delta ${now - lastPhoneBlockedLaunchMillis}ms)")
                return
            }
            lastPhoneBlockedLaunchMillis = now

            Log.d("UnifiedBlockerService", "Lanzando PhoneBlockedActivity")
            try {
                startActivity(Intent(context, PhoneBlockedActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
            } catch (e: Exception) {
                Log.e("UnifiedBlockerService", "Error lanzando PhoneBlockedActivity: ${e.message}", e)
            }
        } catch (t: Throwable) {
            Log.e("UnifiedBlockerService", "Exception en checkAndLaunchPhoneBlock: ${t.message}", t)
        }
    }

    override fun onInterrupt() {
        Log.d("UnifiedBlockerService", "Servicio interrumpido")
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(unlockReceiver)
            Log.d("UnifiedBlockerService", "Receiver desregistrado")
        } catch (e: Exception) {
            Log.w("UnifiedBlockerService", "Receiver posiblemente ya desregistrado: ${e.message}")
        }
        super.onDestroy()
    }
}

class PhoneBlockedActivity : Activity() {

    private var isBlocked = true
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var countdownView: TextView
    private var countDownTimer: CountDownTimer? = null

    private val recheckRunnable = object : Runnable {
        override fun run() {
            try {
                val blocked = RestrictionCheckerPhone.isPhoneBlockedNow(this@PhoneBlockedActivity)
                Log.d("PhoneBlockedActivity", "recheck -> bloqueado = $blocked")
                if (!blocked) {
                    isBlocked = false
                    Log.d("PhoneBlockedActivity", "Fin de bloqueo -> finish()")
                    finish()
                    return
                }
            } catch (t: Throwable) {
                Log.e("PhoneBlockedActivity", "Error en recheck: ${t.message}", t)
            }
            // recheck cada 1s mientras esté bloqueado
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("PhoneBlockedActivity", "onCreate")
        setContentView(R.layout.phone_block_activity)
        // Referencia al TextView que muestra la cuenta atrás
        countdownView = findViewById(R.id.countdownTimer)

        // Evitar que la activity aparezca en la lista de recientes (opcional)
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // opcional: no hacemos nada aquí; la protección real la logra el relanzado desde el servicio
            }
        } catch (e: Exception) {
            Log.w("PhoneBlockedActivity", "ActivityManager: ${e.message}")
        }

        iniciarCuentaAtras()
        handler.post(recheckRunnable)
    }

    private fun iniciarCuentaAtras() {
        val endTimeMillis = RestrictionCheckerPhone.getPhoneBlockEndTime(this)
        if (endTimeMillis == null) {
            countdownView.text = "00:00:00"
            return
        }

        val currentTime = System.currentTimeMillis()
        val timeLeft = endTimeMillis - currentTime

        if (timeLeft > 0) {
            countDownTimer?.cancel() // Cancelar cualquier contador previo
            countDownTimer = object : CountDownTimer(timeLeft, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val hours = millisUntilFinished / (1000 * 60 * 60)
                    val minutes = (millisUntilFinished / (1000 * 60)) % 60
                    val seconds = (millisUntilFinished / 1000) % 60
                    countdownView.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                }

                override fun onFinish() {
                    countdownView.text = "00:00:00"
                    finish() // Cerrar cuando llegue a cero
                }
            }.start()
        } else {
            countdownView.text = "00:00:00"
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("PhoneBlockedActivity", "onResume - isBlocked=$isBlocked")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d("PhoneBlockedActivity", "onWindowFocusChanged: hasFocus=$hasFocus isBlocked=$isBlocked")
        // Si pierde foco mientras sigue bloqueado, pedir al servicio que la vuelva a lanzar
        if (!hasFocus && isBlocked) {
            Log.d("PhoneBlockedActivity", "Perdió foco y sigue bloqueado -> reabrir")
            try {
                startActivity(Intent(this, PhoneBlockedActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
            } catch (e: Exception) {
                Log.e("PhoneBlockedActivity", "Error reabriendo PhoneBlockedActivity: ${e.message}", e)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d("PhoneBlockedActivity", "onPause - isBlocked=$isBlocked")
        if (isBlocked) {
            // Intentamos traerla de nuevo al frente
            try {
                startActivity(Intent(this, PhoneBlockedActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
            } catch (e: Exception) {
                Log.e("PhoneBlockedActivity", "Error reintentando bring to front: ${e.message}", e)
            }
        }
    }

    override fun onBackPressed() {
        Log.d("PhoneBlockedActivity", "onBackPressed - isBlocked=$isBlocked")
        if (isBlocked) return
        super.onBackPressed()
    }

    override fun onUserLeaveHint() {
        Log.d("PhoneBlockedActivity", "onUserLeaveHint - isBlocked=$isBlocked")
        if (isBlocked) {
            // Si el usuario intenta irse, relanzamos
            try {
                startActivity(Intent(this, PhoneBlockedActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
            } catch (e: Exception) {
                Log.e("PhoneBlockedActivity", "Error relanzando desde onUserLeaveHint: ${e.message}", e)
            }
        }
        super.onUserLeaveHint()
    }

    override fun onDestroy() {
        handler.removeCallbacks(recheckRunnable)
        super.onDestroy()
        Log.d("PhoneBlockedActivity", "onDestroy")
    }
}

object RestrictionCheckerPhone {

    private var cachedPhoneBlocks: List<PhoneBlockRule> = emptyList()

    fun isPhoneBlockedNow(context: Context): Boolean {
        loadIfNeeded(context)

        val now = LocalDateTime.now()
        val currentDay = now.dayOfWeek.name
        val currentTime = now.toLocalTime()

        Log.d("RestrictionCheckerPhone", "Comprobando bloqueos para $currentDay $currentTime")
        cachedPhoneBlocks.forEach { rule ->
            Log.d("RestrictionCheckerPhone", "Regla: ${rule.id}, active=${rule.isActive}")
            if (!rule.isActive) return@forEach

            val dayRule = rule.phone_block[currentDay]
            Log.d("RestrictionCheckerPhone", "DayRule: $dayRule")
            if (dayRule != null) {
                val from = LocalTime.parse(dayRule["from"])
                val to = LocalTime.parse(dayRule["to"])
                Log.d("RestrictionCheckerPhone", "from=$from, to=$to, now=$currentTime")

                val inRange = if (!from.equals(to)) {
                    if (from <= to) {
                        // rango normal dentro del mismo día
                        (currentTime >= from && currentTime <= to)
                    } else {
                        // cruza medianoche (p.ej. 22:00 - 06:00)
                        (currentTime >= from) || (currentTime <= to)
                    }
                } else {
                    // from == to -> consider full-day block? (ajusta según tu lógica)
                    false
                }

                if (inRange) {
                    Log.d("RestrictionCheckerPhone", "Bloqueo activo por regla ${rule.id}")
                    return true
                }
            }
        }
        Log.d("RestrictionCheckerPhone", "No hay bloqueo activo")
        return false
    }

    fun loadIfNeeded(context: Context) {
        if (cachedPhoneBlocks.isNotEmpty()) {
            Log.d("RestrictionCheckerPhone", "Cache ya cargado (${cachedPhoneBlocks.size})")
            return
        }
        Log.d("RestrictionCheckerPhone", "Cargando reglas desde Firestore")
        runBlocking {
            val userId = getUserId()
            cachedPhoneBlocks = loadPhoneBlocks(userId)
            Log.d("RestrictionCheckerPhone", "Reglas cargadas: ${cachedPhoneBlocks.size}")
        }
    }

    private suspend fun loadPhoneBlocks(userId: String): List<PhoneBlockRule> {
        val db = FirebaseFirestore.getInstance()
        val snapshot = db.collection("users")
            .document(userId)
            .collection("phone_blocks")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val phoneBlockMap = doc.get("phone_block") as? Map<String, Map<String, String>> ?: emptyMap()
            PhoneBlockRule(
                id = doc.id,
                label = doc.getString("label") ?: "",
                isActive = doc.getBoolean("isActive") ?: true,
                phone_block = phoneBlockMap
            )
        }
    }

    // Helper para permitir invalidar cache si necesitas refrescar
    fun invalidateCache() {
        cachedPhoneBlocks = emptyList()
        Log.d("RestrictionCheckerPhone", "Cache invalidado")
    }

    fun getPhoneBlockEndTime(context: Context): Long? {
        loadIfNeeded(context)

        val now = LocalDateTime.now()
        val currentDay = now.dayOfWeek.name
        val currentTime = now.toLocalTime()

        cachedPhoneBlocks.forEach { rule ->
            if (!rule.isActive) return@forEach

            val dayRule = rule.phone_block[currentDay]
            if (dayRule != null) {
                val from = LocalTime.parse(dayRule["from"])
                val to = LocalTime.parse(dayRule["to"])

                val inRange = if (from <= to) {
                    (currentTime >= from && currentTime <= to)
                } else {
                    (currentTime >= from) || (currentTime <= to)
                }

                if (inRange) {
                    // Devuelve la hora "to" en milisegundos para la cuenta atrás
                    val today = now.toLocalDate()
                    var endDateTime = LocalDateTime.of(today, to)

                    // Si cruza la medianoche y el 'to' es mañana
                    if (from > to && currentTime <= to) {
                        endDateTime = endDateTime.plusDays(1)
                    }

                    return endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            }
        }

        return null
    }
}

