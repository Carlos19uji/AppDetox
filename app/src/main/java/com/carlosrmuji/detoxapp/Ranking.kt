package com.carlosrmuji.detoxapp

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import io.branch.indexing.BranchUniversalObject
import io.branch.referral.Branch
import io.branch.referral.BranchError
import io.branch.referral.util.LinkProperties
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import org.jetbrains.annotations.Async
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


@Composable
fun Ranking(
    auth: FirebaseAuth,
    groupViewModel: GroupViewModel,
) {

    val linkViewModel: LinkViewModel = viewModel()
    val context = LocalContext.current
    val currentUserId = auth.currentUser?.uid ?: run {
        Log.d("Ranking", "No hay usuario actual, saliendo.")
        return
    }
    val groupId = groupViewModel.groupId.value ?: run {
        Log.d("Ranking", "No hay groupId en ViewModel, saliendo.")
        return
    }

    Log.d("Ranking", "ID del usuario actual: $currentUserId")
    Log.d("Ranking", "ID del grupo obtenido del ViewModel: $groupId")

    val rankingList by produceState(initialValue = emptyList<RankingEntry>(), currentUserId) {
        Log.d("Ranking", "Obteniendo ranking para userId: $currentUserId y groupId: $groupId")
        value = fetchGroupRanking(groupId)
        Log.d("Ranking", "Ranking obtenido: ${value.size} entradas")
    }

    var showInviteDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(groupId) {
        linkViewModel.loadInviteLink(context, groupId)
    }

    val inviteLink by linkViewModel.inviteLink

    Log.d("Ranking", "Estado actual de inviteLink: $inviteLink")
    Log.d("Ranking", "Mostrar diálogo de invitación: $showInviteDialog")

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icono de información pegado a la izquierda
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info Ranking",
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { showInfoDialog = true },
                        tint = Color.White
                    )


                    // Texto centrado usando un Box que ocupa todo el ancho disponible
                    Box(
                        modifier = Modifier
                            .weight(1f), // ocupa el espacio restante
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ranking",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            itemsIndexed(rankingList) { index, entry ->
                Log.d("Ranking", "Mostrando entrada $index: ${entry.name}, fase: ${entry.phase}, media diaria: ${entry.averageDailyUsage}")
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
                            modifier = Modifier.width(24.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(entry.photoUrl ?: R.drawable.ic_launcher_foreground)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Foto de perfil",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Gray)
                        )


                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = entry.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Text(
                                text = "Media diaria: ${formatTime(entry.averageDailyUsage)}",
                                color = Color.White
                            )

                            Text(
                                text = "Fase: ${entry.phase}",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )

                            entry.percentageChange?.let { percentage ->
                                val trend = if (percentage < 0) "↓" else "↑"
                                val trendColor = if (percentage < 0) Color(0xFF00FF99) else Color.Red
                                Text(
                                    text = "Últimos 7 días: $trend ${"%.1f".format(percentage)}%",
                                    color = trendColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            Log.d("Ranking", "Botón Invitar amigos pulsado")
                            showInviteDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(Color(0xFF5A4F8D))
                    ) {
                        Text("Invitar amigos", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (showInviteDialog) {
            Log.d("Ranking", "Mostrando InviteDialog con link: $inviteLink")
            InviteDialog(
                onDismiss = {
                    Log.d("Ranking", "InviteDialog cerrado")
                    showInviteDialog = false
                },
                groupId = groupId
            )
        }

        if (showInfoDialog){
            InfoDialog(onDismiss = {showInfoDialog = false})
        }
    }
}

@Composable
fun InfoDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
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
                    Text(
                        text = "Cómo se calcula el Ranking",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "• La media diaria se calcula sumando el tiempo de uso de todas las aplicaciones durante los últimos 7 dias y dividiéndolo entre el número de dias con datos de uso registrados.\n" +
                                "• El cambio de porcentaje muestra la variación respecto a la semana anterior.\n" +
                                "• Flecha ↑ indica aumento y ↓ disminución en comparación con la semana pasada.",
                        color = Color.LightGray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Start
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(Color(0xFF5A4F8D))
                    ) {
                        Text("Cerrar", color = Color.White)
                    }
                }
            }
        }
    }
}


suspend fun generateDynamicInviteLink(
    context: Context,
    groupId: String
): String {
    // Esperamos como máximo 5 segundos
    return withTimeoutOrNull(45_000L) {
        suspendCancellableCoroutine<String> { cont ->
            Log.d("InviteLink", "Generando link para grupo: $groupId")

            val branchUniversalObject = BranchUniversalObject()
                .setCanonicalIdentifier("group/$groupId")
                .setTitle("Únete al grupo")
                .setContentDescription("Invitación para unirte al grupo")
                .addContentMetadata("group_id", groupId)

            val linkProperties = LinkProperties()
                .setChannel("app")
                .setFeature("invite")
                .addControlParameter("action", "join_group")
                .addControlParameter("group_id", groupId)

            branchUniversalObject.generateShortUrl(
                context,
                linkProperties,
                object : Branch.BranchLinkCreateListener {
                    override fun onLinkCreate(url: String?, error: BranchError?) {
                        if (cont.isActive) {
                            if (error == null && url != null) {
                                Log.d("InviteLink", "Link generado exitosamente: $url")
                                cont.resume(url)
                            } else {
                                Log.e(
                                    "InviteLink",
                                    "Error generando link: ${error?.message ?: "desconocido"}"
                                )
                                cont.resumeWithException(
                                    error?.let { Exception(it.message) }
                                        ?: Exception("Error desconocido")
                                )
                            }
                        }
                    }
                }
            )

            // Si el coroutineScope es cancelado, aseguramos no dejar colgado al listener
            cont.invokeOnCancellation {
                // Aquí podrías limpiar recursos si hiciera falta
            }
        }
    } ?: run {
        // Timeout alcanzado: devolvemos un fallback o lanzamos excepción
        Log.w("InviteLink", "Timeout generando link, usando fallback.")
        "https://y6o0b.app.link/fallback_join?group_id=$groupId"
    }
}

@Composable
fun InviteDialog(
    onDismiss: () -> Unit,
    groupId: String
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
                        text = "Comparte el ID del grupo para que tus amigos se unan.",
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
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copiar",
                    tint = Color.White
                )
            }
        }
    }
}

suspend fun fetchGroupRanking(
    groupId: String
): List<RankingEntry> {
    val db = FirebaseFirestore.getInstance()
    val groupDoc = db.collection("groups").document(groupId).get().await()
    val members = groupDoc.get("members") as? Map<String, Map<String, Any>> ?: return emptyList()

    val today = LocalDate.now()
    val formatterPhase = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    val formatterUsage = DateTimeFormatter.ISO_LOCAL_DATE

    val thisWeekDates = (0..6).map { today.minusDays(it.toLong()).format(formatterUsage) }
    val lastWeekDates = (7..13).map { today.minusDays(it.toLong()).format(formatterUsage) }

    val results = members.map { (userId, data) ->
        kotlinx.coroutines.GlobalScope.async {
            val userDoc = db.collection("users").document(userId).get().await()
            val name = data["name"] as? String ?: "Usuario"
            val phaseNumber = (data["phase"] as? Long)?.toInt() ?: 1
            val photoUrl = userDoc.getString("profileImageUrl")

            // Uso esta semana
            val thisWeekDocs = db.collection("users").document(userId)
                .collection("time_use")
                .whereIn(FieldPath.documentId(), thisWeekDates)
                .get().await()

            val thisWeekUsage = thisWeekDocs.sumOf { doc ->
                doc.data.values.filterIsInstance<Number>().sumOf { it.toLong() }
            }
            val thisWeekAvg = if (thisWeekDocs.isEmpty) 0L else thisWeekUsage / thisWeekDocs.size()

            // Uso semana anterior
            val lastWeekDocs = db.collection("users").document(userId)
                .collection("time_use")
                .whereIn(FieldPath.documentId(), lastWeekDates)
                .get().await()

            val lastWeekUsage = lastWeekDocs.sumOf { doc ->
                doc.data.values.filterIsInstance<Number>().sumOf { it.toLong() }
            }
            val lastWeekAvg = if (lastWeekDocs.isEmpty) 0L else lastWeekUsage / lastWeekDocs.size()

            // Fase actual
            val phaseDoc = db.collection("users")
                .document(userId)
                .collection("groups")
                .document(groupId)
                .collection("phases")
                .document("phase$phaseNumber")
                .get().await()

            val fechaInicioStr = phaseDoc.getString("fecha_inicio")
            val fechaFinStr = phaseDoc.getString("fecha_fin")

            val fechaInicio = fechaInicioStr?.let { LocalDate.parse(it, formatterPhase) }
            val fechaFin = fechaFinStr?.let { LocalDate.parse(it, formatterPhase) } ?: today

            val allDocs = db.collection("users")
                .document(userId)
                .collection("time_use")
                .get().await()

            val phaseDocs = allDocs.filter { doc ->
                try {
                    val date = LocalDate.parse(doc.id, formatterUsage)
                    fechaInicio != null && !date.isBefore(fechaInicio) && !date.isAfter(fechaFin)
                } catch (e: Exception) {
                    false
                }
            }

            val phaseUsage = phaseDocs.sumOf { doc ->
                doc.data.values.filterIsInstance<Number>().sumOf { it.toLong() }
            }
            val phaseAvg = if (phaseDocs.isNotEmpty()) phaseUsage / phaseDocs.size else 0L

            val percentageChange = if (lastWeekAvg != 0L) {
                ((thisWeekAvg - lastWeekAvg).toDouble() / lastWeekAvg) * 100
            } else {
                null
            }

            RankingEntry(
                userId = userId,
                name = name,
                averageDailyUsage = thisWeekAvg,
                phase = phaseNumber,
                phaseAverageUsage = phaseAvg,
                percentageChange = percentageChange,
                photoUrl = photoUrl
            )
        }
    }

    return results.awaitAll().sortedBy { it.averageDailyUsage }
}
