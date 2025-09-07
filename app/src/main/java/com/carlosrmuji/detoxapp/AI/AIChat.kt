package com.carlosrmuji.detoxapp.AI

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.carlosrmuji.detoxapp.AIChatMessageData
import com.carlosrmuji.detoxapp.Billing.AdViewModel
import com.carlosrmuji.detoxapp.Screen
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID


@Composable
fun AIChat(
    navController: NavController,
    adViewModel: AdViewModel,
    auth: FirebaseAuth
) {
    val userId = auth.currentUser?.uid
    var prompt by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var tokens by remember { mutableStateOf<Int?>(null) }
    val messages = remember { mutableStateListOf<AIChatMessageData>() }
    val listState = rememberLazyListState()
    var isLoading by remember { mutableStateOf(true) }
    var isThinking by remember { mutableStateOf(false) }
    var tokenRenewal by remember { mutableStateOf<String?>(null) }  // Estado nuevo para la fecha de renovación

    // Cargar tokens una vez al abrir
    LaunchedEffect(userId) {
        userId?.let {
            val firestore = FirebaseFirestore.getInstance()
            try {
                val doc = firestore.collection("users")
                    .document(it)
                    .collection("IA")
                    .document("tokens")
                    .get()
                    .await()

                tokens = doc.getLong("tokens")?.toInt() ?: 0
                tokenRenewal = doc.getString("token_renovation")  // Carga la fecha aquí
            } catch (e: Exception) {
                tokens = 0
                tokenRenewal = null
            }
        }
    }

    // Escuchar mensajes en tiempo real
    LaunchedEffect(userId) {
        userId?.let { uid ->
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users")
                .document(uid)
                .collection("IA")
                .document("messages")
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, _ ->
                    snapshot?.let {
                        messages.clear()
                        for (doc in it.documents) {
                            val text = doc.getString("text") ?: ""
                            val sender = doc.getString("sender") ?: "user"
                            messages.add(AIChatMessageData(text, sender))
                        }

                        coroutineScope.launch {
                            delay(100)
                            listState.scrollToItem(messages.size)
                        }

                        isLoading = false
                    }
                }
        }
    }

    val recentHistory = messages.takeLast(4)

    Surface(
        color = Color.Black,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header con tokens
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tokens?.let {
                    val tokenDisplay = if (it == 999) "∞" else it.toString()
                    Text("Tokens: $tokenDisplay", color = Color.Gray)
                }
            }

            // Zona de mensajes o loader
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading -> {
                        CircularProgressIndicator(color = Color.White)
                    }
                    messages.isEmpty() -> {
                        Text(
                            "Esto es una IA especializada en ayudarte a reducir tu adicción a las pantallas.",
                            color = Color.Gray,
                            modifier = Modifier.padding(32.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            ChatMessages(messages, Modifier.weight(1f), listState)

                            if (isThinking) {
                                ChatMessage(text = "Pensando...", isSent = false)
                            }
                        }
                    }
                }
            }

            Divider(color = Color.DarkGray, thickness = 1.dp)

            if (tokens != null && tokens == 0) {
                NoTokensMessage(
                    tokenRenewal = tokenRenewal,
                    onUpgradeClick = {
                        navController.navigate(Screen.PlansScreen.route)
                    }
                )
            }

            // Input para el prompt
            MessageInput(
                prompt = prompt,
                onPromptChange = { prompt = it },
                onSend = {
                    if (prompt.isNotBlank()) {
                        if (tokens != null && tokens!! > 0) {
                            coroutineScope.launch {
                                userId?.let {
                                    val userPrompt = prompt
                                    prompt = ""
                                    isThinking = true

                                    // Guardamos mensaje del usuario
                                    saveMessage(it, userPrompt, "user")

                                    delay(100)
                                    listState.animateScrollToItem(messages.size + 1)

                                    val aiResponse = callOpenAI(userPrompt, recentHistory)

                                    if (aiResponse.isNotBlank() && !aiResponse.startsWith("⚠️")) {
                                        saveMessage(it, aiResponse, "ia")

                                        // Solo restar si NO es infinito
                                        if (tokens != 999) {
                                            val success = decrementToken(it)
                                            if (success) {
                                                tokens = tokens!! - 1
                                                if (tokens == 0) {
                                                    val activity = context as? Activity
                                                    val ad = adViewModel.aichatInterstitialAd

                                                    if (ad != null && activity != null) {
                                                        ad.fullScreenContentCallback =
                                                            object : FullScreenContentCallback() {
                                                                override fun onAdDismissedFullScreenContent() {
                                                                    adViewModel.clearAIChatAd()
                                                                    adViewModel.loadAiChatAd()
                                                                }
                                                            }
                                                        ad.show(activity)
                                                    }
                                                }
                                            } else {
                                                Toast.makeText(context, "⚠️ Error al restar token", Toast.LENGTH_SHORT).show()
                                            }
                                        }

                                        delay(100)
                                        listState.animateScrollToItem(messages.size + 2)
                                    } else {
                                        Toast.makeText(context, "⚠️ La IA no respondió correctamente", Toast.LENGTH_SHORT).show()
                                    }

                                    isThinking = false
                                }
                            }
                        } else {
                            Toast.makeText(context, "No te quedan tokens", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun NoTokensMessage(
    tokenRenewal: String?,
    onUpgradeClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = buildString {
                append("No te quedan tokens. Puedes volver a interactuar con la IA cuando se renueven tus tokens, el dia: ")
                append(tokenRenewal ?: "desconocido")
                append(". O mejorar tu plan.")
            },
            color = Color(0xFFFF4C4C), // rojo más intenso
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onUpgradeClick() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A4F8D))
        ) {
            Text("Mejorar plan", color = Color.White)
        }
    }
}



@Composable
fun ChatMessages(messages: List<AIChatMessageData>, modifier: Modifier = Modifier, listState: LazyListState) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        state = listState
    ) {
        items(messages) { message ->
            val isSent = message.sender == "user"
            ChatMessage(text = message.text, isSent = isSent)
        }
    }
}

@Composable
fun ChatMessage(text: String, isSent: Boolean) {
    val backgroundColor = if (isSent) Color.DarkGray else Color.Gray
    val contentColor = Color.White
    val alignment = if (isSent) Arrangement.End else Arrangement.Start

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = alignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .padding(horizontal = 4.dp)
                .background(backgroundColor, RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(6.dp)
            ) {
                Text(
                    text = text,
                    color = contentColor,
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(top = if (isSent) 4.dp else 0.dp)
                )
                if (isSent) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copiar mensaje",
                            tint = Color.LightGray,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(text))
                                    Toast.makeText(context, "Mensaje copiado", Toast.LENGTH_SHORT).show()
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageInput(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = prompt,
            onValueChange = onPromptChange,
            placeholder = { Text("Escribe tu mensaje a la IA...", color = Color.White) },
            colors = TextFieldDefaults.textFieldColors(
                textColor = Color.White,
                backgroundColor = Color(0xFF1F1F1F),
                cursorColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp),
            shape = RoundedCornerShape(20.dp),
            singleLine = false,
            maxLines = 4
        )

        IconButton(
            onClick = onSend,
            enabled = prompt.isNotBlank()
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Enviar",
                tint = if (prompt.isNotBlank()) Color.White else Color.Gray
            )
        }
    }
}

suspend fun getTokens(userId: String): Int {
    val firestore = FirebaseFirestore.getInstance()
    return try {
        val doc = firestore.collection("users")
            .document(userId)
            .collection("IA")
            .document("tokens")
            .get()
            .await()

        doc.getLong("tokens")?.toInt() ?: 0
    } catch (e: Exception) {
        0 // Si hay error, se asume 0 tokens disponibles
    }
}

suspend fun decrementToken(userId: String): Boolean {
    val firestore = FirebaseFirestore.getInstance()
    val docRef = firestore.collection("users")
        .document(userId)
        .collection("IA")
        .document("tokens")

    return try {
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val currentTokens = snapshot.getLong("tokens") ?: 0
            if (currentTokens > 0) {
                transaction.update(docRef, "tokens", currentTokens - 1)
            } else {
                throw FirebaseFirestoreException("No tokens left", FirebaseFirestoreException.Code.ABORTED)
            }
        }.await()
        true
    } catch (e: Exception) {
        false
    }
}

suspend fun saveMessage(
    userId: String,
    text: String,
    sender: String
) {
    val firestore = FirebaseFirestore.getInstance()
    val messageId = UUID.randomUUID().toString()

    val message = hashMapOf(
        "id" to messageId,
        "text" to text,
        "timestamp" to FieldValue.serverTimestamp(),
        "sender" to sender
    )

    firestore.collection("users")
        .document(userId)
        .collection("IA")
        .document("messages")
        .collection("messages")
        .document(messageId)
        .set(message)
}