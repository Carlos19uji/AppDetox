package com.example.detoxapp

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch


@Composable
fun HomeScreen(navController: NavController, auth: FirebaseAuth, groupViewModel: GroupViewModel = viewModel()) {

    val db = FirebaseFirestore.getInstance()
    val userID = auth.currentUser?.uid ?: return

    var loadingState by remember { mutableStateOf(true) }
    var groups by remember { mutableStateOf<List<GroupData>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userID) {
        Log.d("HomeScreen", "Fetching groups for userID: $userID")
        db.collection("users").document(userID).collection("groups").get()
            .addOnSuccessListener { result ->
                val groupIds = result.documents.mapNotNull { it.getString("groupId") }
                Log.d("HomeScreen", "Group IDs retrieved: $groupIds")

                if (groupIds.isEmpty()) {
                    Log.d("HomeScreen", "No groups found for this user.")
                    groups = emptyList()
                    loadingState = false
                    return@addOnSuccessListener
                }

                db.collection("groups")
                    .whereIn(FieldPath.documentId(), groupIds.take(10))
                    .get()
                    .addOnSuccessListener { groupDocs ->
                        Log.d("HomeScreen", "Successfully retrieved group documents.")
                        val groupList = groupDocs.mapNotNull { doc ->
                            val groupName = doc.getString("groupName") ?: run {
                                Log.e("HomeScreen", "Group name is null for doc ${doc.id}")
                                return@mapNotNull null
                            }

                            val membersRaw = doc.get("members") as? Map<*, *> ?: run {
                                Log.e("HomeScreen", "Members map is null for group $groupName (${doc.id})")
                                return@mapNotNull null
                            }

                            val members = membersRaw.mapNotNull { entry ->
                                val userId = entry.key as? String ?: return@mapNotNull null
                                val memberMap = entry.value as? Map<*, *> ?: return@mapNotNull null

                                val name = memberMap["name"] as? String ?: return@mapNotNull null
                                val phase = (memberMap["phase"] as? Long)?.toInt() ?: 1
                                val etapa = memberMap["etapa"] as? String ?: "Intro"
                                val timestamp = memberMap["phaseStartDate"] as? Timestamp ?: Timestamp.now()

                                val challengesRaw = memberMap["challengesCompleted"] as? Map<*, *>
                                val challengesCompleted = challengesRaw?.mapNotNull {
                                    val key = (it.key as? String) ?: return@mapNotNull null
                                    val value = it.value as? Boolean ?: return@mapNotNull null
                                    key to value
                                }?.toMap() ?: emptyMap()

                                userId to MemberData(
                                    name = name,
                                    phase = phase,
                                    challengesCompleted = challengesCompleted,
                                    phaseStartDate = timestamp,
                                    etapa = etapa
                                )
                            }.toMap()

                            val groupID = doc.id
                            Log.d("HomeScreen", "Group loaded: $groupName (ID: $groupID) with ${members.size} members")
                            GroupData(groupName, members, groupID)
                        }

                        groups = groupList
                        loadingState = false
                        Log.d("HomeScreen", "Finished loading groups. Count: ${groupList.size}")
                    }
                    .addOnFailureListener {
                        Log.e("HomeScreen", "Error loading group documents: ${it.message}", it)
                        loadingState = false
                    }
            }
            .addOnFailureListener {
                Log.e("HomeScreen", "Error fetching user group references: ${it.message}", it)
                loadingState = false
            }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 32.dp)
    ) {
        when {
            loadingState -> {
                item {
                    Log.d("HomeScreen", "Loading state active - showing spinner")
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            groups.isEmpty() -> {
                item {
                    Log.d("HomeScreen", "No groups to show - showing onboarding options")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("HomeScreen", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                    Box(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(0.8f)
                            .background(Color(0xFF444444), RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "¡Casi Listo!",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Crea o únete a un grupo para comenzar",
                                color = Color.White,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { showCreateDialog = true },
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF888888)),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("+ Crear grupo", color = Color.White, fontSize = 18.sp)
                            }
                            OutlinedButton(
                                onClick = { showJoinDialog = true },
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, Color.White),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text="Unirse a un grupo",color= Color.Black, fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }

            else -> {
                item {
                    Log.d("HomeScreen", "Displaying group list")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("HomeScreen", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(50.dp))
                }
                groups.forEach { group ->
                    item {
                        Log.d("HomeScreen", "Rendering group item: ${group.groupName}")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .background(Color(0xFF444444), RoundedCornerShape(12.dp))
                                .padding(16.dp)
                                .clickable {
                                    Log.d("HomeScreen", "Group clicked: ${group.groupName}, navigating to group screen")
                                    groupViewModel.setGroupId(group.groupID)
                                    navController.navigate(Screen.Group.createRoute(group.groupID))
                                }
                        ) {
                            Text(
                                text = "Grupo: ${group.groupName}",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
                item {
                    Log.d("HomeScreen", "Showing create/join group options")
                    Spacer(modifier = Modifier.height(100.dp))
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF888888)),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ Crear grupo", color = Color.White, fontSize = 18.sp)
                    }
                    OutlinedButton(
                        onClick = { showJoinDialog = true },
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color.White),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Unirse a un grupo", fontSize = 16.sp, color= Color.Black)
                    }
                    Spacer(modifier = Modifier.height(100.dp))
                }

                item {
                    Log.d("HomeScreen", "Showing invite friends section")
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF333333), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "friends",
                            tint = Color(0xFFFFD54F)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Invita a 5 amigos para no tener anuncios", color = Color.White)
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        Log.d("HomeScreen", "Displaying create group dialog")
        ShowCreateGroupDialog(
            onDismiss = { showCreateDialog = false },
            navController = navController
        )
    }

    if (showJoinDialog) {
        Log.d("HomeScreen", "Displaying join group dialog")
        ShowJoinGroupDialog(
            userId = userID,
            navController = navController,
            onDismiss = { showJoinDialog = false }
        )
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
                    scope.launch {
                        db.collection("groups").document(groupIdInput).get()
                            .addOnSuccessListener { doc ->
                                if (doc.exists()) {
                                    onDismiss()
                                    navController.navigate(Screen.YourNameJoin.createRoute(groupIdInput))
                                } else {
                                    errorMessage = "No se encontró el grupo"
                                }
                            }
                            .addOnFailureListener {
                                errorMessage = "Error al buscar grupo"
                            }
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
fun YourNameJoin(navController: NavController, groupID: String, auth: FirebaseAuth, groupViewModel: GroupViewModel) {
    val context = LocalContext.current
    val userID = auth.currentUser?.uid ?: return
    var userName by remember { mutableStateOf("") }
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
                val groupRef = db.collection("groups").document(groupID)

                // Crear los datos como mapa plano
                val memberDataMap = mapOf(
                    "name" to userName,
                    "phase" to 1,
                    "challengesCompleted" to emptyMap<String, Boolean>(),
                    "phaseStartDate" to Timestamp.now(),
                    "etapa" to "Previa"
                )

                groupRef.update("members.$userID", memberDataMap).addOnSuccessListener {
                    val userGroupRef = db.collection("users").document(userID)
                        .collection("groups").document(groupID)

                    userGroupRef.set(hashMapOf("groupId" to groupID)).addOnSuccessListener {
                        groupViewModel.setGroupId(groupID)
                        navController.navigate(Screen.Group.createRoute(groupID))
                    }.addOnFailureListener {
                        Toast.makeText(
                            context,
                            "Error al guardar en el usuario",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }.addOnFailureListener {
                    Toast.makeText(context, "Error al unirse al grupo", Toast.LENGTH_SHORT).show()
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
fun YourName(navController: NavController, groupName: String, auth: FirebaseAuth, groupViewModel: GroupViewModel) {
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
                        groupViewModel.setGroupId(groupID)
                        navController.navigate(Screen.Group.createRoute(groupRef.id))
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