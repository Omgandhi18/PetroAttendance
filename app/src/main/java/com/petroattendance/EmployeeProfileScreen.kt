package com.petroattendance

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

// Compatibility helper
object CompatUtils {
    val isNewAndroid = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S // Android 12+

    // Define fallback colors
    val primaryColor = Color(0xFF6200EE)
    val primaryVariantColor = Color(0xFF3700B3)
    val secondaryColor = Color(0xFF03DAC6)
    val secondaryVariantColor = Color(0xFF018786)
    val surfaceColor = Color.White
    val backgroundColor = Color.White
    val errorColor = Color(0xFFB00020)
    val errorContainerColor = Color(0xFFFFDAD6)
    val onErrorContainerColor = Color(0xFFB3261E)
    val secondaryContainerColor = Color(0xFFCEFAF8)
    val onSecondaryContainerColor = Color(0xFF005048)
    val onSurfaceVariantColor = Color(0xFF7A7A7A)

    // Legacy style helpers
    val headlineMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp
    )

    val titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )

    val headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    )

    val bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )

    val bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )

    // Icon compatibility
    fun getLogoutIcon(): ImageVector {
        return if (isNewAndroid) Icons.Filled.Logout else Icons.Filled.ExitToApp
    }
}

@Composable
fun ProfileScreen(navController: NavController, padding: PaddingValues, isVisible: Boolean) {
    val coroutineScope = rememberCoroutineScope()
    val auth = remember { AuthRepository() }
    val currentUser = auth.getCurrentUser()
    val db = remember { FirebaseFirestore.getInstance() }
    var name by remember {
        mutableStateOf("")
    }
    var attendanceCount by remember { mutableStateOf(0) }
    val getUserData = db.collection("users")
    var wasVisible by remember { mutableStateOf(false) }
    fun getMonthRangeStrings(year: Int, month: Int): Pair<String, String> {
        // Format year and month (month is 1-based in parameters)
        val monthStr = month.toString()

        // Create start date string (first day of month)
        val startString = "$year-$monthStr-1"

        // Calculate the last day of the month
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1) // Month is 0-based in Calendar
        val lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        // Create end date string (last day of month)
        val endString = "$year-$monthStr-$lastDay"

        return Pair(startString, endString)
    }
    // Get attendance count
    LaunchedEffect(Unit) {
        if (currentUser?.id != null) {
            try {
                // Get user name
                val userDoc = getUserData.document(currentUser.id).get().await()
                println(userDoc)
                if (userDoc != null && userDoc.exists()) {
                    name = userDoc.getString("name") ?: "N/A"
                } else {
                    name = "N/A"
                }
                wasVisible = true

            } catch (e: Exception) {
                // Handle error
            }
        }
        wasVisible = isVisible
    }

    // Define the surface color with compatibility fallback
    val surfaceColor = if (CompatUtils.isNewAndroid) {
        MaterialTheme.colorScheme.background
    } else {
        CompatUtils.backgroundColor
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = surfaceColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Employee Profile",
                style = if (CompatUtils.isNewAndroid) MaterialTheme.typography.headlineMedium else CompatUtils.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Profile picture with compatible primary color
            val primaryColorWithAlpha = if (CompatUtils.isNewAndroid) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            } else {
                CompatUtils.primaryColor.copy(alpha = 0.1f)
            }

            val primaryColor = if (CompatUtils.isNewAndroid) {
                MaterialTheme.colorScheme.primary
            } else {
                CompatUtils.primaryColor
            }

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(primaryColorWithAlpha),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "Profile Picture",
                    modifier = Modifier.size(80.dp),
                    tint = primaryColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // User info card with elevation compatibility
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                elevation = if (CompatUtils.isNewAndroid) {
                    CardDefaults.cardElevation(defaultElevation = 2.dp)
                } else {
                    CardDefaults.cardElevation(defaultElevation = 2.dp)
                }
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    ProfileInfoRow(
                        label = "Name",
                        value = name,
                        icon = Icons.Filled.Person
                    )

                    if (CompatUtils.isNewAndroid) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    } else {
                        // Fallback divider using Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .padding(vertical = 12.dp)
                                .background(Color.LightGray)
                        )
                    }

                    ProfileInfoRow(
                        label = "Employee ID",
                        value = currentUser?.id?.take(8)?.uppercase() ?: "N/A",
                        icon = Icons.Filled.Badge
                    )

                    if (CompatUtils.isNewAndroid) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    } else {
                        // Fallback divider using Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .padding(vertical = 12.dp)
                                .background(Color.LightGray)
                        )
                    }

                    ProfileInfoRow(
                        label = "Email",
                        value = currentUser?.email ?: "N/A",
                        icon = Icons.Filled.Email
                    )
                }
            }

            // Attendance stats card with compatible colors
            val secondaryContainerColor = if (CompatUtils.isNewAndroid) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                CompatUtils.secondaryContainerColor
            }

            val onSecondaryContainerColor = if (CompatUtils.isNewAndroid) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                CompatUtils.onSecondaryContainerColor
            }
            // Logout button with compatible colors
            val errorContainerColor = if (CompatUtils.isNewAndroid) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                CompatUtils.errorContainerColor
            }

            val onErrorContainerColor = if (CompatUtils.isNewAndroid) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                CompatUtils.onErrorContainerColor
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        auth.signOut()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = errorContainerColor,
                    contentColor = onErrorContainerColor
                )
            ) {
                Icon(CompatUtils.getLogoutIcon(), contentDescription = "Logout")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Sign Out",
                    style = if (CompatUtils.isNewAndroid) MaterialTheme.typography.titleMedium else CompatUtils.titleMedium
                )
            }
        }
    }
}

@Composable
fun ProfileInfoRow(label: String, value: String, icon: ImageVector) {
    val primaryColor = if (CompatUtils.isNewAndroid) {
        MaterialTheme.colorScheme.primary
    } else {
        CompatUtils.primaryColor
    }

    val onSurfaceVariantColor = if (CompatUtils.isNewAndroid) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        CompatUtils.onSurfaceVariantColor
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = primaryColor,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = label,
                style = if (CompatUtils.isNewAndroid) MaterialTheme.typography.bodySmall else CompatUtils.bodySmall,
                color = onSurfaceVariantColor
            )

            Text(
                text = value,
                style = if (CompatUtils.isNewAndroid) MaterialTheme.typography.bodyLarge else CompatUtils.bodyLarge
            )
        }
    }
}