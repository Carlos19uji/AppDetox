package com.example.detoxapp

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.AlertDialog
import androidx.compose.material.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await



@Composable
fun TopBarGroup(
    navController: NavController,
    groupViewModel: GroupViewModel,
    auth: FirebaseAuth,
    modifier: Modifier = Modifier
) {
    val expanded = remember { mutableStateOf(false) }
    val showLogOutDialog = remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .height(56.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White,
                    modifier = Modifier.clickable {
                        groupViewModel.setGroupId(null)
                        navController.popBackStack(Screen.Home.route, false)
                        navController.navigate(Screen.Home.route)
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Grupos",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.clickable {
                        groupViewModel.setGroupId(null)
                        navController.popBackStack(Screen.Home.route, false)
                        navController.navigate(Screen.Home.route)
                    }
                )
            }

            Spacer(modifier = Modifier.width(250.dp))

            TopBarMenu(navController, auth, expanded, showLogOutDialog)
        }
    }
}

@Composable
fun HomeTopBar(
    navController: NavController,
    auth: FirebaseAuth,
    modifier: Modifier = Modifier
) {
    val expanded = remember { mutableStateOf(false) }
    val showLogOutDialog = remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .height(56.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "DetoxApp",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )

            Spacer(modifier = Modifier.width(260.dp))

            TopBarMenu(navController, auth, expanded, showLogOutDialog)
        }
    }
}

@Composable
fun TopBarMenu(
    navController: NavController,
    auth: FirebaseAuth,
    expanded: MutableState<Boolean>,
    showLogOutDialog: MutableState<Boolean>
) {
    Icon(
        imageVector = Icons.Default.Menu,
        contentDescription = "Menu",
        tint = Color.White,
        modifier = Modifier.clickable { expanded.value = true }
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false },
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            DropdownMenuItem(
                text = { Text("Editar perfil") },
                onClick = {
                    navController.navigate(Screen.EditProfile.route)
                    expanded.value = false
                }
            )
            DropdownMenuItem(
                text = { Text("Grupos")},
                onClick = {
                    navController.navigate(Screen.Home.route)
                    expanded.value = false
                }
            )
            DropdownMenuItem(
                text = { Text("Cerrar Sesión") },
                onClick = {
                    showLogOutDialog.value = true
                    expanded.value = false
                }
            )
        }
    }

    if (showLogOutDialog.value) {
        LogOutConfirmationDialog(
            onConfirm = {
                auth.signOut()
                navController.navigate(Screen.Start.route) {
                    popUpTo(Screen.Start.route) { inclusive = true }
                }
                showLogOutDialog.value = false
            },
            onCancel = {
                showLogOutDialog.value = false
            }
        )
    }
}


@Composable
fun LogOutConfirmationDialog(onConfirm: () -> Unit, onCancel: () -> Unit){

    AlertDialog(
        onDismissRequest = { onCancel },
        title =  { Text("¿Estas seguro de que quieres cerrar sesion?") },
        confirmButton = {
            TextButton( onClick = onConfirm) {
                Text("Si")
            }
        },
        dismissButton = {
            TextButton( onClick = onCancel) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun BottomBar(
    navController: NavController,
    groupViewModel: GroupViewModel,
    auth: FirebaseAuth,
    modifier: Modifier = Modifier
) {
    val groupId = groupViewModel.groupId.value ?: run {
        Log.e("BottomBar", "Group ID is null, cannot render bottom bar")
        return
    }

    val userId = auth.currentUser?.uid ?: run {
        Log.e("BottomBar", "User ID is null, cannot render bottom bar")
        return
    }

    val db = FirebaseFirestore.getInstance()
    val coroutineScope = rememberCoroutineScope()
    var memberData = remember { mutableStateOf<MemberData?>(null) }

    val onPhaseClick: () -> Unit = {
        coroutineScope.launch {
            Log.d("BottomBar", "Fetching member data for phase button click for user $userId in group $groupId")
            val groupDoc = db.collection("groups").document(groupId).get().await()
            val membersMap = groupDoc.toObject(GroupData::class.java)?.members
            val rawMember = membersMap?.get(userId)

            if (rawMember != null) {
                val updatedMemberData = rawMember as MemberData
                memberData.value = updatedMemberData
                val etapa = updatedMemberData.etapa
                Log.d("BottomBar", "Phase button clicked, etapa = $etapa")

                when (etapa) {
                    "Intro" -> {
                        Log.d("BottomBar", "Navigating to PhaseIntroScreen")
                        navController.navigate(Screen.PhaseIntroScreen.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                    "Objectives" -> {
                        Log.d("BottomBar", "Navigating to Objectives")
                        navController.navigate(Screen.Objectives.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                    "End" -> {
                        Log.d("BottomBar", "Navigating to PhaseEndScreen")
                        navController.navigate(Screen.PhaseEndScreen.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                    "Previa" -> {
                        Log.d("BottomBar", "Navigating to Previa")
                        navController.navigate(Screen.Previa.route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                    else -> {
                        Log.w("BottomBar", "Etapa is null or unrecognized: $etapa")
                    }
                }
            } else {
                Log.e("BottomBar", "Member data is null or not found when clicking phase button")
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Home button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable {
                Log.d("BottomBar", "Navigating to Home (Group)")
                navController.navigate(Screen.Group.route) {
                    popUpTo(navController.graph.startDestinationId)
                    launchSingleTop = true
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Home",
                tint = if (navController.currentDestination?.route == Screen.Group.route) Color.White else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Text(
                "Home",
                color = if (navController.currentDestination?.route == Screen.Group.route) Color.White else Color.Gray,
                fontSize = 12.sp
            )
        }

        // Stats button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable {
                Log.d("BottomBar", "Navigating to Stats")
                navController.navigate(Screen.Stats.route) {
                    popUpTo(navController.graph.startDestinationId)
                    launchSingleTop = true
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Estadísticas",
                tint = if (navController.currentDestination?.route == Screen.Stats.route) Color.White else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Text(
                "Estadísticas",
                color = if (navController.currentDestination?.route == Screen.Stats.route) Color.White else Color.Gray,
                fontSize = 12.sp
            )
        }

        // Fase button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable {
                Log.d("BottomBar", "Phase button clicked")
                onPhaseClick()
            }
        ) {
            val currentRoute = navController.currentDestination?.route

            val faseColor = if (currentRoute == Screen.Previa.route ||
                currentRoute == Screen.PhaseIntroScreen.route ||
                currentRoute == Screen.Objectives.route ||
                currentRoute == Screen.PhaseEndScreen.route) {
                Color.White
            } else {
                Color.Gray
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Objetivos",
                tint = faseColor,
                modifier = Modifier.size(24.dp)
            )
            Text(
                "Fase",
                color = faseColor,
                fontSize = 12.sp
            )
        }

        // Ranking button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable {
                Log.d("BottomBar", "Navigating to Ranking")
                navController.navigate(Screen.Ranking.route) {
                    popUpTo(navController.graph.startDestinationId)
                    launchSingleTop = true
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Ranking",
                tint = if (navController.currentDestination?.route == Screen.Ranking.route) Color.White else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Text(
                "Ranking",
                color = if (navController.currentDestination?.route == Screen.Ranking.route) Color.White else Color.Gray,
                fontSize = 12.sp
            )
        }

        // Messages button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable {
                Log.d("BottomBar", "Navigating to Messages")
                navController.navigate(Screen.Messages.route) {
                    popUpTo(navController.graph.startDestinationId)
                    launchSingleTop = true
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Reflexiones",
                tint = if (navController.currentDestination?.route == Screen.Messages.route) Color.White else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Text(
                "Reflexiones",
                color = if (navController.currentDestination?.route == Screen.Messages.route) Color.White else Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}