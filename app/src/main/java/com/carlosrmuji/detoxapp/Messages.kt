package com.carlosrmuji.detoxapp

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.format.TextStyle



@Composable
fun Messages(
    groupViewModel: GroupViewModel,
    auth: FirebaseAuth
) {
    val currentUserId = auth.currentUser?.uid ?: return
    val groupId = groupViewModel.groupId.value ?: return
    val db = FirebaseFirestore.getInstance()

    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }

    val listState = rememberLazyListState() // Estado del scroll

    // Cargar mensajes desde Firestore
    LaunchedEffect(Unit) {
        val groupDoc = db.collection("groups").document(groupId).get().await()
        val membersMap = groupDoc.get("members") as? Map<String, Map<String, Any>> ?: emptyMap()

        val allMessages = mutableListOf<Message>()

        // Mapa userId -> nombre
        val userNameMap = membersMap.mapValues { (_, value) ->
            value["name"] as? String ?: "Desconocido"
        }

        // Obtener mensajes de cada usuario
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

        // Ordenar mensajes por timestamp
        messages = allMessages.sortedBy { it.timestamp }
    }

    // Hacer scroll al último mensaje cuando se cargan
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

        LazyColumn(
            state = listState, // Aplicamos el estado del scroll
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            reverseLayout = false
        ) {
            val shownDates = mutableSetOf<String>()
            items(messages) { msg ->
                val dateKey = getDateKey(msg.timestamp)
                if (dateKey !in shownDates) {
                    shownDates.add(dateKey)
                    DateSeparator(getFormattedDate(msg.timestamp))
                }
                MessageBubble(message = msg, isCurrentUser = msg.userId == currentUserId)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message, isCurrentUser: Boolean) {
    val bubbleColor = if (isCurrentUser) Color(0xFF075E54) else Color(0xFF1F1F1F)
    val alignment = if (isCurrentUser) Alignment.End else Alignment.Start
    val textColor = Color.White
    val shape = if (isCurrentUser)
        RoundedCornerShape(12.dp, 0.dp, 12.dp, 12.dp)
    else
        RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp)

    val nameColor = remember(message.userId) { getColorForUser(message.userId) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp), // más separación
        horizontalAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 250.dp) // burbuja un poco más estrecha
                .background(bubbleColor, shape)
                .padding(6.dp) // padding interno más compacto
        ) {
            if (!isCurrentUser) {
                Text(
                    text = message.name,
                    color = nameColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = message.text,
                color = textColor,
                fontSize = 16.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = formatHour(message.timestamp),
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
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
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val dateTime = LocalDateTime.parse(timestamp, formatter)
        val date = dateTime.toLocalDate()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        when {
            date.isEqual(today) -> "Hoy"
            date.isEqual(yesterday) -> "Ayer"
            else -> date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        }
    } catch (e: Exception) {
        timestamp.substringBefore(" ")
    }
}


fun getDateKey(timestamp: String): String {
    return try {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val dateTime = LocalDateTime.parse(timestamp, formatter)
        dateTime.toLocalDate().format(DateTimeFormatter.ISO_DATE) // yyyy-MM-dd
    } catch (e: Exception) {
        timestamp.substringBefore(" ") // fallback rápido
    }
}

// Hora de cada mensaje (parte inferior del globo)
fun formatHour(timestamp: String): String {
    return try {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val dateTime = LocalDateTime.parse(timestamp, formatter)
        dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        ""
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