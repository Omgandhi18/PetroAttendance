package com.petroattendance

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun EmployeeMainScreen(navController: NavController) {
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                    label = { Text("Profile") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> EnhancedAttendanceScreen(navController, padding, isVisible = selectedTab == 0)
            1 -> ProfileScreen(navController, padding, isVisible = selectedTab == 1)
        }
    }
}

@SuppressLint("MissingPermission")
class LocationClient(private val context: android.content.Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                currentLocation = location
            }
        }
    }

    private var locationRequest: LocationRequest? = null

    var currentLocation: Location? = null
        private set(value) {
            field = value
            locationUpdateListener?.invoke(value)
        }

    var locationUpdateListener: ((Location?) -> Unit)? = null

    fun startLocationUpdates(intervalMs: Long = 10000) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        locationRequest = LocationRequest.Builder(intervalMs)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        locationRequest?.let {
            fusedLocationClient.requestLocationUpdates(
                it,
                locationCallback,
                context.mainLooper
            )
        }

        // Get initial location
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                currentLocation = location
            }
        }
    }

    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

@SuppressLint("MissingPermission")
@Composable
fun EnhancedAttendanceScreen(navController: NavController, padding: PaddingValues, isVisible: Boolean) {
    val context = LocalContext.current
    val locationClient = remember { LocationClient(context) }
    val db = remember { FirebaseFirestore.getInstance() }

    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var isWithinGeofence by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var locationUpdateTime by remember { mutableStateOf<Long>(0) }

    val auth = remember { AuthRepository() }
    val currentUser = auth.getCurrentUser()
    val userId = currentUser?.id
    var userName by remember {
        mutableStateOf("")
    }
    val getUserData = db.collection("users")
    var wasVisible by remember { mutableStateOf(false) }

    // Coordinates of the petrol pump
    val petrolPumpLatitude = 21.8704003
    val petrolPumpLongitude = 73.5024621
    val geofenceRadius = 100f  // 100 meters
    val coroutineScope = rememberCoroutineScope()

    // Check if attendance is already marked for today
    var isAttendanceAlreadyMarked by remember { mutableStateOf(false) }
    var attendanceTime by remember { mutableStateOf<String?>(null) }

    // Function to check if user is within geofence
    fun checkGeofence(location: Location) {
        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude, location.longitude,
            petrolPumpLatitude, petrolPumpLongitude,
            results
        )
        isWithinGeofence = results[0] <= geofenceRadius
        locationUpdateTime = System.currentTimeMillis()
    }

    // Set up location updates
    DisposableEffect(key1 = locationClient) {
        locationClient.locationUpdateListener = { location ->
            if (location != null) {
                currentLocation = location
                checkGeofence(location)
            }
        }

        locationClient.startLocationUpdates(5000) // 5 seconds interval

        onDispose {
            locationClient.stopLocationUpdates()
        }
    }

    // Function to mark attendance
    suspend fun markAttendance() {
        try {
            isLoading = true

            val now = Timestamp.now()

            val attendanceData = hashMapOf(
                "userId" to userId,
                "userName" to userName,
                "timestamp" to now,
                "latitude" to currentLocation?.latitude,
                "longitude" to currentLocation?.longitude,
                "status" to "present"
            )

            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            // Store attendance in a structured way for easy retrieval
            if (userId != null) {
                db.collection("attendance")
                    .document("$year")
                    .collection("$month")
                    .document("$day")
                    .collection("employees")
                    .document(userId)
                    .set(attendanceData)
                    .await()

                db.collection("users")
                    .document(userId)
                    .collection("attendance")
                    .document("$year-$month-$day")
                    .set(attendanceData)
                    .await()
            }

            isAttendanceAlreadyMarked = true

            // Format timestamp for display
            val date = now.toDate()
            val timeFormat = java.text.SimpleDateFormat("hh:mm a", Locale.getDefault())
            attendanceTime = timeFormat.format(date)

            errorMessage = null
        } catch (e: Exception) {
            errorMessage = "Failed to mark attendance: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    // Effect to check previous attendance
    LaunchedEffect(Unit) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        if (userId != null) {
            try {
                val attendanceDoc = db.collection("attendance")
                    .document("$year")
                    .collection("$month")
                    .document("$day")
                    .collection("employees")
                    .document(userId)
                    .get()
                    .await()

                val userDoc = getUserData.document(currentUser.id).get().await()
                println(userDoc)
                if (userDoc != null && userDoc.exists()) {
                    userName = userDoc.getString("name") ?: "N/A"
                } else {
                    userName = "N/A"
                }
                wasVisible = true

                isAttendanceAlreadyMarked = attendanceDoc.exists()

                if (isAttendanceAlreadyMarked) {
                    val timestamp = attendanceDoc.getTimestamp("timestamp")
                    if (timestamp != null) {
                        val timeFormat = java.text.SimpleDateFormat("hh:mm a", Locale.getDefault())
                        attendanceTime = timeFormat.format(timestamp.toDate())
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Error checking attendance: ${e.message}"
            }
        }
        wasVisible = isVisible
    }

    // Define fallback colors for pre-Android 12 devices
    val isAndroid12OrHigher = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // Compatibility colors
    val primaryColor = if (isAndroid12OrHigher) {
        MaterialTheme.colorScheme.primary
    } else {
        Color(0xFF6200EE) // Default primary for older Android
    }

    val primaryContainerColor = if (isAndroid12OrHigher) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color(0xFFE3F2FD) // Light blue for container backgrounds
    }

    val onPrimaryContainerColor = if (isAndroid12OrHigher) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        Color(0xFF001E2E) // Dark text on light container
    }

    val surfaceVariantColor = if (isAndroid12OrHigher) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        Color(0xFFE7E0EC) // Light gray for surface variant
    }

    val onSurfaceVariantColor = if (isAndroid12OrHigher) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        Color(0xFF49454F) // Dark gray for text on surface variant
    }

    val errorContainerColor = if (isAndroid12OrHigher) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        Color(0xFFFFDAD6) // Light red for error container
    }

    val onErrorContainerColor = if (isAndroid12OrHigher) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        Color(0xFF410002) // Dark red for text on error container
    }

    val errorColor = if (isAndroid12OrHigher) {
        MaterialTheme.colorScheme.error
    } else {
        Color(0xFFB3261E) // Standard error red
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top app bar replacement
            Text(
                text = "Petrol Pump Attendance",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Employee greeting
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Hello, ${userName ?: "Employee"}",
                        style = MaterialTheme.typography.titleLarge
                    )

                    val dateFormat = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                    Text(
                        text = dateFormat.format(Calendar.getInstance().time),
                        style = MaterialTheme.typography.bodyMedium,
                        color = onSurfaceVariantColor
                    )
                }
            }

            if (isAttendanceAlreadyMarked) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = primaryContainerColor
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Attendance Marked",
                            modifier = Modifier.size(72.dp),
                            tint = primaryColor
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Attendance Marked Successfully",
                            style = MaterialTheme.typography.titleLarge,
                            color = onPrimaryContainerColor
                        )
                        attendanceTime?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Time: $it",
                                style = MaterialTheme.typography.bodyLarge,
                                color = onPrimaryContainerColor
                            )
                        }
                    }
                }
            } else {
                // Location status card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isWithinGeofence)
                            primaryContainerColor
                        else
                            errorContainerColor
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (isWithinGeofence) Icons.Filled.LocationOn else Icons.Filled.LocationOff,
                            contentDescription = "Location Status",
                            modifier = Modifier.size(64.dp),
                            tint = if (isWithinGeofence)
                                primaryColor
                            else
                                errorColor
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isWithinGeofence)
                                "You are at the petrol pump"
                            else
                                "You're not at the petrol pump location",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            color = if (isWithinGeofence)
                                onPrimaryContainerColor
                            else
                                onErrorContainerColor
                        )

                        if (!isWithinGeofence) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Please go to the petrol pump to mark your attendance",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = onErrorContainerColor
                            )
                        }

                        // Display last update time
                        if (locationUpdateTime > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val timeFormat = java.text.SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
                            Text(
                                text = "Last updated: ${timeFormat.format(Date(locationUpdateTime))}",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = if (isWithinGeofence)
                                    onPrimaryContainerColor.copy(alpha = 0.7f)
                                else
                                    onErrorContainerColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Show distance from petrol pump
                currentLocation?.let { location ->
                    val results = FloatArray(1)
                    Location.distanceBetween(
                        location.latitude, location.longitude,
                        petrolPumpLatitude, petrolPumpLongitude,
                        results
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = surfaceVariantColor
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Distance from petrol pump",
                                style = MaterialTheme.typography.titleSmall,
                                color = onSurfaceVariantColor
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            val distance = results[0].toInt()
                            Text(
                                text = "$distance meters",
                                style = MaterialTheme.typography.headlineMedium,
                                color = when {
                                    distance <= 50 -> Color(0xFF388E3C) // Green
                                    distance <= 100 -> Color(0xFFFFA000) // Amber
                                    else -> Color(0xFFD32F2F) // Red
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 6.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Marking attendance...",
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    // Button with proper compatibility colors
                    val buttonColors = if (isWithinGeofence) {
                        ButtonDefaults.buttonColors(
                            containerColor = primaryColor,
                            contentColor = Color.White // Ensure text is visible
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = if (isAndroid12OrHigher) {
                                surfaceVariantColor
                            } else {
                                Color(0xFF607D8B) // Fallback color for older Android
                            },
                            contentColor = if (isAndroid12OrHigher) {
                                onSurfaceVariantColor
                            } else {
                                Color.White
                            }
                        )
                    }

                    // Pulsating indicator when within geofence
                    val infiniteTransition = rememberInfiniteTransition(label = "button-pulse")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = if (isWithinGeofence) 1.05f else 1f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(700),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                        ),
                        label = "pulse-animation"
                    )

                    Button(
                        onClick = {
                            if (isWithinGeofence) {
                                coroutineScope.launch {
                                    markAttendance()
                                }
                            } else {
                                errorMessage = "You must be at the petrol pump to mark attendance"
                            }
                        },
                        enabled = isWithinGeofence && !isLoading,
                        colors = buttonColors,
                        modifier = Modifier
                            .height(56.dp)
                            .fillMaxWidth(0.8f)
                            .scale(scale),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isWithinGeofence) Icons.Filled.CheckCircle else Icons.Filled.LocationOff,
                            contentDescription = "Mark Attendance"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isWithinGeofence) "Mark Attendance" else "Not at Location",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = error,
                            color = errorColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}