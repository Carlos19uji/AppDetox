package com.carlosrmuji.detoxapp

import android.app.Activity
import android.content.Context
import android.util.Log
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
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams

class BillingManager(
    private val context: Context,
) {
    private lateinit var billingClient: BillingClient
    private var skuDetailsList: List<SkuDetails> = listOf()

    private val skuList = listOf("plus_plan", "premium_plan")

    var onPlanUpdated: ((String) -> Unit)? = null
    var onPurchaseError: ((String) -> Unit)? = null

    fun startBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .enablePendingPurchases()
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (purchase in purchases) {
                        handlePurchase(purchase)
                    }
                }
            }.build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryAvailablePlans()
                    queryActivePurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.e("Billing", "Servicio de facturación desconectado")
            }
        })
    }

    private fun queryAvailablePlans(){
        val params = SkuDetailsParams.newBuilder()
            .setSkusList(skuList)
            .setType(BillingClient.SkuType.SUBS)
            .build()

        billingClient.querySkuDetailsAsync(params){ billingResult, detailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && detailsList != null){
                skuDetailsList = detailsList
                for (detail in detailsList) {
                    Log.d("Billing", "Plan: ${detail.title}, Precio: ${detail.price}")
                }
            }
        }
    }

    fun getSkuDetails(planId: String): SkuDetails? {
        return skuDetailsList.find { it.sku == planId }
    }

    fun launchBillingFlow(activity: Activity, skuDetails: SkuDetails) {
        val flowParams = BillingFlowParams.newBuilder()
            .setSkuDetails(skuDetails)
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.acknowledgePurchase(params) { billingResult ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    when (purchase.products[0]) {
                        "plus_plan" -> saveUserPlan("PLUS")
                        "premium_plan" -> saveUserPlan("PREMIUM")
                        else -> saveUserPlan("FREE")
                    }
                }
            }
        }
    }

    private fun queryActivePurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                if (purchasesList.isNotEmpty()) {
                    for (purchase in purchasesList) {
                        when (purchase.products[0]) {
                            "plus_plan" -> saveUserPlan("PLUS")
                            "premium_plan" -> saveUserPlan("PREMIUM")
                            else -> saveUserPlan("FREE")
                        }
                    }
                } else {
                    saveUserPlan("FREE")
                }
            }
        }
    }

    private fun saveUserPlan(plan: String) {
        context.getSharedPreferences("user_plan", Context.MODE_PRIVATE)
            .edit()
            .putString("current_plan", plan)
            .apply()
        Log.d("Billing", "Plan guardado: $plan")
        onPlanUpdated?.invoke(plan)
    }

    fun getUserPlan(): String {
        return context.getSharedPreferences("user_plan", Context.MODE_PRIVATE)
            .getString("current_plan", "FREE") ?: "FREE"
    }
}

@Composable
fun BillingScreen(
    billingViewModel: BillingViewModel,
    onClose: () -> Unit,
    onPurchasePlan: (String) -> Unit,
    onCancelPlan: () -> Unit,
) {
    val userPlan by billingViewModel.userPlan
    var selectedPlan by remember { mutableStateOf(userPlan.lowercase()) }

    val plans = listOf(
        Plan(
            id = "free",
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

                        // ✅ Etiqueta de "Tu plan actual" solo si es el mismo plan que el del usuario
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

                        when {
                            plan.id == userPlan.lowercase() -> {
                                if (plan.id != "free") {
                                    Button(
                                        onClick = onCancelPlan,
                                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Cancelar suscripción", color = Color.White)
                                    }
                                }
                            }

                            else -> {
                                Button(
                                    onClick = { onPurchasePlan(plan.id) },
                                    colors = ButtonDefaults.buttonColors(backgroundColor = primaryColor),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Suscribirse a ${plan.title}", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
