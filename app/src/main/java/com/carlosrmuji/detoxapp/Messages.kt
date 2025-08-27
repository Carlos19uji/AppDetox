package com.carlosrmuji.detoxapp

import android.app.Activity
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Icon
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.format.TextStyle
import java.util.UUID


@Composable
fun Messages(
    groupViewModel: GroupViewModel,
    auth: FirebaseAuth,
    adViewModel: AdViewModel
) {
    val currentUserId = auth.currentUser?.uid ?: return
    val groupId = groupViewModel.groupId.value ?: return
    val db = FirebaseFirestore.getInstance()

    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var reflectionDoneToday by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }

    val listState = rememberLazyListState()
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var isSending by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(editingMessage) {
        if (editingMessage != null) {
            delay(100) // pequeño delay para que el TextField esté listo
            focusRequester.requestFocus()
        }
    }

    // Cargar mensajes desde Firestore
    LaunchedEffect(Unit) {
        isLoading = true

        val groupDoc = db.collection("groups").document(groupId).get().await()
        val membersMap = groupDoc.get("members") as? Map<String, Map<String, Any>> ?: emptyMap()
        val allMessages = mutableListOf<Message>()
        val userNameMap = membersMap.mapValues { (_, value) -> value["name"] as? String ?: "Desconocido" }

        membersMap.keys.forEach { userId ->
            val userMessagesSnapshot = db.collection("users")
                .document(userId)
                .collection("groups")
                .document(groupId)
                .collection("messages")
                .orderBy("timestamp")
                .get()
                .await()

            userMessagesSnapshot.documents.mapNotNullTo(allMessages) { doc ->
                val id = doc.getString("id") ?: return@mapNotNullTo null
                val text = doc.getString("text") ?: return@mapNotNullTo null
                val timestamp = doc.getString("timestamp") ?: return@mapNotNullTo null
                val name = userNameMap[userId] ?: "Desconocido"
                Message(id, userId, timestamp, text, name)
            }
        }

        // 🔹 Ordenar cronológicamente con Instant (UTC consistente)
        messages = allMessages.sortedBy { msg ->
            runCatching { Instant.parse(msg.timestamp) }.getOrNull() ?: Instant.EPOCH
        }

        // 🔹 Detectar si el usuario ya escribió hoy (comparando en horario español)
        val spainZone = ZoneId.of("Europe/Madrid")
        val todaySpain = LocalDate.now(spainZone)

        reflectionDoneToday = messages.any { msg ->
            msg.userId == currentUserId &&
                    runCatching {
                        val instant = Instant.parse(msg.timestamp)
                        val msgDateSpain = instant.atZone(spainZone).toLocalDate()
                        msgDateSpain.isEqual(todaySpain)
                    }.getOrDefault(false)
        }

        isLoading = false
    }


    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Text(
            text = "Reflexiones",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {

            when {
                isLoading -> {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                messages.isEmpty() -> {
                    Text(
                        text = "No hay reflexiones aún en este grupo.\n\n" +
                                "Comparte con los miembros del grupo alguna dificultad que hayas tenido " +
                                "para reducir el uso del móvil, algún beneficio que hayas notado gracias a usarlo menos, " +
                                "o algo que hayas hecho en lugar de perder el tiempo con el móvil.\n\n" +
                                "¡Sé el primero en escribir una reflexión!",
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val shownDates = mutableSetOf<String>()
                        items(messages) { msg ->
                            val dateKey = getDateKey(msg.timestamp)
                            if (dateKey !in shownDates) {
                                shownDates.add(dateKey)
                                DateSeparator(getFormattedDate(msg.timestamp))
                            }
                            MessageBubble(
                                message = msg,
                                isCurrentUser = msg.userId == currentUserId,
                                onEdit = { message ->
                                    inputText = message.text
                                    editingMessage = message
                                },
                                onDelete = { message ->
                                    db.collection("users")
                                        .document(currentUserId)
                                        .collection("groups")
                                        .document(groupId)
                                        .collection("messages")
                                        .document(message.id)
                                        .delete()
                                        .addOnSuccessListener {
                                            messages = messages.filter { it.id != message.id }
                                            reflectionDoneToday = false
                                        }
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        if (isLoading != true) {

            Divider(color = Color.DarkGray, thickness = 1.dp)


            // Input de mensaje o edición
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = {
                        if (!reflectionDoneToday && !isSending || editingMessage != null) inputText =
                            it
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .focusRequester(focusRequester),
                    enabled = !reflectionDoneToday || editingMessage != null,
                    colors = TextFieldDefaults.textFieldColors(
                        textColor = Color.White,
                        backgroundColor = Color(0xFF1F1F1F),
                        disabledTextColor = Color.Gray,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    placeholder = {
                        Text(
                            when {
                                editingMessage != null -> "Edita tu reflexión..."
                                reflectionDoneToday -> "Ya has escrito tu reflexión diaria" // 🔹 mensaje bloqueado
                                else -> "Escribe tu reflexión..."
                            },
                            color = Color.White
                        )
                    }
                )

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            isSending = true
                            if (editingMessage != null) {
                                // Guardar edición
                                val msg = editingMessage!!
                                db.collection("users")
                                    .document(currentUserId)
                                    .collection("groups")
                                    .document(groupId)
                                    .collection("messages")
                                    .whereEqualTo("id", msg.id)
                                    .get()
                                    .addOnSuccessListener { snapshot ->
                                        snapshot.documents.forEach {
                                            it.reference.update("text", inputText)
                                        }
                                        messages = messages.map {
                                            if (it.id == msg.id) it.copy(text = inputText) else it
                                        }
                                        inputText = ""
                                        editingMessage = null
                                        isSending = false
                                    }
                            } else {
                                // Crear nuevo mensaje
                                val spainZone = ZoneId.of("Europe/Madrid")
                                val nowSpain = ZonedDateTime.now(spainZone)
                                val timestamp = nowSpain.toInstant().toString()

                                val messageId = UUID.randomUUID().toString()
                                val newMessage = mapOf(
                                    "id" to messageId,
                                    "userId" to currentUserId,
                                    "timestamp" to timestamp,
                                    "text" to inputText
                                )

                                val userMessagesRef = db.collection("users")
                                    .document(currentUserId)
                                    .collection("groups")
                                    .document(groupId)
                                    .collection("messages")
                                    .document(messageId)

                                userMessagesRef.set(newMessage).addOnSuccessListener {
                                    messages = messages + Message(
                                        id = newMessage["id"] as String,
                                        userId = currentUserId,
                                        timestamp = timestamp,
                                        text = inputText,
                                        name = "Tú"
                                    )
                                    reflectionDoneToday = true
                                    inputText = ""
                                    isSending = false

                                    adViewModel.messageSaveInterstitialAd?.let { ad ->
                                        ad.show(context as Activity)
                                        adViewModel.clearMessageSaveAd()
                                        adViewModel.loadMessageSaveAd()
                                    }
                                }
                            }
                        }
                    },
                    enabled = !reflectionDoneToday || editingMessage != null
                ) {
                    Icon(
                        imageVector = if (editingMessage != null) Icons.Default.Check else Icons.Default.Send,
                        contentDescription = if (editingMessage != null) "Guardar" else "Enviar",
                        tint = if (!reflectionDoneToday || editingMessage != null) Color.White else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isCurrentUser: Boolean,
    onEdit: (Message) -> Unit = {},
    onDelete: (Message) -> Unit = {}
) {
    val bubbleColor = if (isCurrentUser) Color(0xFF075E54) else Color(0xFF1F1F1F)
    val alignment = if (isCurrentUser) Alignment.End else Alignment.Start
    val textColor = Color.White
    val shape = if (isCurrentUser)
        RoundedCornerShape(12.dp, 0.dp, 12.dp, 12.dp)
    else
        RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp)

    val nameColor = remember(message.userId) { getColorForUser(message.userId) }

    val isToday = remember(message.timestamp) {
        getDateKey(message.timestamp) == LocalDate.now().format(DateTimeFormatter.ISO_DATE)
    }

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 250.dp)
                .background(bubbleColor, shape)
                .padding(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Contenido principal (nombre + texto)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp) // espacio para la columna derecha
                ) {
                    if (!isCurrentUser) {
                        Text(
                            text = message.name,
                            color = nameColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    Text(
                        text = message.text,
                        color = textColor,
                        fontSize = 16.sp,
                        lineHeight = 18.sp
                    )
                }

                // Columna fija a la derecha para botón 3 puntos y hora
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .heightIn(min = 48.dp)  // para que tenga al menos la altura del texto
                        .width(36.dp)           // ancho fijo para que no afecte el texto
                ) {
                    if (isCurrentUser && isToday) {
                        Box {
                            IconButton(
                                onClick = { expanded = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Más opciones",
                                    tint = Color.White
                                )
                            }

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Editar", color = Color.DarkGray) },
                                    onClick = {
                                        expanded = false
                                        onEdit(message)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Eliminar", color = Color.DarkGray) },
                                    onClick = {
                                        expanded = false
                                        onDelete(message)
                                    }
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(24.dp)) // para mantener espacio aunque no haya icono
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = formatHour(message.timestamp),
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun DateSeparator(dateText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = dateText,
            color = Color.LightGray,
            fontSize = 12.sp,
            modifier = Modifier
                .background(Color.DarkGray, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

fun getFormattedDate(timestamp: String): String {
    return try {

        val instant = Instant.parse(timestamp)          // lo guardado en UTC
        val userZone = ZoneId.systemDefault()           // zona del usuario
        val userDateTime = instant.atZone(userZone)

        val today = LocalDate.now(userZone)
        val yesterday = today.minusDays(1)
        val date = userDateTime.toLocalDate()

        when {
            date.isEqual(today) -> "Hoy"
            date.isEqual(yesterday) -> "Ayer"
            else -> date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        }
    } catch (e: Exception) {
        timestamp.substringBefore(" ")
    }
}


fun formatHour(timestamp: String): String {
    return try {
        val instant = Instant.parse(timestamp)
        val userZone = ZoneId.systemDefault()
        val userDateTime = instant.atZone(userZone)
        userDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        ""
    }
}

fun getDateKey(timestamp: String): String {
    return try {
        val instant = Instant.parse(timestamp)
        val userZone = ZoneId.systemDefault()
        val userDate = instant.atZone(userZone).toLocalDate()
        userDate.format(DateTimeFormatter.ISO_DATE)
    } catch (e: Exception) {
        timestamp.substringBefore(" ")
    }
}


// Color aleatorio basado en userId (determinístico)
fun getColorForUser(userId: String): Color {
    val hash = userId.hashCode()
    val r = (hash shr 16 and 0xFF).coerceIn(100, 255)
    val g = (hash shr 8 and 0xFF).coerceIn(100, 255)
    val b = (hash and 0xFF).coerceIn(100, 255)
    return Color(r, g, b)
}