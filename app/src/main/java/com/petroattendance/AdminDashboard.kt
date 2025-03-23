package com.petroattendance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

data class Employee(
    val id: String,
    val name: String,
    val email: String,
    val phone: String,
    val role: String
)

data class AttendanceRecord(
    val id: String,
    val userId: String,
    val userName: String,
    val timestamp: Timestamp,
    val status: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboard(navController: NavController) {
    val db = remember { FirebaseFirestore.getInstance() }
    var employees by remember { mutableStateOf<List<Employee>>(emptyList()) }
    var attendanceRecords by remember { mutableStateOf<Map<String, List<AttendanceRecord>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }

    // Date format for display
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val couroutineScope = rememberCoroutineScope()
    // Load employees
    suspend fun loadAttendanceForDate(calendar: Calendar) {
        isLoading = true
        try {
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val snapshot = db.collection("attendance")
                .document("$year")
                .collection("$month")
                .document("$day")
                .collection("employees")
                .get()
                .await()

            val records = snapshot.documents.mapNotNull { doc ->
                val id = doc.id
                val userId = doc.getString("userId") ?: return@mapNotNull null
                val userName = doc.getString("userName") ?: return@mapNotNull null
                val timestamp = doc.getTimestamp("timestamp") ?: return@mapNotNull null
                val status = doc.getString("status") ?: "present"

                AttendanceRecord(id, userId, userName, timestamp, status)
            }

            // Group by userId
            attendanceRecords = records.groupBy { it.userId }
        } catch (e: Exception) {
            // Handle error
        } finally {
            isLoading = false
        }
    }

    // Function to mark employee on leave
    suspend fun markEmployeeOnLeave(employeeId: String, employeeName: String) {
        try {
            val calendar = selectedDate
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val leaveData = hashMapOf(
                "userId" to employeeId,
                "userName" to employeeName,
                "timestamp" to Timestamp.now(),
                "status" to "on_leave"
            )

            // Update attendance record
            db.collection("attendance")
                .document("$year")
                .collection("$month")
                .document("$day")
                .collection("employees")
                .document(employeeId)
                .set(leaveData)
                .await()

            // Also update in user's own collection
            db.collection("users")
                .document(employeeId)
                .collection("attendance")
                .document("$year-$month-$day")
                .set(leaveData)
                .await()

            // Refresh data
            loadAttendanceForDate(selectedDate)
        } catch (e: Exception) {
            // Handle error
        }
    }
    LaunchedEffect(Unit) {
        try {
            val snapshot = db.collection("users")
                .whereNotEqualTo("role", "admin")
                .get()
                .await()

            employees = snapshot.documents.mapNotNull { doc ->
                val id = doc.id
                val name = doc.getString("name") ?: return@mapNotNull null
                val email = doc.getString("email") ?: return@mapNotNull null
                val phone = doc.getString("phone") ?: ""
                val role = doc.getString("role") ?: "employee"

                Employee(id, name, email, phone, role)
            }

            // Load today's attendance
            couroutineScope.launch {
                loadAttendanceForDate(selectedDate)
            }

        } catch (e: Exception) {
            // Handle error
        } finally {
            isLoading = false
        }
    }
    // Function to load attendance for a specific date


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Dashboard") },
                actions = {
                    IconButton(onClick = {
                        // Export report functionality
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export Report")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Date selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = {
                    val newDate = Calendar.getInstance()
                    newDate.time = selectedDate.time
                    newDate.add(Calendar.DAY_OF_MONTH, -1)
                    selectedDate = newDate
                    // Refresh attendance data
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Previous Day")
                }

                Text(
                    text = dateFormat.format(selectedDate.time),
                    style = MaterialTheme.typography.titleLarge
                )

                IconButton(onClick = {
                    val newDate = Calendar.getInstance()
                    newDate.time = selectedDate.time
                    newDate.add(Calendar.DAY_OF_MONTH, 1)
                    selectedDate = newDate
                    // Refresh attendance data
                }) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Next Day")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Attendance list
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(employees) { employee ->
                        val attendanceStatus = when {
                            attendanceRecords[employee.id]?.any { it.status == "present" } == true -> "Present"
                            attendanceRecords[employee.id]?.any { it.status == "on_leave" } == true -> "On Leave"
                            else -> "Absent"
                        }

                        val attendanceColor = when(attendanceStatus) {
                            "Present" -> MaterialTheme.colorScheme.primary
                            "On Leave" -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = employee.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = employee.role,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = attendanceStatus,
                                        color = attendanceColor,
                                        style = MaterialTheme.typography.bodyLarge
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (attendanceStatus != "On Leave") {
                                        var showConfirmDialog by remember { mutableStateOf(false) }

                                        IconButton(onClick = { showConfirmDialog = true }) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Mark On Leave",
                                                tint = MaterialTheme.colorScheme.tertiary
                                            )
                                        }

                                        if (showConfirmDialog) {
                                            AlertDialog(
                                                onDismissRequest = { showConfirmDialog = false },
                                                title = { Text("Mark On Leave") },
                                                text = { Text("Mark ${employee.name} as on leave for ${dateFormat.format(selectedDate.time)}?") },
                                                confirmButton = {
                                                    Button(
                                                        onClick = {
                                                            showConfirmDialog = false
                                                            // Mark on leave
                                                        }
                                                    ) {
                                                        Text("Confirm")
                                                    }
                                                },
                                                dismissButton = {
                                                    TextButton(
                                                        onClick = { showConfirmDialog = false }
                                                    ) {
                                                        Text("Cancel")
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}