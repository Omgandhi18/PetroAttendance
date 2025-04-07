package com.petroattendance

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// This class handles the Firebase initialization
class FirebaseManager(private val context: Context) {

    fun initialize() {
        try {
            FirebaseApp.initializeApp(context)
        } catch (e: Exception) {
            // Firebase already initialized
        }
    }

    companion object {
        private var instance: FirebaseManager? = null

        fun getInstance(context: Context): FirebaseManager {
            if (instance == null) {
                instance = FirebaseManager(context)
            }
            return instance!!
        }
    }
}

// Data classes for user management
data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "employee"
)

// Authentication Repository
class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    suspend fun signIn(email: String, password: String): Result<User> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val userId = authResult.user?.uid ?: return Result.failure(Exception("Authentication failed"))

            // Get user details from Firestore
            var userDoc = db.collection("users").document(userId).get().await()

            if (!userDoc.exists()) {
                userDoc = db.collection("admin").document(userId).get().await()
            }
            if (userDoc.exists()) {
                val user = User(
                    id = userId,
                    name = userDoc.getString("name") ?: "",
                    email = userDoc.getString("email") ?: "",
                    phone = userDoc.getString("phone") ?: "",
                    role = userDoc.getString("role") ?: "employee"
                )
                Result.success(user)
            } else {
                Result.failure(Exception("User data not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }

    fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        return User(
            id = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            name = firebaseUser.displayName ?: ""
        )
    }
    suspend fun getUserRole(userId: String): String? {
        return try {
            var userDoc = db.collection("users").document(userId).get().await()

            // If not found, check in admin collection
            if (!userDoc.exists()) {
                userDoc = db.collection("admin").document(userId).get().await()
            }

            userDoc.getString("role")
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getUserFromFirestore(userId: String): User? {
        return try {
            var userDoc = db.collection("users").document(userId).get().await()

            // If not found, check in admin collection
            if (!userDoc.exists()) {
                userDoc = db.collection("admin").document(userId).get().await()
            }

            if (userDoc.exists()) {
                User(
                    id = userId,
                    name = userDoc.getString("name") ?: "",
                    email = userDoc.getString("email") ?: "",
                    phone = userDoc.getString("phone") ?: "",
                    role = userDoc.getString("role") ?: "employee"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    // Admin functions to create new employees
    suspend fun createEmployee(name: String, email: String, password: String, phone: String): Result<String> {
        return try {
            // Create authentication account
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = authResult.user?.uid ?: return Result.failure(Exception("User creation failed"))

            // Create user document in Firestore
            val user = hashMapOf(
                "name" to name,
                "email" to email,
                "phone" to phone,
                "role" to "employee"
            )

            db.collection("users").document(userId).set(user).await()

            Result.success(userId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Login Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val auth = remember { AuthRepository() }
    val coroutineScope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Initialize Firebase
    LaunchedEffect(Unit) {
        FirebaseManager.getInstance(context).initialize()

        // Check if user is already logged in (handles case of direct access to login page)
        val currentUser = auth.getCurrentUser()
        if (currentUser != null) {
            coroutineScope.launch {
                val userRole = auth.getUserRole(currentUser.id)
                if (userRole == "admin") {
                    navController.navigate(Screen.AdminHome.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                } else {
                    navController.navigate(Screen.MarkAttendance.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Petrol Pump Attendance",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                coroutineScope.launch {
                    isLoading = true
                    errorMessage = null

                    try {
                        val result = auth.signIn(email, password)
                        if (result.isSuccess) {
                            val user = result.getOrNull()

                            // Navigate based on user role
                            if (user?.role == "admin") {
                                navController.navigate(Screen.AdminHome.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            } else {
                                navController.navigate(Screen.MarkAttendance.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            }
                        } else {
                            errorMessage = result.exceptionOrNull()?.message ?: "Authentication failed"
                        }
                    } catch (e: Exception) {
                        errorMessage = "Error: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Login")
            }
        }

        errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}