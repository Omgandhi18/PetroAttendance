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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
// Data class for employee stats
data class EmployeeMonthlyStats(
    val userId: String,
    val name: String,
    val presentDays: Int,
    val absentDays: Int,
    val leaveDays: Int,
    val totalWorkingDays: Int
)
@Composable
fun AdminStatsScreen(navController: NavController, padding: PaddingValues) {
    val db = remember { FirebaseFirestore.getInstance() }
    var employees by remember { mutableStateOf<List<Employee>>(emptyList()) }
    var employeeStats by remember { mutableStateOf<Map<String, EmployeeMonthlyStats>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH) + 1) }
    var selectedYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var showMonthPicker by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Month names for display
    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")



    // Function to load employee monthly stats
    suspend fun loadEmployeeMonthlyStats(year: Int, month: Int) {
        isLoading = true
        try {
            // First, ensure we have employees loaded
            if (employees.isEmpty()) {
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
            }

            // Calculate number of working days in the month
            val calendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

            // Simplified: count all days as working days (you might want to exclude weekends)
            val totalWorkingDays = daysInMonth

            // Load attendance records for the month
            val statsMap = mutableMapOf<String, EmployeeMonthlyStats>()

            // For each employee, initialize stats
            employees.forEach { employee ->
                statsMap[employee.id] = EmployeeMonthlyStats(
                    userId = employee.id,
                    name = employee.name,
                    presentDays = 0,
                    absentDays = 0,
                    leaveDays = 0,
                    totalWorkingDays = totalWorkingDays
                )
            }

            // For each day in the month, fetch attendance records
            for (day in 1..daysInMonth) {
                val dayRecords = db.collection("attendance")
                    .document("$year")
                    .collection("$month")
                    .document("$day")
                    .collection("employees")
                    .get()
                    .await()

                // Process each record
                val recordsForDay = dayRecords.documents.mapNotNull { doc ->
                    val userId = doc.getString("userId") ?: return@mapNotNull null
                    val status = doc.getString("status") ?: "present"

                    Pair(userId, status)
                }

                // Update stats for employees with records
                recordsForDay.forEach { (userId, status) ->
                    val currentStats = statsMap[userId] ?: return@forEach

                    when (status) {
                        "present" -> statsMap[userId] = currentStats.copy(presentDays = currentStats.presentDays + 1)
                        "on_leave" -> statsMap[userId] = currentStats.copy(leaveDays = currentStats.leaveDays + 1)
                        else -> statsMap[userId] = currentStats.copy(absentDays = currentStats.absentDays + 1)
                    }
                }
            }

            // For employees without records, mark them absent
            employees.forEach { employee ->
                val stats = statsMap[employee.id] ?: return@forEach
                val accountedDays = stats.presentDays + stats.leaveDays + stats.absentDays

                if (accountedDays < totalWorkingDays) {
                    statsMap[employee.id] = stats.copy(
                        absentDays = stats.absentDays + (totalWorkingDays - accountedDays)
                    )
                }
            }

            employeeStats = statsMap
        } catch (e: Exception) {
            // Handle error
            println("Error loading stats: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    // Load data when component is first displayed
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            loadEmployeeMonthlyStats(selectedYear, selectedMonth)
        }
    }

    // Load data when month/year changes
    LaunchedEffect(selectedMonth, selectedYear) {
        coroutineScope.launch {
            loadEmployeeMonthlyStats(selectedYear, selectedMonth)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Monthly Statistics",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Employee attendance overview",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }


        }

        Spacer(modifier = Modifier.height(24.dp))

        // Month/Year Selector
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
                // Previous Month Button
                IconButton(
                    onClick = {
                        if (selectedMonth > 1) {
                            selectedMonth--
                        } else {
                            selectedMonth = 12
                            selectedYear--
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Previous Month",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Month/Year Display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { showMonthPicker = true }
                ) {
                    Text(
                        text = "Selected Period",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${monthNames[selectedMonth - 1]} $selectedYear",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Next Month Button
                IconButton(
                    onClick = {
                        if (selectedMonth < 12) {
                            selectedMonth++
                        } else {
                            selectedMonth = 1
                            selectedYear++
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                ) {
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = "Next Month",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Month picker dialog
        if (showMonthPicker) {
            AlertDialog(
                onDismissRequest = { showMonthPicker = false },
                title = { Text("Select Month") },
                text = {
                    Column {
                        // Year selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { selectedYear-- }) {
                                Icon(Icons.Default.ArrowBack, "Previous Year")
                            }
                            Text(
                                text = "$selectedYear",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { selectedYear++ }) {
                                Icon(Icons.Default.ArrowForward, "Next Year")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Month grid
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (i in 0 until 4) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (j in 0 until 3) {
                                        val monthIndex = i * 3 + j
                                        val isSelected = monthIndex + 1 == selectedMonth

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceVariant
                                                )
                                                .clickable {
                                                    selectedMonth = monthIndex + 1
                                                }
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = monthNames[monthIndex],
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { showMonthPicker = false }) {
                        Text("Confirm")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
//
//        // Stats Overview
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            horizontalArrangement = Arrangement.spacedBy(8.dp)
//        ) {
//            // Calculate totals
//            val totalPresent = employeeStats.values.sumOf { it.presentDays }
//            val totalAbsent = employeeStats.values.sumOf { it.absentDays }
//            val totalLeave = employeeStats.values.sumOf { it.leaveDays }
//
//            // Present Card
//            Card(
//                modifier = Modifier.weight(1f),
//                colors = CardDefaults.cardColors(
//                    containerColor = MaterialTheme.colorScheme.primaryContainer
//                )
//            ) {
//                Column(
//                    modifier = Modifier.padding(16.dp),
//                    horizontalAlignment = Alignment.CenterHorizontally
//                ) {
//                    Text(
//                        text = totalPresent.toString(),
//                        style = MaterialTheme.typography.headlineLarge,
//                        fontWeight = FontWeight.Bold,
//                        color = MaterialTheme.colorScheme.onPrimaryContainer
//                    )
//                    Text(
//                        text = "Total Present",
//                        style = MaterialTheme.typography.bodyMedium,
//                        color = MaterialTheme.colorScheme.onPrimaryContainer
//                    )
//                }
//            }
//
//            // Absent Card
//            Card(
//                modifier = Modifier.weight(1f),
//                colors = CardDefaults.cardColors(
//                    containerColor = MaterialTheme.colorScheme.errorContainer
//                )
//            ) {
//                Column(
//                    modifier = Modifier.padding(16.dp),
//                    horizontalAlignment = Alignment.CenterHorizontally
//                ) {
//                    Text(
//                        text = totalAbsent.toString(),
//                        style = MaterialTheme.typography.headlineLarge,
//                        fontWeight = FontWeight.Bold,
//                        color = MaterialTheme.colorScheme.onErrorContainer
//                    )
//                    Text(
//                        text = "Total Absent",
//                        style = MaterialTheme.typography.bodyMedium,
//                        color = MaterialTheme.colorScheme.onErrorContainer
//                    )
//                }
//            }
//
//            // On Leave Card
//            Card(
//                modifier = Modifier.weight(1f),
//                colors = CardDefaults.cardColors(
//                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
//                )
//            ) {
//                Column(
//                    modifier = Modifier.padding(16.dp),
//                    horizontalAlignment = Alignment.CenterHorizontally
//                ) {
//                    Text(
//                        text = totalLeave.toString(),
//                        style = MaterialTheme.typography.headlineLarge,
//                        fontWeight = FontWeight.Bold,
//                        color = MaterialTheme.colorScheme.onTertiaryContainer
//                    )
//                    Text(
//                        text = "Total Leave",
//                        style = MaterialTheme.typography.bodyMedium,
//                        color = MaterialTheme.colorScheme.onTertiaryContainer
//                    )
//                }
//            }
//        }

        Spacer(modifier = Modifier.height(16.dp))

        // Employee stats list header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Employee Monthly Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
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
            // Employee stats list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(employeeStats.values.toList()) { stats ->
                    // Calculate attendance percentage
                    val attendancePercentage = if (stats.totalWorkingDays > 0) {
                        (stats.presentDays.toFloat() / stats.totalWorkingDays) * 100
                    } else {
                        0f
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            // Navigate to detailed employee attendance screen
                            navController.navigate(
                                "employee_attendance_detail/${stats.userId}/${stats.name}/${selectedMonth}/${selectedYear}"
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Employee info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                            text = stats.name.take(1).uppercase(),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = stats.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${String.format("%.1f", attendancePercentage)}% Attendance",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = when {
                                                attendancePercentage >= 90 -> MaterialTheme.colorScheme.primary
                                                attendancePercentage >= 75 -> MaterialTheme.colorScheme.tertiary
                                                else -> MaterialTheme.colorScheme.error
                                            }
                                        )
                                    }
                                }

                                // Attendance indicator
                                Surface(
                                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                                    color = when {
                                        attendancePercentage >= 90 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        attendancePercentage >= 75 -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            attendancePercentage >= 90 -> "Excellent"
                                            attendancePercentage >= 75 -> "Good"
                                            else -> "Poor"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = when {
                                            attendancePercentage >= 90 -> MaterialTheme.colorScheme.primary
                                            attendancePercentage >= 75 -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.error
                                        },
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Attendance details
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stats.presentDays.toString(),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Present",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stats.absentDays.toString(),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Absent",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stats.leaveDays.toString(),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        text = "On Leave",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stats.totalWorkingDays.toString(),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Work Days",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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