package com.carlosrmuji.detoxapp

import android.content.Context
import android.util.Log
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun FirstScreen(onLoginClick: ()-> Unit, onCreateAccountClick: () -> Unit){
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), // Fondo negro
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(150.dp))
        Text(
            text = "DetoxApp",
            color = Color.White, // Texto blanco
            fontSize = 35.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(250.dp))

        Button(
            onClick = onLoginClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444)), // Gris oscuro
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .padding(vertical = 8.dp)
        ) {
            Text(text = "Iniciar Sesion", color = Color.White, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onCreateAccountClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF888888)), // Gris medio
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .padding(vertical = 8.dp)
        ) {
            Text(text = "Crear Cuenta", color = Color.White, fontSize = 18.sp)
        }
    }
}

@Composable
fun LoginScreen(
    auth: FirebaseAuth,
    navController: NavController,
    onCreateAccountClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onGoogleSignIn: () -> Unit
){
    val emailState = remember { mutableStateOf("") }
    val passwordState = remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val (savedEmail, savedPassword) = SecurePrefs.getCredentials(context)
        emailState.value = savedEmail
        passwordState.value = savedPassword
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), // Fondo negro
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(30.dp))

        email(emailState)
        password(passwordState)

        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = {
                if (emailState.value.isNotEmpty() && passwordState.value.isNotEmpty()){
                    auth.signInWithEmailAndPassword(emailState.value, passwordState.value)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful){
                                SecurePrefs.saveCredentials(context, emailState.value, passwordState.value)
                                navController.navigate(Screen.Stats.route)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Password or e-mail incorrect: ${task.exception?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                } else {
                    Toast.makeText(
                        context,
                        "Please write your e-mail and password",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF888888)), // Gris medio
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .padding(vertical = 8.dp)
        ) {
            Text(text = "Iniciar Sesion", color = Color.White, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(30.dp))

        GoogleSignInButton("Inicio de sesion con Google", onGoogleSignIn)

        Spacer(modifier = Modifier.height(60.dp))

        Text(
            text = "Todavia no tienes una cuenta?",
            fontSize = 18.sp,
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally)
                .padding(5.dp)
        )

        TextButton(onClick = onCreateAccountClick) {
            Text(
                text = "Crear Cuenta",
                color = Color(0xFF888888), // Gris medio
                fontSize = 18.sp
            )
        }
        TextButton(onClick = onForgotPasswordClick) {
            Text(text = "Has olvidado tu contraseña?", color = Color(0xFF888888), fontSize = 18.sp)
        }
    }
}

@Composable
fun CreateAccount(
    onLoginClick: () -> Unit,
    navController: NavController,
    auth: FirebaseAuth,
    onGoogleSignIn: () -> Unit
) {
    var emailState = remember { mutableStateOf("") }
    var passwordState = remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black), // Fondo negro
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(30.dp))

        email(emailState)
        password(passwordState)

        Spacer(modifier = Modifier.height(30.dp))
        Button(
            onClick = {
                if (emailState.value.isNotEmpty() && passwordState.value.isNotEmpty()) {
                    auth.createUserWithEmailAndPassword(
                        emailState.value,
                        passwordState.value
                    ).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val userId = task.result?.user?.uid
                            val user = auth.currentUser
                            if (userId != null) {
                                val db = FirebaseFirestore.getInstance()
                                val userDoc = db.collection("users").document(userId)
                                val userInfo = mapOf(
                                    "email" to emailState.value,
                                    "password" to passwordState.value
                                )

                                userDoc.set(userInfo).addOnSuccessListener {

                                    Log.d("Firestore", "User data saved successfully!")


                                    val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                                    val now = Calendar.getInstance()
                                    val today = dateFormat.format(now.time)

                                    now.add(Calendar.DAY_OF_YEAR, 7)
                                    val renovationDate = dateFormat.format(now.time)

                                    // ③ Crear subcolección IA/tokens
                                    val iaTokensData = mapOf(
                                        "tokens" to 5,
                                        "tokens_start" to today,
                                        "token_renovation" to renovationDate
                                    )

                                    db.collection("users")
                                        .document(userId)
                                        .collection("IA")
                                        .document("tokens") // puedes cambiar "tokens" por cualquier otro ID
                                        .set(iaTokensData)
                                        .addOnSuccessListener {
                                            Log.d("Firestore", "IA token info saved successfully!")
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("Firestore", "Error saving IA data: ${e.message}")
                                        }

                                    val planData = mapOf("plan" to "base_plan")

                                    db.collection("users").document(userId)
                                        .collection("plan").document("plan")
                                        .set(planData)
                                        .addOnSuccessListener {
                                            Log.d("Firestore", "Plan info saved successfully!")

                                            // --- Enviar correo de verificación ---
                                            // Llamamos a sendEmailVerification() sobre el usuario recién creado.
                                            user?.sendEmailVerification()
                                                ?.addOnCompleteListener { emailTask ->
                                                    if (emailTask.isSuccessful) {
                                                        Log.d("Email", "Verificación enviada a ${emailState.value}")
                                                    } else {
                                                        Log.e("Email", "Error enviando verificacion: ${emailTask.exception?.message}")
                                                    }

                                                    // Navegar después de intentar enviar el correo (ajusta si quieres otro comportamiento)
                                                    navController.navigate(Screen.Stats.route)
                                                }
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("Firestore", "Error saving plan data: ${e.message}")
                                        }


                                }.addOnFailureListener { e ->
                                    Log.e("Firestore", "Error saving user data: ${e.message}")
                                }
                            }
                        } else {
                            Log.e("Auth", "Error creating user: ${task.exception?.message}")
                            Toast.makeText(
                                context,
                                "Error creando usuario: ${task.exception?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Introduce email y contraseña", Toast.LENGTH_LONG).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF888888)), // Gris medio
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .padding(vertical = 8.dp)
        ) {
            Text(text = "Crear Cuenta", color = Color.White, fontSize = 18.sp)
        }

        Spacer(modifier = Modifier.height(30.dp))

        GoogleSignInButton("Inicio de sesion con Google", onGoogleSignIn)

        Spacer(modifier = Modifier.height(60.dp))

        Text(
            text = "Ya tienes una cuenta?",
            fontSize = 18.sp,
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally)
                .padding(5.dp)
        )

        TextButton(onClick = { navController.navigate(Screen.Login.route) }) {
            Text(
                text = "Iniciar Sesion",
                color = Color(0xFF888888), // Gris medio
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun PasswordRecovery(auth: FirebaseAuth, navController: NavController){
    val emailSent = remember { mutableStateOf(false) }
    val errorMessage = remember { mutableStateOf("") }
    val emailState = remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(75.dp))

        Text(
            text = "Introduce tu email para recuperar tu contraseña",
            fontSize = 18.sp,
            color = Color.White,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(75.dp))
        email(emailState)
        Spacer(modifier = Modifier.height(50.dp))

        Button(
            onClick = {
                if (emailState.value.isNotEmpty()) {
                    auth.sendPasswordResetEmail(emailState.value)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                emailSent.value = true
                                errorMessage.value = ""
                            } else {
                                errorMessage.value =
                                    task.exception?.message ?: "Error sending email"
                            }
                        }
                } else {
                    errorMessage.value = "Please enter your email address"
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF888888)), // Gris medio
            shape = RoundedCornerShape(50),
            modifier = Modifier.padding(vertical = 8.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            Text(text = "Enviar", color = Color.White, fontSize = 18.sp)
        }
        if (emailSent.value) {
            Text(
                text = "Email para recuperar tu contraseña enviado. Compruebe su email",
                color = Color.White
            )
        }
        if (errorMessage.value.isNotEmpty()) {
            Text(text = errorMessage.value, color= Color.Red)
        }

        Spacer(modifier = Modifier.height(50.dp))

        TextButton(onClick = { navController.navigate(Screen.Login.route) }) {
            Text(
                text = "Iniciar Sesion",
                color = Color(0xFF888888), // Gris medio
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun email(emailState: MutableState<String>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Email,
            contentDescription = "Email icon",
            modifier = Modifier.size(28.dp),
            tint = Color.Gray
        )

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f) // Ancho fijo relativo
                .background(Color(0xFF444444), RoundedCornerShape(8.dp))
                .height(56.dp) // Altura fija
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = emailState.value,
                onValueChange = { emailState.value = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = Color.White),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (emailState.value.isEmpty()) {
                        Text("you@example.com", color = Color.LightGray)
                    }
                    innerTextField()
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
}





@Composable
fun password(passwordState: MutableState<String>) {
    var passwordVisible by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Padlock",
            modifier = Modifier.size(28.dp),
            tint = Color.Gray
        )

        Spacer(modifier = Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f) // Ancho fijo relativo
                .background(Color(0xFF444444), RoundedCornerShape(8.dp))
                .height(56.dp) // Altura fija
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicTextField(
                    value = passwordState.value,
                    onValueChange = { passwordState.value = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    textStyle = TextStyle(color = Color.White),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    decorationBox = { innerTextField ->
                        if (passwordState.value.isEmpty()) {
                            Text("Contraseña", color = Color.LightGray)
                        }
                        innerTextField()
                    }
                )

                Icon(
                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle password visibility",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { passwordVisible = !passwordVisible },
                    tint = Color.Gray
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

object SecurePrefs{

    private const val PREFS_FILENAME = "user_credentials"
    private const val KEY_EMAIL = "email"
    private const val KEY_PASSWORD = "password"

    private fun getPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_FILENAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveCredentials(context: Context, email: String, password: String){
        val prefs = getPrefs(context)
        prefs.edit().putString(KEY_EMAIL, email).putString(KEY_PASSWORD, password).apply()
    }

    fun getCredentials(context: Context): Pair<String, String>{
        val prefs = getPrefs(context)
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val password = prefs.getString(KEY_PASSWORD, "") ?: ""
        return email to password
    }

    fun clearCredentials(context: Context){
        val prefs = getPrefs(context)
        prefs.edit().clear().apply()
    }
}