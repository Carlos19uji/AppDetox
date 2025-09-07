package com.carlosrmuji.detoxapp.Restrictions

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
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.carlosrmuji.detoxapp.AppRestriction
import com.carlosrmuji.detoxapp.BlockedApp
import com.carlosrmuji.detoxapp.InstalledAppInfo
import com.carlosrmuji.detoxapp.R
import com.carlosrmuji.detoxapp.includes
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class BlockedAppActivity : Activity() {

    private lateinit var blockedPackage: String


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("BlockedAppActivity", "onCreate - Activity de bloqueo iniciada")
        setContentView(R.layout.activity_blocked_app)

        blockedPackage = intent.getStringExtra("BLOCKED_PACKAGE") ?: "Aplicación"
        val appName = RestrictionChecker.getAppNameFromPackage(this, blockedPackage)
        val nextAvailable = RestrictionChecker.getNextAvailableTime(this, blockedPackage)

        val blockMessage = findViewById<TextView>(R.id.block_message)
        blockMessage.text = "No puedes usar $appName en este momento. \n\nEstará disponible a partir de las $nextAvailable."

        val appIconView = findViewById<ImageView>(R.id.app_icon)
        try {
            val appIcon = packageManager.getApplicationIcon(blockedPackage)
            appIconView.setImageDrawable(appIcon)
        } catch (e: Exception) {
            Log.e("BlockedAppActivity", "No se pudo obtener el icono de la app: $blockedPackage", e)
            appIconView.setImageResource(R.drawable.ic_launcher_foreground)
        }
    }

    override fun onBackPressed() {
        // Ignorar botón atrás para que el usuario no pueda salir
        Log.d("BlockedAppActivity", "Botón atrás presionado, ignorado")
    }
}

object RestrictionChecker {
    private var cachedBlockedApps: List<BlockedApp> = emptyList()

    fun isAppBlockedNow(context: Context, packageName: String): Boolean {
        loadIfNeeded(context)
        val blockedApp = cachedBlockedApps.find { it.app.packageName == packageName } ?: return false

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        val db = Firebase.firestore
        val unlockPaid = runBlocking {
            val doc = db.collection("users")
                .document(userId)
                .collection("restrictions")
                .document(packageName)
                .get()
                .await()
            doc.getBoolean("unlock_day_paid") ?: false
        }

        if (unlockPaid) return false

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
            this["unlock_day_paid"] = false // 🔹 Añadido

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

        BlockedApp(
            appInfo,
            restrictions,
            limitUsageByDay = limitUsageByDay
        )
    }
}

class DayPassBillingManager(private val context: Context) : PurchasesUpdatedListener {

    private lateinit var billingClient: BillingClient
    private var currentPackageToUnlock: String? = null
    private var billingReady = false
    private var connecting = false

    var onPurchaseComplete: ((packageName: String) -> Unit)? = null
    var onError: ((msg: String) -> Unit)? = null

    fun startConnection(onReady: () -> Unit) {
        // Si ya inicializado y listo, llamamos onReady inmediatamente
        if (this::billingClient.isInitialized && billingReady && billingClient.isReady) {
            onReady()
            return
        }
        if (connecting) return // evitar reentradas
        connecting = true

        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases( // v8: PendingPurchasesParams no obligatorio; enablePendingPurchases new style handled inside builder
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    billingReady = true
                    onReady()
                } else {
                    billingReady = false
                    Log.e("DayPassBilling", "Error iniciando BillingClient: ${result.debugMessage} (${result.responseCode})")
                    onError?.invoke("Error inicializando Billing: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                billingReady = false
                connecting = false
                Log.e("DayPassBilling", "BillingService desconectado")
            }
        })
    }

    fun launchDayPassPurchase(activity: Activity, packageName: String) {
        Toast.makeText(context, "launchDayPassPurchase llamado para $packageName", Toast.LENGTH_SHORT).show()
        Log.d("DayPassBilling", "launchDayPassPurchase llamado para $packageName")

        currentPackageToUnlock = packageName

        if (!this::billingClient.isInitialized || !billingReady || !billingClient.isReady) {
            Log.w("DayPassBilling", "BillingClient no está listo. billingReady=$billingReady, isReady=${if (this::billingClient.isInitialized) billingClient.isReady else "no-init"}")
            // Intentamos reconectar y luego lanzar compra
            startConnection {
                launchDayPassPurchase(activity, packageName)
            }
            return
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId("unlock_day_pass") // productId en Play Console
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            Log.d("DayPassBilling", "queryProductDetailsAsync responseCode=${billingResult.responseCode}, debug='${billingResult.debugMessage}'")

            val products = productDetailsResult?.productDetailsList ?: emptyList()
            Log.d("DayPassBilling", "productDetailsList.size = ${products.size}")

            products.forEach { pd ->
                try {
                    val id = pd.productId
                    val title = pd.title ?: "no-title"
                    val hasOneTime = pd.oneTimePurchaseOfferDetails != null
                    val priceMicros = pd.oneTimePurchaseOfferDetails?.priceAmountMicros ?: -1L
                    val priceStr = pd.oneTimePurchaseOfferDetails?.formattedPrice ?: "n/a"
                    Log.d("DayPassBilling", "ProductDetails -> id=$id, title=$title, hasOneTime=$hasOneTime, priceMicros=$priceMicros, priceStr=$priceStr")
                } catch (e: Exception) {
                    Log.w("DayPassBilling", "Error leyendo ProductDetails: ${e.message}")
                }
            }

            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && products.isNotEmpty()) {
                val productDetails = products.first()
                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .build()
                        )
                    )
                    .build()

                // Comprobamos que la Activity está en buen estado
                if (activity.isFinishing || activity.isDestroyed) {
                    Log.w("DayPassBilling", "Activity no está en foreground; abortando launchBillingFlow")
                    onError?.invoke("Pantalla no disponible")
                    return@queryProductDetailsAsync
                }

                val launchResult = billingClient.launchBillingFlow(activity, flowParams)
                Log.d("DayPassBilling", "launchBillingFlow responseCode=${launchResult.responseCode}, debug='${launchResult.debugMessage}'")
            } else {
                Log.w("DayPassBilling", "No se encontraron productos o error: responseCode=${billingResult.responseCode}, productsSize=${products.size}")
                onError?.invoke("Producto no disponible o error en la consulta. code=${billingResult.responseCode}")
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) handlePurchase(purchase)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.w("DayPassBilling", "Compra cancelada por el usuario")
            onError?.invoke("Compra cancelada")
        } else {
            Log.w("DayPassBilling", "Compra cancelada o error: ${billingResult.debugMessage} (${billingResult.responseCode})")
            onError?.invoke("Error compra: ${billingResult.debugMessage}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.products.contains("unlock_day_pass")) {
            // Marca local (día desbloqueado) — usa java.time.LocalDate (API 26+) o alternativa si necesitas compatibilidad
            try {
                val prefs = context.getSharedPreferences("restrictions", Context.MODE_PRIVATE)
                prefs.edit().putLong("unlocked_until_day", java.time.LocalDate.now().toEpochDay()).apply()
            } catch (e: Exception) {
                Log.w("DayPassBilling", "No se pudo guardar fecha localmente: ${e.message}")
            }

            // Consumir la compra para permitir comprar de nuevo mañana
            val consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.consumeAsync(consumeParams) { result, token ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("DayPassBilling", "Compra consumida correctamente: $token")

                    // Actualizar Firestore (asegúrate de tener Firebase ya inicializado)
                    val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    if (userId == null) {
                        Log.w("DayPassBilling", "Usuario no autenticado; no se actualiza Firestore.")
                        onPurchaseComplete?.invoke(currentPackageToUnlock ?: "")
                        return@consumeAsync
                    }

                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val packageName = currentPackageToUnlock ?: ""

                    val data = mapOf("unlock_day_paid" to true, "unlocked_at" to com.google.firebase.Timestamp.now())

                    db.collection("users")
                        .document(userId)
                        .collection("restrictions")
                        .document(packageName)
                        .set(data, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d("DayPassBilling", "unlock_day_paid actualizado para $packageName")
                            onPurchaseComplete?.invoke(packageName)
                        }
                        .addOnFailureListener { e ->
                            Log.e("DayPassBilling", "Error actualizando unlock_day_paid", e)
                            onError?.invoke("Error actualizando servidor")
                        }
                } else {
                    Log.w("DayPassBilling", "Error consumiendo compra: ${result.debugMessage}")
                    onError?.invoke("Error consumiendo compra: ${result.debugMessage}")
                }
            }
        }
    }
}


@Composable
fun DayPassBillingScreen(
    packageName: String,
    dayPassBillingManager: DayPassBillingManager,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        Log.d("DayPassBillingScreen", "Mostrando DayPassBillingScreen para $packageName")
        Toast.makeText(context, "Pantalla DayPass abierta para $packageName", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Para acceder a la app hoy, realiza el pago de 0,99€ + IVA.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = {
                Log.d("DayPassBillingScreen", "Acceder pulsado -> intentar launchDayPassPurchase para $packageName")
                Toast.makeText(context, "Iniciando compra...", Toast.LENGTH_SHORT).show()
                val activity = (context as? Activity)
                if (activity != null) {
                    dayPassBillingManager.launchDayPassPurchase(activity, packageName)
                } else {
                    Log.e("DayPassBillingScreen", "Context no es Activity; no se puede llamar a launchBillingFlow")
                    Toast.makeText(context, "Error: pantalla no válida para iniciar compra", Toast.LENGTH_LONG).show()
                }
            }) {
                Text("Acceder")
            }

            Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)) {
                Text("Cancelar")
            }
        }
    }
}