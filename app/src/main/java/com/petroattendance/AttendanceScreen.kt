package com.petroattendance

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationServices
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.*
import kotlinx.coroutines.tasks.await

@Composable
fun AttendanceScreen(navController: NavController) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val db = remember { FirebaseFirestore.getInstance() }

    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var isWithinGeofence by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var attendanceMarked by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val auth = remember {
        AuthRepository()
    }
    val currentUser = auth.getCurrentUser()
    val userId = currentUser?.id
    val userName = currentUser?.name
    // Coordinates of the petrol pump
    val petrolPumpLatitude = 21.8704003  // Replace with actual coordinates
    val petrolPumpLongitude = 73.5024621   // Replace with actual coordinates
    val geofenceRadius = 100f  // 100 meters
    val coroutineScope = rememberCoroutineScope()

    // Check if attendance is already marked for today
    var isAttendanceAlreadyMarked by remember { mutableStateOf(false) }
    // Function to check if user is within geofence
    fun checkGeofence(location: Location) {
        val results = FloatArray(1)
        println(location.latitude)
        println(location.longitude)
        Location.distanceBetween(
            location.latitude, location.longitude,
            petrolPumpLatitude, petrolPumpLongitude,
            results
        )
        isWithinGeofence = results[0] <= geofenceRadius
        println(results[0])
        println(isWithinGeofence)
    }

    // Function to mark attendance
    suspend fun markAttendance() {
        try {
            isLoading = true

            val attendanceData = hashMapOf(
                "userId" to userId,
                "userName" to userName,
                "timestamp" to Timestamp.now(),
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
            errorMessage = null
        } catch (e: Exception) {
            errorMessage = "Failed to mark attendance: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    // Effect to get location
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

                isAttendanceAlreadyMarked = attendanceDoc.exists()
                print(isAttendanceAlreadyMarked)
            } catch (e: Exception) {
                errorMessage = "Error checking attendance: ${e.message}"
            }
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        currentLocation = location
                        checkGeofence(location)
                    }
                }
            } catch (e: Exception) {
                errorMessage = "Location error: ${e.message}"
            }
        } else {
            errorMessage = "Location permission required"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isAttendanceAlreadyMarked) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.CheckCircle,
                contentDescription = "Attendance Marked",
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Attendance already marked for today",
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Marking attendance...")
            } else {
                Text(
                    text = if (isWithinGeofence) "You are at the petrol pump"
                    else "You're not at the petrol pump location",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isWithinGeofence) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (isWithinGeofence) {
                            // Launch coroutine to mark attendance
                           coroutineScope.launch {
                               markAttendance()
                           }
                        } else {
                            errorMessage = "You must be at the petrol pump to mark attendance"
                        }
                    },
                    enabled = isWithinGeofence && !isLoading,
                    modifier = Modifier
                        .height(56.dp)
                        .width(200.dp)
                ) {
                    Text("Mark Attendance")
                }

                errorMessage?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}