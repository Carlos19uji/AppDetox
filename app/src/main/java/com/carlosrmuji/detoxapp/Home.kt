package com.carlosrmuji.detoxapp

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Space
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.android.gms.ads.AdRequest
import com.google.firebase.firestore.FieldValue

@Composable
fun HomeScreen(navController: NavController,
               auth: FirebaseAuth,
               groupViewModel: GroupViewModel = viewModel(),
               adViewModel: AdViewModel) {
    val db = FirebaseFirestore.getInstance()
    val userID = auth.currentUser?.uid ?: return

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (!isUsageStatsPermissionGranted(context)) {
            Toast.makeText(
                context,
                "Otorga permiso de acceso a uso para ver las estadísticas.",
                Toast.LENGTH_LONG
            ).show()
            requestUsageStatsPermission(context)
        }
    }

    var loadingState by remember { mutableStateOf(true) }
    var groups by remember { mutableStateOf<List<GroupData>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var groupToLeave by remember { mutableStateOf<String?>(null) }

    if (groupToLeave != null){
        AlertDialog(
            onDismissRequest = { groupToLeave = null },
            title = { Text("Abandonar Grupo", color = Color.White) },
            text = { Text("¿Seguro que quieres abandonar este grupo?", color = Color.White) },
            confirmButton = {
                Button(onClick = {
                    groupToLeave?.let { gid ->
                        leaveGroup(gid, userID, context){
                            groupToLeave = null
                            loadingState = true
                            navController.navigate(Screen.Home.route){
                                popUpTo(Screen.Home.route){ inclusive = true }
                            }
                        }
                    }
                }) {
                    Text("Confirmar", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {groupToLeave = null}) {
                    Text("Cancelar", color= Color.Black)
                }
            },
            backgroundColor = Color(0xFF222222)
        )
    }

    // Firestore loading
    LaunchedEffect(userID) {
        db.collection("users").document(userID).collection("groups").get()
            .addOnSuccessListener { result ->
                val groupIds = result.documents.mapNotNull { it.getString("groupId") }
                if (groupIds.isEmpty()) {
                    groups = emptyList()
                    loadingState = false
                    return@addOnSuccessListener
                }

                db.collection("groups")
                    .whereIn(FieldPath.documentId(), groupIds.take(10))
                    .get()
                    .addOnSuccessListener { groupDocs ->
                        val groupList = groupDocs.mapNotNull { doc ->
                            val groupName = doc.getString("groupName") ?: return@mapNotNull null
                            val membersRaw = doc.get("members") as? Map<*, *> ?: return@mapNotNull null

                            val members = membersRaw.mapNotNull { entry ->
                                val userId = entry.key as? String ?: return@mapNotNull null
                                val memberMap = entry.value as? Map<*, *> ?: return@mapNotNull null
                                val name = memberMap["name"] as? String ?: return@mapNotNull null
                                val phase = (memberMap["phase"] as? Long)?.toInt() ?: 1
                                val etapa = memberMap["etapa"] as? String ?: "Intro"
                                val timestamp = memberMap["phaseStartDate"] as? Timestamp ?: Timestamp.now()
                                val challengesRaw = memberMap["challengesCompleted"] as? Map<*, *>
                                val challengesCompleted = challengesRaw?.mapNotNull {
                                    val key = it.key as? String ?: return@mapNotNull null
                                    val value = it.value as? Boolean ?: return@mapNotNull null
                                    key to value
                                }?.toMap() ?: emptyMap()

                                userId to MemberData(name, phase, challengesCompleted, timestamp, etapa)
                            }.toMap()

                            GroupData(groupName, members, doc.id)
                        }
                        groups = groupList
                        loadingState = false
                    }
                    .addOnFailureListener { loadingState = false }
            }
            .addOnFailureListener { loadingState = false }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (loadingState) {
            // Spinner blanco centrado
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        } else {
            // Tu UI completa sobre negro
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(top = 24.dp, bottom = 16.dp)
                ) {
                    if (groups.isEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(32.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .background(Color(0xFF444444), RoundedCornerShape(16.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "¡Casi Listo!",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Crea o únete a un grupo para comenzar",
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Grupos:",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                        groups.forEach { group ->
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .padding(vertical = 8.dp)
                                        .background(Color(0xFF444444), RoundedCornerShape(12.dp))
                                        .clickable {
                                            groupViewModel.setGroupId(group.groupID)
                                            adViewModel.homeInterstitialAd?.let { ad ->
                                                ad.show(context as Activity)
                                                adViewModel.clearHomeAd()
                                                adViewModel.loadHomeAd()
                                            }
                                            navController.navigate(Screen.Ranking.route) {
                                                popUpTo(Screen.Home.route)
                                            }
                                        }
                                        .padding(16.dp)
                                ) {

                                    Row(
                                       modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Grupo: ${group.groupName}",
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )

                                        var expanded by remember { mutableStateOf(false) }

                                        Box(modifier = Modifier.wrapContentSize()) {
                                            IconButton(
                                                onClick = { expanded = true }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "Opciones",
                                                    tint = Color.White
                                                )
                                            }

                                            DropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false },
                                                offset = DpOffset(0.dp, 0.dp)
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Abandonar grupo") },
                                                    onClick = {
                                                        expanded = false
                                                        groupToLeave = group.groupID
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF888888)),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Text("+ Crear grupo", color = Color.White, fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showJoinDialog = true },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color.White),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Text("Unirse a un grupo", fontSize = 16.sp, color = Color.Black)
                    }
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
    }

    if (showCreateDialog) {
        ShowCreateGroupDialog(onDismiss = { showCreateDialog = false }, navController = navController)
    }

    if (showJoinDialog) {
        ShowJoinGroupDialog(userId = userID, navController = navController, onDismiss = { showJoinDialog = false })
    }
}

@Composable
fun ShowJoinGroupDialog(
    userId: String,
    navController: NavController,
    onDismiss: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    var groupIdInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Unirse a un grupo",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = groupIdInput,
                    onValueChange = { groupIdInput = it },
                    label = { Text("ID del grupo", color = Color.White) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray,
                        textColor = Color.White
                    )
                )
                errorMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = Color.Red, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (groupIdInput != "") {
                        scope.launch {
                            db.collection("groups").document(groupIdInput).get()
                                .addOnSuccessListener { doc ->
                                    if (doc.exists()) {
                                        onDismiss()
                                        navController.navigate(
                                            Screen.YourNameJoin.createRoute(
                                                groupIdInput
                                            )
                                        )
                                    } else {
                                        errorMessage = "No se encontró el grupo, introduce un id de grupo valido"
                                    }
                                }
                                .addOnFailureListener {
                                    errorMessage = "Error al buscar grupo"
                                }
                        }
                    } else{
                        errorMessage = "No se encontró el grupo, introduce un id de grupo valido"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50)
            ) {
                Text("Unirse", color = Color.White)
            }
        },
        backgroundColor = Color(0xFF222222)
    )
}

@Composable
fun YourNameJoin(
    navController: NavController,
    groupID: String,
    auth: FirebaseAuth,
    groupViewModel: GroupViewModel,
    adViewModel: AdViewModel
) {
    val context = LocalContext.current
    val userID = auth.currentUser?.uid ?: return
    var userName by remember { mutableStateOf("Nombre") }
    val db = FirebaseFirestore.getInstance()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Tu nombre", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "¿Cómo quieres que te vean los otros miembros del grupo?",
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(15.dp))

        TextField(
            value = userName,
            onValueChange = { userName = it },
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color.White,
                textColor = Color.Black,
                cursorColor = Color.Black,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val groupRef = db.collection("groups").document(groupID)
                        val groupDoc = groupRef.get().await()
                        val membersMap = groupDoc.get("members") as? Map<String, Map<String, Any>> ?: emptyMap()

                        val anyPrevia = membersMap.values.any { it["etapa"] == "Previa" }

                        val etapaValue = if (anyPrevia) "Previa" else "Intro"

                        val memberDataMap = mapOf(
                            "name" to userName,
                            "phase" to 1,
                            "challengesCompleted" to emptyMap<String, Boolean>(),
                            "phaseStartDate" to Timestamp.now(),
                            "etapa" to etapaValue
                        )

                        groupRef.update("members.$userID", memberDataMap).await()

                        val userGroupRef = db.collection("users")
                            .document(userID)
                            .collection("groups")
                            .document(groupID)

                        userGroupRef.set(mapOf("groupId" to groupID)).await()

                        adViewModel.joinOrCreateGroupInterstitialAd?.let { ad ->
                            ad.show(context as Activity)
                            adViewModel.clearCreateOrJoinAd()
                            adViewModel.loadCreateOrJoinGroupAd()
                        }

                        withContext(Dispatchers.Main) {
                            groupViewModel.setGroupId(groupID)
                            navController.navigate(Screen.Ranking.route)
                        }

                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error al unirse al grupo", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF888888)),
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("¡Únete!", color = Color.White, fontSize = 18.sp)
        }
    }
}

@Composable
fun ShowCreateGroupDialog(
    onDismiss: () -> Unit,
    navController: NavController
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "¿Nuevo Grupo?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = "Empieza tu desafío detox junto con tus amigos o familiares. Todos los miembros del grupo recibirán unos tips a seguir para ir avanzando en el desafío y podrás ver tanto tu progreso como el del resto de miembros del grupo.",
                color = Color(0xFFCCCCCC),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Column {
                Button(
                    onClick = {
                        onDismiss()
                        navController.navigate(Screen.NameGroup.route)
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF888888)),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Crear grupo", color = Color.White, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        },
        backgroundColor = Color(0xFF222222)
    )
}

@Composable
fun CreateGroupScreen(navController: NavController, auth: FirebaseAuth) {
    var groupName by remember { mutableStateOf("Reto Detox") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 32.dp, vertical = 48.dp), // Subimos el contenido
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Crear grupo", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        Text("Nombra tu grupo:", color = Color.White, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(15.dp))

        TextField(
            value = groupName,
            onValueChange = { groupName = it },
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color.White,
                textColor = Color.Black,
                cursorColor = Color.Black,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { navController.navigate(Screen.YourName.createRoue(groupName)) },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF888888)),
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Crear", color = Color.White, fontSize = 18.sp)
        }
    }
}

@Composable
fun YourName(
    navController: NavController,
    groupName: String, auth: FirebaseAuth,
    groupViewModel: GroupViewModel,
    adViewModel: AdViewModel) {
    val context = LocalContext.current
    val userID = auth.currentUser?.uid ?: return
    var userName by remember { mutableStateOf("Nombre") }
    val db = FirebaseFirestore.getInstance()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Tu nombre", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "¿Cómo quieres que te vean los otros miembros del grupo?",
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(15.dp))

        TextField(
            value = userName,
            onValueChange = { userName = it },
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(
                backgroundColor = Color.White,
                textColor = Color.Black,
                cursorColor = Color.Black,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                val groupRef = db.collection("groups").document()
                val groupID = groupRef.id

                // Miembro como mapa plano
                val memberDataMap = mapOf(
                    "name" to userName,
                    "phase" to 1,
                    "challengesCompleted" to emptyMap<String, Boolean>(),
                    "phaseStartDate" to Timestamp.now(),
                    "etapa" to "Previa"
                )

                val groupData = mapOf(
                    "groupName" to groupName,
                    "groupID" to groupID,
                    "members" to mapOf(userID to memberDataMap)
                )

                groupRef.set(groupData).addOnSuccessListener {
                    val userGroupRef = db.collection("users").document(userID)
                        .collection("groups").document(groupID)

                    userGroupRef.set(hashMapOf("groupId" to groupID)).addOnSuccessListener {

                        adViewModel.joinOrCreateGroupInterstitialAd?.let { ad ->
                            ad.show(context as Activity)
                            adViewModel.clearCreateOrJoinAd()
                            adViewModel.loadCreateOrJoinGroupAd()
                        }

                        groupViewModel.setGroupId(groupID)
                        navController.navigate(Screen.Ranking.route)
                    }.addOnFailureListener {
                        Toast.makeText(context, "Error al guardar en el usuario", Toast.LENGTH_SHORT).show()
                    }
                }.addOnFailureListener {
                    Toast.makeText(context, "Error al crear grupo", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF888888)),
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("¡Únete!", color = Color.White, fontSize = 18.sp)
        }
    }
}

fun leaveGroup(groupID: String, userID: String, context: Context, onComplete: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val groupRef = db.collection("groups").document(groupID)

    groupRef.get().addOnSuccessListener { doc ->
        if (doc.exists()) {
            val members = doc.get("members") as? Map<String, Any> ?: emptyMap()

            if (members.size <= 1) {
                // Si solo queda este usuario, eliminar grupo completo
                groupRef.delete().addOnSuccessListener {
                    db.collection("users").document(userID)
                        .collection("groups").document(groupID).delete()
                        .addOnSuccessListener {
                            Toast.makeText(context, "Grupo eliminado", Toast.LENGTH_SHORT).show()
                            onComplete()
                        }
                }
            } else {
                // Si hay más miembros, solo eliminar al usuario
                groupRef.update("members.$userID", FieldValue.delete())
                    .addOnSuccessListener {
                        db.collection("users").document(userID)
                            .collection("groups").document(groupID).delete()
                            .addOnSuccessListener {
                                Toast.makeText(context, "Has salido del grupo", Toast.LENGTH_SHORT).show()
                                onComplete()
                            }
                    }
            }
        } else {
            Toast.makeText(context, "El grupo ya no existe", Toast.LENGTH_SHORT).show()
            onComplete()
        }
    }.addOnFailureListener {
        Toast.makeText(context, "Error al abandonar el grupo", Toast.LENGTH_SHORT).show()
        onComplete()
    }
}
