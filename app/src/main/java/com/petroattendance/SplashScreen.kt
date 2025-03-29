package com.petroattendance
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

@Composable
fun SplashScreen(navController: NavController) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository() }
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Initialize Firebase
    LaunchedEffect(Unit) {
        FirebaseManager.getInstance(context).initialize()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Your app logo would go here
        // Image(
        //    painter = painterResource(id = R.drawable.app_logo),
        //    contentDescription = "App Logo",
        //    modifier = Modifier.size(150.dp)
        // )

        // For now, using text as placeholder
        Text(
            text = "Arvindkant Gandhi Attendance",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )

        // Loading indicator at the bottom
        if (isLoading) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Error message if any
        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        }

        // Check current Firebase user and navigate accordingly
        LaunchedEffect(key1 = true) {
            delay(1500) // Short delay for splash screen visibility

            try {
                val currentUser = authRepository.getCurrentUser()

                if (currentUser != null) {
                    // User is logged in, determine role
                    val db = FirebaseFirestore.getInstance()
                    val userDoc = db.collection("users").document(currentUser.id).get().await()

                    if (userDoc.exists()) {
                        val role = userDoc.getString("role") ?: "employee"

                        // Navigate based on user role
                        if (role == "admin") {
                            navController.navigate(Screen.AdminHome.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        } else {
                            navController.navigate(Screen.MarkAttendance.route) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        }
                    } else {
                        // User document doesn't exist, go to login
                        navigateToLogin(navController)
                    }
                } else {
                    // No user is logged in, go to login
                    navigateToLogin(navController)
                }
            } catch (e: Exception) {
                errorMessage = "Error initializing: ${e.message}"
                delay(2000) // Show error for a moment
                navigateToLogin(navController)
            } finally {
                isLoading = false
            }
        }
    }
}

private fun navigateToLogin(navController: NavController) {
    navController.navigate(Screen.Login.route) {
        popUpTo(Screen.Splash.route) { inclusive = true }
    }
}