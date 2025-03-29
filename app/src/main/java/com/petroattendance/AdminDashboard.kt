package com.petroattendance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

@Composable
fun AdminMainScreen(navController: NavController) {
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
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = "Stats") },
                    label = { Text("Monthly") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                    label = { Text("Profile") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> AdminDashboard(navController,padding)
            1 -> AdminStatsScreen(navController,padding)
            2 -> AdminProfileScreen(navController,padding)
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboard(navController: NavController,padding: PaddingValues) {
    val db = remember { FirebaseFirestore.getInstance() }
    var employees by remember { mutableStateOf<List<Employee>>(emptyList()) }
    var attendanceRecords by remember { mutableStateOf<Map<String, List<AttendanceRecord>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Stats for dashboard
    var presentCount by remember { mutableStateOf(0) }
    var absentCount by remember { mutableStateOf(0) }
    var leaveCount by remember { mutableStateOf(0) }

    // Date format for display
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val coroutineScope = rememberCoroutineScope()

    // Functions for loading attendance, marking leave, etc. remain the same
    // Just adding calculation of stats

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

            // Calculate stats
            if (employees.isNotEmpty()) {
                presentCount = employees.count { employee ->
                    attendanceRecords[employee.id]?.any { it.status == "present" } == true
                }
                leaveCount = employees.count { employee ->
                    attendanceRecords[employee.id]?.any { it.status == "on_leave" } == true
                }
                absentCount = employees.size - presentCount - leaveCount
            }
        } catch (e: Exception) {
            // Handle error
        } finally {
            isLoading = false
        }
    }
    suspend fun refreshData() {
        isRefreshing = true
        try {
            // Reload employees
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

            // Reload attendance for current selected date
            loadAttendanceForDate(selectedDate)
        } catch (e: Exception) {
            // Handle error
        } finally {
            isRefreshing = false
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
            coroutineScope.launch {
                loadAttendanceForDate(selectedDate)
            }

        } catch (e: Exception) {
            // Handle error
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
    ) {
        // App bar with modern design
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Admin Dashboard",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage employee attendance",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Date selector with improved design
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Previous Day Button
                IconButton(
                    onClick = {
                        val newDate = Calendar.getInstance()
                        newDate.time = selectedDate.time
                        newDate.add(Calendar.DAY_OF_MONTH, -1)
                        selectedDate = newDate
                        coroutineScope.launch {
                            loadAttendanceForDate(newDate)
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Previous Day",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Date Display and Picker
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Selected Date",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateFormat.format(selectedDate.time),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { showDatePicker = true }
                    )
                }

                // Today Button
                FilledTonalButton(
                    onClick = {
                        val today = Calendar.getInstance()
                        selectedDate = today
                        coroutineScope.launch {
                            loadAttendanceForDate(today)
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Today",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Today")
                }

                // Next Day Button
                IconButton(
                    onClick = {
                        val newDate = Calendar.getInstance()
                        newDate.time = selectedDate.time
                        newDate.add(Calendar.DAY_OF_MONTH, 1)
                        selectedDate = newDate
                        coroutineScope.launch {
                            loadAttendanceForDate(newDate)
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                ) {
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = "Next Day",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        if (datePickerState.selectedDateMillis != null) {
                            val newDate = dateFormat.format(datePickerState.selectedDateMillis)
                            println(newDate)
                            selectedDate = Calendar.getInstance().apply { time =
                                dateFormat.parse(newDate)!!
                            }
                            coroutineScope.launch {
                                loadAttendanceForDate(selectedDate)
                            }
                            showDatePicker = false
                        }
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                DatePicker(
                    state = datePickerState,
                    modifier = Modifier
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Stats cards in a row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Present Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = presentCount.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Present",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Absent Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = absentCount.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Absent",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // On Leave Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = leaveCount.toString(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "On Leave",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Attendance list header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Employee Attendance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Search button could be added here
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Employee list with improved cards
            val refreshState = rememberPullToRefreshState()
            PullToRefreshBox(state = refreshState,isRefreshing = isRefreshing, onRefresh = { coroutineScope.launch { refreshData() } }) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)

                ) {
                    items(employees) { employee ->
                        val attendanceStatus = when {
                            attendanceRecords[employee.id]?.any { it.status == "present" } == true -> "Present"
                            attendanceRecords[employee.id]?.any { it.status == "on_leave" } == true -> "On Leave"
                            else -> "Absent"
                        }

                        val (statusColor, statusBgColor) = when (attendanceStatus) {
                            "Present" -> Pair(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            "On Leave" -> Pair(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
                            else -> Pair(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Avatar placeholder
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = employee.name.take(1).uppercase(),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = employee.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = employee.role.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                                        color = statusBgColor
                                    ) {
                                        Text(
                                            text = attendanceStatus,
                                            color = statusColor,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (attendanceStatus != "On Leave") {
                                        var showConfirmDialog by remember { mutableStateOf(false) }

                                        IconButton(
                                            onClick = { showConfirmDialog = true },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(
                                                    MaterialTheme.colorScheme.tertiaryContainer.copy(
                                                        alpha = 0.3f
                                                    )
                                                )
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Mark On Leave",
                                                tint = MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        if (showConfirmDialog) {
                                            AlertDialog(
                                                onDismissRequest = { showConfirmDialog = false },
                                                title = { Text("Mark On Leave") },
                                                text = {
                                                    Text(
                                                        "Mark ${employee.name} as on leave for ${
                                                            dateFormat.format(
                                                                selectedDate.time
                                                            )
                                                        }?"
                                                    )
                                                },
                                                confirmButton = {
                                                    Button(
                                                        onClick = {
                                                            showConfirmDialog = false
                                                            coroutineScope.launch {
                                                                // Function to mark employee on leave
                                                                // Implementation remains the same
                                                            }
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


