package com.petroattendance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

// Data class for daily attendance record
data class DailyAttendanceRecord(
    val date: String,
    val day: Int,
    val status: String,
    val timestamp: String?,
    val notes: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeDetailedAttendanceScreen(
    navController: NavController,
    padding: PaddingValues,
    employeeId: String,
    employeeName: String,
    month: Int,
    year: Int
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var attendanceRecords by remember { mutableStateOf<List<DailyAttendanceRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var presentDays by remember { mutableStateOf(0) }
    var absentDays by remember { mutableStateOf(0) }
    var leaveDays by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    // Calendar for date calculations
    val calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month - 1)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.time)

    // Function to load daily attendance records
    suspend fun loadDailyAttendanceRecords() {
        isLoading = true
        try {
            val records = mutableListOf<DailyAttendanceRecord>()

            // Reset counters
            presentDays = 0
            absentDays = 0
            leaveDays = 0

            // For each day in the month
            for (day in 1..daysInMonth) {
                calendar.set(Calendar.DAY_OF_MONTH, day)
                val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                val formattedDate = dateFormat.format(calendar.time)

                // Get attendance record for this day
                val recordSnapshot = db.collection("attendance")
                    .document("$year")
                    .collection("$month")
                    .document("$day")
                    .collection("employees")
                    .document(employeeId)
                    .get()
                    .await()

                if (recordSnapshot.exists()) {
                    val status = recordSnapshot.getString("status") ?: "absent"
                    val timestamp = recordSnapshot.getTimestamp("timestamp")
                    val notes = recordSnapshot.getString("notes")

                    val formattedTime = if (timestamp != null) {
                        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(timestamp.toDate())
                    } else null

                    records.add(
                        DailyAttendanceRecord(
                            date = formattedDate,
                            day = day,
                            status = status,
                            timestamp = formattedTime,
                            notes = notes
                        )
                    )

                    // Update counters
                    when (status) {
                        "present" -> presentDays++
                        "on_leave" -> leaveDays++
                        else -> absentDays++
                    }
                } else {
                    // No record for this day, so mark as absent
                    records.add(
                        DailyAttendanceRecord(
                            date = formattedDate,
                            day = day,
                            status = "absent",
                            timestamp = null,
                            notes = null
                        )
                    )
                    absentDays++
                }
            }

            // Sort records by day (should be already sorted but just to be sure)
            attendanceRecords = records.sortedBy { it.day }
        } catch (e: Exception) {
            // Handle error
            println("Error loading attendance records: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    // Load data when component is first displayed
    LaunchedEffect(employeeId, month, year) {
        coroutineScope.launch {
            loadDailyAttendanceRecords()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
    ) {
        // Top App Bar with back button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = { navController.popBackStack() }
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = employeeName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Attendance Details - $monthName $year",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Attendance Summary Cards
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
                        text = presentDays.toString(),
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
                        text = absentDays.toString(),
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
                        text = leaveDays.toString(),
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

        Spacer(modifier = Modifier.height(24.dp))

        // Daily Records Header
        Text(
            text = "Daily Attendance Records",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Daily Attendance Records List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(attendanceRecords) { record ->
                    DailyAttendanceCard(record)
                }
            }
        }
    }
}

@Composable
fun DailyAttendanceCard(record: DailyAttendanceRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date Circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = record.day.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Date and Status
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = record.date,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Time stamp (if available)
                if (record.timestamp != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Marked at: ${record.timestamp}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Notes (if available)
                if (!record.notes.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Note: ${record.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status Indicator
            StatusIndicator(status = record.status)
        }
    }
}

@Composable
fun StatusIndicator(status: String) {
    val (backgroundColor, contentColor, icon, label) = when (status) {
        "present" -> Quadruple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primary,
            Icons.Default.CheckCircle,
            "Present"
        )
        "on_leave" -> Quadruple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.tertiary,
            Icons.Default.CalendarMonth,
            "On Leave"
        )
        else -> Quadruple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.error,
            Icons.Default.Close,
            "Absent"
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

// Helper class for multiple return values
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)