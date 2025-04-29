package com.example.detoxapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.pow


@Composable
fun Previa(navController: NavController, groupViewModel: GroupViewModel, auth: FirebaseAuth) {
    val context = LocalContext.current
    val groupId = groupViewModel.groupId.value ?: return
    val currentUserId = auth.currentUser?.uid ?: return

    val showDialog = remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var inputPercentage by remember { mutableStateOf("50") }

    val isValidPercentage = inputPercentage.toIntOrNull() in 1..99
    val targetReduction = inputPercentage.toDoubleOrNull() ?: 50.0
    val weeklyFactor = (1 - targetReduction / 100).pow(1.0 / 3)
    val finalPercentage = weeklyFactor.pow(3) * 100
    val totalReduction = 100 - finalPercentage

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            item {
                Text(
                    text = "Bienvenido al grupo",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }

            item {
                Text(
                    text = "Estás en la previa del reto. Aquí te explicamos cómo funcionará:",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }

            val phases = listOf(
                "Fase 1 (5 días): Introducción y recopilación de datos.",
                "Fase 2 (7 días): Se te propondrán hábitos para reducir el uso del móvil.",
                "Fase 3 (7 días): Refuerzo de hábitos y nuevos retos.",
                "Fase 4 (7 días): Consolidación de hábitos y última reducción."
            )

            items(phases) { phase ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2B2B2B), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = phase,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showDialog.value = true },
                    colors = ButtonDefaults.buttonColors(Color(0xFF5A4F8D)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                ) {
                    Text("Iniciar reto", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

    }
    if (showDialog.value == true){
        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            title = {
                Text(
                    text = "¿Estás seguro?",
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "Al iniciar el reto, comenzará para todos los miembros del grupo.",
                        textAlign = TextAlign.Center,
                        color = Color.LightGray
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Indica el % total que quieres reducir al final del reto:",
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = inputPercentage,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() } && it.length <= 2) {
                                inputPercentage = it
                            }
                        },
                        label = { Text("% reducción") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(150.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray,
                            textColor = Color.White,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White
                        )
                    )

                    if (isValidPercentage) {
                        val porcentaje = inputPercentage.toInt()
                        val totalReduction = porcentaje.toDouble() / 100
                        val weeklyFactor = (1 - totalReduction).pow(1.0 / 3)
                        val weeklyReduction = (1 - weeklyFactor) * 100

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Debes reducir aproximadamente un ${String.format("%.2f", weeklyReduction)}% por semana",
                            textAlign = TextAlign.Center,
                            color = Color.LightGray
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isValidPercentage) {
                            coroutineScope.launch {
                                startChallengeForGroup(groupId, inputPercentage.toInt())
                                showDialog.value = false
                                navController.navigate(Screen.PhaseIntroScreen.route)
                            }
                        }
                    }
                ) {
                    Text("Aceptar", color = Color(0xFF5A4F8D))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog.value = false }) {
                    Text("Cancelar", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(16.dp),
            backgroundColor = Color(0xFF1A1A1A),
            contentColor = Color.White
        )
    }
}

suspend fun startChallengeForGroup(groupId: String, porcentaje: Int) {
    val db = FirebaseFirestore.getInstance()
    val groupRef = db.collection("groups").document(groupId)
    val groupSnapshot = groupRef.get().await()

    val groupData = groupSnapshot.toObject(GroupData::class.java)
    val members = groupData?.members ?: return

    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    val startDate = today.format(formatter)
    val endDate = today.plusDays(5).format(formatter) // Fase 1 = 5 días

    // Guardar reto general en el grupo
    groupRef.collection("reto").document("config").set(
        mapOf(
            "porcentajeTotal" to porcentaje,
            "porcentajeSemanal" to (1 - (1 - porcentaje / 100.0).pow(1.0 / 3)) * 100
        )
    )
    for (userId in members.keys) {
        val userRef = db.collection("users").document(userId)

        userRef.collection("phases").document("phase1").set(
            mapOf(
                "fase" to 1,
                "fecha_inicio" to startDate,
                "fecha_fin" to endDate
            )
        )

        // Actualizar atributo "etapa" a "Intro"
        groupRef.update("members.$userId.etapa", "Intro")
    }
}