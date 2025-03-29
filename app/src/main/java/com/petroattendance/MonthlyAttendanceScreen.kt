package com.petroattendance

import AttendanceStatus
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyAttendanceScreen(navController: NavController,userId: String) {
    val db = remember { FirebaseFirestore.getInstance() }
    var selectedMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var attendanceData by remember { mutableStateOf<Map<Int, AttendanceStatus>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var monthSummary by remember { mutableStateOf<Map<AttendanceStatus, Int>>(emptyMap()) }

    // Date formatters
    val monthFormatter = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val dayFormatter = remember { SimpleDateFormat("d", Locale.getDefault()) }

    // Calculate days in month
    fun getDaysInMonth(calendar: Calendar): Int {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val daysInMonth = Calendar.getInstance()
        daysInMonth.set(year, month, 1)
        return daysInMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    // Load attendance data for the selected month
    LaunchedEffect(selectedMonth) {
        isLoading = true
        try {
            val year = selectedMonth.get(Calendar.YEAR)
            val month = selectedMonth.get(Calendar.MONTH) + 1

            // Query attendance data for the month
            val attendanceMap = HashMap<Int, AttendanceStatus>()
            val summary = mutableMapOf<AttendanceStatus, Int>()

            // Set default counts
            AttendanceStatus.values().forEach { status ->
                summary[status] = 0
            }

            // Get data from Firestore
            val querySnapshot = db.collection("users")
                .document(userId)
                .collection("attendance")
                .whereGreaterThanOrEqualTo("timestamp", Timestamp(Date(year - 1900, month - 1, 1)))
                .whereLessThan("timestamp", Timestamp(Date(year - 1900, month, 1)))
                .get()
                .await()

            // Parse the data
            for (document in querySnapshot.documents) {
                val timestamp = document.getTimestamp("timestamp")
                val status = document.getString("status") ?: "present"

                if (timestamp != null) {
                    val calendar = Calendar.getInstance()
                    calendar.time = timestamp.toDate()
                    val day = calendar.get(Calendar.DAY_OF_MONTH)

                    attendanceMap[day] = when(status) {
                        "present" -> AttendanceStatus.PRESENT
                        "on_leave" -> AttendanceStatus.ON_LEAVE
                        else -> AttendanceStatus.ABSENT
                    }
                }
            }

            // Fill in remaining days
            val daysInMonth = getDaysInMonth(selectedMonth)
            val currentDate = Calendar.getInstance()

            for (day in 1..daysInMonth) {
                val dayDate = Calendar.getInstance()
                dayDate.set(year, selectedMonth.get(Calendar.MONTH), day)

                // If date is in future, mark as FUTURE
                if (dayDate.after(currentDate)) {
                    attendanceMap[day] = AttendanceStatus.FUTURE
                    summary[AttendanceStatus.FUTURE] = (summary[AttendanceStatus.FUTURE] ?: 0) + 1
                    continue
                }

                // If weekend, mark as WEEKEND
                if (dayDate.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                    dayDate.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                    attendanceMap[day] = AttendanceStatus.WEEKEND
                    summary[AttendanceStatus.WEEKEND] = (summary[AttendanceStatus.WEEKEND] ?: 0) + 1
                    continue
                }

                // If not already recorded, mark as ABSENT
                if (!attendanceMap.containsKey(day)) {
                    attendanceMap[day] = AttendanceStatus.ABSENT
                }

                // Update summary count
                val status = attendanceMap[day] ?: AttendanceStatus.ABSENT
                summary[status] = (summary[status] ?: 0) + 1
            }

            attendanceData = attendanceMap
            monthSummary = summary
        } catch (e: Exception) {
            // Handle error
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Month selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = {
                val newMonth = Calendar.getInstance()
                newMonth.time = selectedMonth.time
                newMonth.add(Calendar.MONTH, -1)
                selectedMonth = newMonth
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Previous Month")
            }

            Text(
                text = monthFormatter.format(selectedMonth.time),
                style = MaterialTheme.typography.titleLarge
            )

            IconButton(onClick = {
                val newMonth = Calendar.getInstance()
                newMonth.time = selectedMonth.time
                newMonth.add(Calendar.MONTH, 1)
                selectedMonth = newMonth
            }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next Month")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Calendar header (days of week)
        val daysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            daysOfWeek.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Calendar grid
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Get the first day of month
            val firstDayCalendar = Calendar.getInstance()
            firstDayCalendar.set(selectedMonth.get(Calendar.YEAR), selectedMonth.get(Calendar.MONTH), 1)
            val firstDayOfWeek = firstDayCalendar.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday

            // Get total days in month
            val daysInMonth = getDaysInMonth(selectedMonth)

            // Calculate total cells needed (previous month days + current month + potentially next month)
            val totalCells = ((firstDayOfWeek + daysInMonth + 6) / 7) * 7

            // Generate all day cells
            val dayCells = List(totalCells) { index ->
                when {
                    index < firstDayOfWeek -> null // Empty cell before month starts
                    index >= firstDayOfWeek + daysInMonth -> null // Empty cell after month ends
                    else -> {
                        val dayOfMonth = index - firstDayOfWeek + 1
                        val status = attendanceData[dayOfMonth] ?: AttendanceStatus.ABSENT
                        dayOfMonth to status
                    }
                }
            }

            // Display the grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxWidth().height(300.dp)
            ) {
                items(dayCells) { cell ->
                    if (cell == null) {
                        // Empty cell
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(2.dp)
                        )
                    } else {
                        val (day, status) = cell
                        val backgroundColor = when (status) {
                            AttendanceStatus.PRESENT -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            AttendanceStatus.ABSENT -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                            AttendanceStatus.ON_LEAVE -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                            AttendanceStatus.WEEKEND -> MaterialTheme.colorScheme.surfaceVariant
                            AttendanceStatus.HOLIDAY -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                            AttendanceStatus.FUTURE -> Color.Transparent
                        }

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(backgroundColor)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = MaterialTheme.shapes.small
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = day.toString())
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Summary
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Monthly Summary",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${monthSummary[AttendanceStatus.PRESENT] ?: 0}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("Present")
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${monthSummary[AttendanceStatus.ABSENT] ?: 0}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text("Absent")
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${monthSummary[AttendanceStatus.ON_LEAVE] ?: 0}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text("Leave")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Attendance percentage calculation
                    val workingDays = (monthSummary[AttendanceStatus.PRESENT] ?: 0) +
                            (monthSummary[AttendanceStatus.ABSENT] ?: 0) +
                            (monthSummary[AttendanceStatus.ON_LEAVE] ?: 0)

                    val presentDays = monthSummary[AttendanceStatus.PRESENT] ?: 0
                    val attendancePercentage = if (workingDays > 0) {
                        (presentDays.toFloat() / workingDays.toFloat() * 100).toInt()
                    } else {
                        0
                    }

                    LinearProgressIndicator(
                        progress = { attendancePercentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(MaterialTheme.shapes.small),
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Attendance: $attendancePercentage%",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}