package com.carlosrmuji.detoxapp


import android.app.Activity
import android.app.DatePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.carlosrmuji.detoxapp.Billing.AdViewModel
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.Timestamp


@Composable
fun EditProfile(
    navController: NavController,
    groupViewModel: GroupViewModel,
    auth: FirebaseAuth,
    adViewModel: AdViewModel
) {
    val userId = auth.currentUser?.uid ?: return

    val context = LocalContext.current
    val storage = Firebase.storage
    val db = FirebaseFirestore.getInstance()

    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf<Date?>(null) }
    var profileImageUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    var dateState by remember { mutableStateOf("Seleccionar fecha") }

    val calendar = Calendar.getInstance()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> profileImageUri = uri }

    val genders = listOf("Hombre", "Mujer", "Otro/Prefiero no especificar")
    val countries = listOf(
        "Argentina", "Bolivia", "Chile", "Colombia", "Costa Rica", "Cuba", "Ecuador", "El Salvador",
        "España", "Guatemala", "Honduras", "México", "Nicaragua", "Panamá", "Paraguay", "Perú",
        "Puerto Rico", "República Dominicana", "Uruguay", "Venezuela", "Guinea Ecuatorial"
    )

    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                birthDate = calendar.time
                dateState = dateFormat.format(calendar.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    var genderExpanded by remember { mutableStateOf(false) }
    var countryExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val userDoc = db.collection("users").document(userId).get().await()
        name = userDoc.getString("name") ?: ""
        gender = userDoc.getString("gender") ?: ""
        country = userDoc.getString("country") ?: ""

        (userDoc.get("birthDate") as? Timestamp)?.toDate()?.let {
            birthDate = it
            dateState = dateFormat.format(it)
        }

        (userDoc.get("profileImageUrl") as? String)?.let {
            profileImageUri = Uri.parse(it)
        }
    }

    fun guardarCambios() {
        fun uploadData(imageUrl: String?) {
            val userData = mutableMapOf<String, Any>()
            if (name.isNotEmpty()) userData["name"] = name
            if (gender.isNotEmpty()) userData["gender"] = gender
            if (country.isNotEmpty()) userData["country"] = country
            if (birthDate != null) userData["birthDate"] = Timestamp(birthDate!!)
            if (imageUrl != null) userData["profileImageUrl"] = imageUrl

            if (userData.isEmpty()) {
                Toast.makeText(context, "No hay cambios para guardar", Toast.LENGTH_SHORT).show()
                isLoading = false
                return
            }

            db.collection("users").document(userId)
                .update(userData)
                .addOnSuccessListener {
                    isLoading = false
                    navController.popBackStack()
                }
                .addOnFailureListener {
                    isLoading = false
                    Toast.makeText(context, "Error al guardar", Toast.LENGTH_SHORT).show()
                }
        }

        if (profileImageUri != null && !profileImageUri.toString().startsWith("https://")) {
            val ref = storage.reference.child("users/$userId/profile.jpg")
            ref.putFile(profileImageUri!!)
                .continueWithTask { task ->
                    if (!task.isSuccessful) throw task.exception ?: Exception("Error en subida")
                    ref.downloadUrl
                }
                .addOnSuccessListener { uri -> uploadData(uri.toString()) }
                .addOnFailureListener {
                    isLoading = false
                    Toast.makeText(context, "Error al subir imagen", Toast.LENGTH_SHORT).show()
                }
        } else {
            uploadData(profileImageUri?.toString())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Editar Perfil",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            item {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = 0.3f))
                        .clickable { launcher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (profileImageUri != null) {
                        Image(
                            painter = rememberAsyncImagePainter(profileImageUri),
                            contentDescription = "Foto de perfil",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text("Foto", color = Color.LightGray)
                    }
                }
            }

            item {
                InputField(label = "Nombre", value = name, onValueChange = { name = it })
            }

            item {
                Column {
                    Text("Género", color = Color.White, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { genderExpanded = true }
                            .background(Color.White, shape = RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(gender.ifEmpty { "Seleccionar género" }, color = Color.Black)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Black)
                        }
                    }
                    DropdownMenu(
                        expanded = genderExpanded,
                        onDismissRequest = { genderExpanded = false }
                    ) {
                        genders.forEach {
                            DropdownMenuItem(
                                text = { Text(it) },
                                onClick = {
                                    gender = it
                                    genderExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Column {
                    Text("País", color = Color.White, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { countryExpanded = true }
                            .background(Color.White, shape = RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(country.ifEmpty { "Seleccionar país" }, color = Color.Black)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Black)
                        }
                    }
                    DropdownMenu(
                        expanded = countryExpanded,
                        onDismissRequest = { countryExpanded = false }
                    ) {
                        countries.forEach {
                            DropdownMenuItem(
                                text = { Text(it) },
                                onClick = {
                                    country = it
                                    countryExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Column {
                    Text("Fecha de nacimiento", color = Color.White, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { datePickerDialog.show() }
                            .background(Color.White, shape = RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(dateState, color = Color.Black)
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = Color.Black)
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        isLoading = true
                        val activity = context as? Activity
                        val ad = adViewModel.editProfileInterstitialAd

                        if (ad != null && activity != null) {
                            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                                override fun onAdDismissedFullScreenContent() {
                                    adViewModel.clearEditProfileAd()
                                    adViewModel.loadEditProfileAd()
                                    guardarCambios()
                                }

                                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                    guardarCambios()
                                }
                            }
                            ad.show(activity)
                        } else {
                            guardarCambios()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Guardar cambios")
                }
            }

            if (isLoading) {
                item {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun InputField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, shape = RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = Color.Black, fontSize = 16.sp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text("Escribe tu nombre", color = Color.Gray)
                    }
                    innerTextField()
                }
            )
        }
    }
}