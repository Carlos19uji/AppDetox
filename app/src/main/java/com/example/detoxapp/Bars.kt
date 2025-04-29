package com.example.detoxapp

import android.util.Log
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
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
fun TopBarGroup(navController: NavController, groupViewModel: GroupViewModel) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .height(56.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Volver",
                tint = Color.White,
                modifier = Modifier
                    .clickable {
                        groupViewModel.setGroupId(null)
                        navController.popBackStack(route = Screen.Home.route, inclusive = false)
                        navController.navigate(Screen.Home.route)
                    }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Grupos",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .clickable {
                        groupViewModel.setGroupId(null)
                        navController.popBackStack(route = Screen.Home.route, inclusive = false)
                        navController.navigate(Screen.Home.route)
                    }
            )
        }
    }
}

@Composable
fun BottomBar(navController: NavController, groupViewModel: GroupViewModel, auth: FirebaseAuth) {
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
        modifier = Modifier
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

        // Phase button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable {
                Log.d("BottomBar", "Phase button clicked")
                onPhaseClick()
            }
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Objetivos",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Text(
                "Fase",
                color = Color.White,
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
                contentDescription = "Messages",
                tint = if (navController.currentDestination?.route == Screen.Messages.route) Color.White else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Text(
                "Messages",
                color = if (navController.currentDestination?.route == Screen.Messages.route) Color.White else Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}