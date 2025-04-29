package com.example.detoxapp

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.dynamiclinks.androidParameters
import com.google.firebase.dynamiclinks.dynamicLink
import com.google.firebase.dynamiclinks.dynamicLinks
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.jetbrains.annotations.Async
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun Ranking(
    navController: NavController,
    auth: FirebaseAuth,
    groupViewModel: GroupViewModel
) {
    val context = LocalContext.current
    val currentUserId = auth.currentUser?.uid ?: return
    val groupId = groupViewModel.groupId.value ?: return

    val rankingList by produceState(initialValue = emptyList<RankingEntry>(), currentUserId) {
        value = fetchGroupRanking(context, currentUserId, groupId)
    }

    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteLink by remember { mutableStateOf("") }

    LaunchedEffect(groupId) {
        inviteLink = generateDynamicInviteLink(groupId) // Genera el enlace
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            item {
                Text(
                    text = "Ranking",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            itemsIndexed(rankingList) { index, entry ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${index + 1}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(15.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Gray)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = entry.name, color = Color.White, fontSize = 16.sp)
                            Text(
                                text = "Media diaria: ${formatTime(entry.averageDailyUsage)}",
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { showInviteDialog = true },
                ) {
                    Text("Invitar amigos")
                }
            }
        }

        if (showInviteDialog) {
            InviteDialog(
                onDismiss = { showInviteDialog = false },
                groupId = groupId,
                inviteLink = inviteLink
            )
        }
    }
}

@Composable
fun InviteDialog(
    onDismiss: () -> Unit,
    groupId: String,
    inviteLink: String
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Dialog(onDismissRequest = { onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1A1A),
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Box {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Invitar amigos",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Comparte este enlace o el ID del grupo para que tus amigos se unan.",
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    InviteField(
                        label = "ID del grupo",
                        value = groupId,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(groupId))
                            Toast.makeText(context, "ID copiado", Toast.LENGTH_SHORT).show()
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    InviteField(
                        label = "Enlace de invitación",
                        value = inviteLink,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(inviteLink))
                            Toast.makeText(context, "Enlace copiado", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                // Botón de cerrar (X)
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color.DarkGray, shape = CircleShape)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun InviteField(label: String, value: String, onCopy: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier
                .background(Color(0xFF2C2C2C), RoundedCornerShape(8.dp))
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Copiar",
                    tint = Color.White
                )
            }
        }
    }
}

suspend fun fetchGroupRanking(
    context: Context,
    currentUserId: String,
    groupId: String
): List<RankingEntry> {
    val db = FirebaseFirestore.getInstance()

    val groupDoc = db.collection("groups").document(groupId).get().await()
    val members = groupDoc.toObject(GroupData::class.java)?.members ?: return emptyList()

    // Obtener las fechas de los últimos 7 días
    val today = LocalDate.now()
    val last7Days = (0..6).map { today.minusDays(it.toLong()) }
    // Asegurarse de que las fechas tengan el formato yyyy-MM-dd
    val weekDates = last7Days.map { it.format(DateTimeFormatter.ISO_LOCAL_DATE) }

    val results = members.map { (userId, memberData) ->
        kotlinx.coroutines.GlobalScope.async {
            val usageDocs = db.collection("users").document(userId)
                .collection("time_use")
                .whereIn(FieldPath.documentId(), weekDates) // Buscar documentos con estas fechas
                .get()
                .await()

            val total = usageDocs.sumOf { doc ->
                doc.data.values.filterIsInstance<Number>().sumOf { it.toLong() }
            }
            val average = if (usageDocs.isEmpty()) 0L else total / usageDocs.size()

            RankingEntry(userId, memberData.name, average)
        }
    }

    return results.awaitAll().sortedByDescending { it.averageDailyUsage }
}

suspend fun generateDynamicInviteLink(groupId: String): String {
    val dynamicLink = Firebase.dynamicLinks.dynamicLink {
        link = Uri.parse("https://miapp.com/join?groupId=$groupId")
        domainUriPrefix = "https://miapp.page.link"
        androidParameters { }
    }
    return dynamicLink.uri.toString()
}