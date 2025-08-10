package com.carlosrmuji.detoxapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BillingManager(private val context: Context) {
    private lateinit var billingClient: BillingClient
    private var productDetailsList: List<ProductDetails> = listOf()
    private var pendingBasePlanId: String? = null

    private val productIds = listOf("main_suscription") // NUEVO: ID de grupo de suscripción

    var onPlanUpdated: ((String) -> Unit)? = null
    var onPurchaseError: ((String) -> Unit)? = null


    fun startBillingClient(onQueryDone: (() -> Unit)? = null) {
        billingClient = BillingClient.newBuilder(context)
            .enablePendingPurchases()
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (purchase in purchases) {
                        handlePurchase(purchase)
                    }
                }
            }
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryAvailablePlans()
                    queryActivePurchases(onQueryDone)
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.e("Billing", "Servicio de facturación desconectado")
            }
        })
    }

    private fun queryAvailablePlans() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                this.productDetailsList = productDetailsList
            } else {
                Log.e("Billing", "Error consultando productos: ${billingResult.debugMessage}")
            }
        }
    }

    fun getProductDetails(basePlanId: String): ProductDetails? {
        return productDetailsList.find { pd ->
            pd.subscriptionOfferDetails?.any { it.basePlanId == basePlanId } == true
        }
    }

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails, basePlanId: String) {
        // Memoriza el plan que vamos a pedir
        pendingBasePlanId = basePlanId

        val offerToken = productDetails.subscriptionOfferDetails
            ?.find { it.basePlanId == basePlanId }
            ?.offerToken ?: return

        val billingParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()

        billingClient.launchBillingFlow(activity, billingParams)
    }

    fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

                billingClient.acknowledgePurchase(acknowledgeParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        extractBasePlanFromPurchase(purchase)
                    } else {
                        Log.e("Billing", "Error al reconocer compra: ${billingResult.debugMessage}")
                    }
                }
            } else {
                extractBasePlanFromPurchase(purchase)
            }
        }
    }

    private fun extractBasePlanFromPurchase(purchase: Purchase) {
        // Si habíamos guardado qué basePlanId lanzamos, lo usamos
        val normalizedPlan = pendingBasePlanId?.also {
            // limpiamos para la próxima
            pendingBasePlanId = null
        } ?: run {
            // …tu lógica actual de inspeccionar subscriptionOfferDetails…
            val productId = purchase.products.firstOrNull() ?: return
            val pd = productDetailsList.find { it.productId == productId } ?: return
            val knownBasePlans = listOf("plus_plan", "premium_plan")
            val offer = pd.subscriptionOfferDetails?.find { off ->
                knownBasePlans.contains(off.basePlanId.replace("-", "_"))
            }
            offer?.basePlanId
                ?.replace("-", "_")
                ?: run {
                    Log.e("Billing", "❌ No pude determinar plan")
                    return
                }
        }

        Log.d("Billing", "✅ Plan final: $normalizedPlan")
        saveUserPlan(normalizedPlan)
    }

    private fun queryActivePurchases(onDone: (() -> Unit)? = null) {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchasesList.isEmpty()) {
                    saveUserPlan("base_plan")
                    onDone?.invoke()
                    return@queryPurchasesAsync
                }

                for (purchase in purchasesList) {
                    extractBasePlanFromPurchase(purchase)
                }
            }
            onDone?.invoke()
        }
    }

    fun saveUserPlan(plan: String) {
        val planNormalized = plan.lowercase().replace("-", "_")
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()

        // Guardar plan localmente
        context.getSharedPreferences("user_plan", Context.MODE_PRIVATE)
            .edit()
            .putString("current_plan", planNormalized)
            .apply()

        Log.d("Billing", "Plan guardado localmente: $planNormalized")

        // Referencia a tokens doc
        val tokensDoc = firestore.collection("users")
            .document(userId)
            .collection("IA")
            .document("tokens")

        // Obtener tokens actuales y token_renovation
        tokensDoc.get().addOnSuccessListener { snapshot ->
            val tokensCurrent = snapshot.getLong("tokens")?.toInt() ?: 0
            val tokenRenovationStr = snapshot.getString("token_renovation") ?: ""

            val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
            val today = LocalDate.now()
            val todayStr = today.format(formatter)

            val shouldRenewTokens = try {
                val tokenRenovationDate = LocalDate.parse(tokenRenovationStr, formatter)
                !tokenRenovationDate.isAfter(today)  // true si tokenRenovation <= today
            } catch (e: Exception) {
                // Si no puede parsear la fecha, asumimos que debe renovar
                true
            }

            // Tokens asignados por plan
            val tokensToAssign = when (planNormalized) {
                "base_plan" -> 5
                "plus_plan" -> 25
                "premium_plan" -> 999
                else -> 0
            }

            // Guardar plan en Firestore
            firestore.collection("users")
                .document(userId)
                .collection("plan")
                .document("plan")
                .set(mapOf("plan" to planNormalized))
                .addOnSuccessListener {
                    Log.d("Billing", "✅ Plan actualizado en Firestore: $planNormalized")
                }
                .addOnFailureListener {
                    Log.e("Billing", "❌ Error al guardar plan: ${it.message}")
                }

            if (planNormalized == "base_plan" && !shouldRenewTokens) {
                // No renovamos tokens ni token_renovation, solo actualizamos token_start y tokens actuales
                tokensDoc.set(
                    mapOf(
                        "tokens" to tokensCurrent,
                        "token_start" to todayStr
                        // No tocamos token_renovation
                    ),
                    SetOptions.merge()
                ).addOnSuccessListener {
                    Log.d("Billing", "✅ Tokens conservados para base_plan sin renovación")
                }.addOnFailureListener {
                    Log.e("Billing", "❌ Error al guardar tokens: ${it.message}")
                }

            } else {
                // Renovamos tokens, token_start y token_renovation (fecha a +7 días)
                val nextWeekStr = today.plusDays(7).format(formatter)
                tokensDoc.set(
                    mapOf(
                        "tokens" to tokensToAssign,
                        "token_start" to todayStr,
                        "token_renovation" to nextWeekStr
                    )
                ).addOnSuccessListener {
                    Log.d("Billing", "✅ Tokens iniciales guardados para $planNormalized")
                }.addOnFailureListener {
                    Log.e("Billing", "❌ Error al guardar tokens: ${it.message}")
                }
            }

            // Notificar al ViewModel
            onPlanUpdated?.invoke(planNormalized)
        }.addOnFailureListener {
            Log.e("Billing", "❌ Error al leer tokens existentes: ${it.message}")
            // En caso de error leyendo tokens, guardamos todo como renovación
            val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
            val today = LocalDate.now()
            val todayStr = today.format(formatter)
            val nextWeekStr = today.plusDays(7).format(formatter)

            val tokensToAssign = when (planNormalized) {
                "base_plan" -> 5
                "plus_plan" -> 25
                "premium_plan" -> 999
                else -> 0
            }

            firestore.collection("users")
                .document(userId)
                .collection("plan")
                .document("plan")
                .set(mapOf("plan" to planNormalized))

            firestore.collection("users")
                .document(userId)
                .collection("IA")
                .document("tokens")
                .set(
                    mapOf(
                        "tokens" to tokensToAssign,
                        "token_start" to todayStr,
                        "token_renovation" to nextWeekStr
                    )
                )
        }
    }
}



@Composable
fun BillingScreen(
    billingViewModel: BillingViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val userPlan by billingViewModel.userPlan
    val purchaseStatus by billingViewModel.purchaseStatus
    var selectedPlan by remember(userPlan) { mutableStateOf(userPlan.lowercase()) }

    // Mostrar Toast si cambia el estado de compra
    LaunchedEffect(purchaseStatus) {
        purchaseStatus?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            billingViewModel.clearPurchaseStatus()
        }
    }

    val plans = listOf(
        Plan(
            id = "base_plan",
            title = "Estándar",
            price = "Gratis",
            features = listOf(
                "5 tokens semanales",
                "Restringe hasta 2 aplicaciones",
                "Con anuncios"
            )
        ),
        Plan(
            id = "plus_plan",
            title = "Plus",
            price = "2,99€ / mes",
            features = listOf(
                "25 tokens semanales",
                "Restringe hasta 5 aplicaciones",
                "Sin anuncios",
            )
        ),
        Plan(
            id = "premium_plan",
            title = "Premium",
            price = "4,99€ / mes",
            features = listOf(
                "Tokens ilimitados",
                "Restringe aplicaciones ilimitadas",
                "Sin anuncios"
            )
        )
    )

    val primaryColor = Color(0xFF5A4F8D)
    val backgroundColor = Color.Black
    val textColor = Color.White
    val subtitleColor = Color.LightGray

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mejora tu plan",
                    fontSize = 20.sp,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Cerrar",
                        tint = textColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                plans.forEach { plan ->
                    val isSelected = selectedPlan == plan.id
                    Button(
                        onClick = { selectedPlan = plan.id },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = if (isSelected) primaryColor else Color.Transparent
                        ),
                        border = if (!isSelected) BorderStroke(1.dp, primaryColor) else null,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = plan.title,
                            color = if (isSelected) Color.White else primaryColor,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            val plan = plans.firstOrNull { it.id == selectedPlan }

            plan?.let {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .wrapContentHeight()
                            .background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
                            .padding(vertical = 24.dp, horizontal = 16.dp)
                    ) {
                        Text(
                            text = plan.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )

                        Text(
                            text = plan.price,
                            fontSize = 16.sp,
                            color = primaryColor
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        plan.features.forEach { feature ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = feature,
                                    color = subtitleColor,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (plan.id == userPlan.lowercase()) {
                            Text(
                                text = "Tu plan actual",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Green,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Mostrar botón solo si es diferente al actual
                        if (plan.id != userPlan.lowercase()) {
                            if (plan.id == "plus_plan" || plan.id == "premium_plan") {
                                Button(
                                    onClick = {
                                        if (activity != null) {
                                            billingViewModel.launchPurchase(activity, plan.id)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(backgroundColor = primaryColor),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Mejorar plan a ${plan.title}", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

