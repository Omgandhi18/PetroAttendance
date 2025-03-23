import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

enum class AttendanceStatus {
    PRESENT, ABSENT, ON_LEAVE, HOLIDAY, WEEKEND, FUTURE
}

data class DayAttendance(
    val date: Date,
    val status: AttendanceStatus
)



@Composable
fun YearlyAttendanceScreen(navController: NavController, userId: String) {
    val db = remember { FirebaseFirestore.getInstance() }
    var selectedYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var yearlyData by remember { mutableStateOf<Map<Int, Map<AttendanceStatus, Int>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load yearly attendance data
    LaunchedEffect(selectedYear) {
        isLoading = true
        try {
            val yearlyStats = mutableMapOf<Int, Map<AttendanceStatus, Int>>()

            // For each month
            for (month in 1..12) {
                val monthlySummary = mutableMapOf<AttendanceStatus, Int>()

                // Initialize counters
                AttendanceStatus.values().forEach { status ->
                    monthlySummary[status] = 0
                }

                // Query for the month
                val startDate = Calendar.getInstance()
                startDate.set(selectedYear, month - 1, 1, 0, 0, 0)

                val endDate = Calendar.getInstance()
                endDate.set(selectedYear, month, 1, 0, 0, 0)

                val querySnapshot = db.collection("users")
                    .document(userId)
                    .collection("attendance")
                    .whereGreaterThanOrEqualTo("timestamp", Timestamp(startDate.time))
                    .whereLessThan("timestamp", Timestamp(endDate.time))
                    .get()
                    .await()

                // Process results
                for (document in querySnapshot.documents) {
                    val status = document.getString("status") ?: "present"

                    val attendanceStatus = when(status) {
                        "present" -> AttendanceStatus.PRESENT
                        "on_leave" -> AttendanceStatus.ON_LEAVE
                        else -> AttendanceStatus.ABSENT
                    }

                    monthlySummary[attendanceStatus] = (monthlySummary[attendanceStatus] ?: 0) + 1
                }

                yearlyStats[month] = monthlySummary
            }

            yearlyData = yearlyStats
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
        // Year selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = {
                selectedYear -= 1
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Previous Year")
            }

            Text(
                text = selectedYear.toString(),
                style = MaterialTheme.typography.titleLarge
            )

            IconButton(onClick = {
                selectedYear += 1
            }) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next Year")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Monthly breakdown
            val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

            // Calculate yearly totals
            val yearlyTotals = mutableMapOf<AttendanceStatus, Int>()

            AttendanceStatus.values().forEach { status ->
                yearlyTotals[status] = 0
            }

            yearlyData.forEach { (_, monthData) ->
                monthData.forEach { (status, count) ->
                    yearlyTotals[status] = (yearlyTotals[status] ?: 0) + count
                }
            }

            // Yearly summary card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Yearly Summary",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${yearlyTotals[AttendanceStatus.PRESENT] ?: 0}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("Present Days")
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${yearlyTotals[AttendanceStatus.ON_LEAVE] ?: 0}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text("Leave Days")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Yearly attendance percentage
                    val totalWorkingDays = (yearlyTotals[AttendanceStatus.PRESENT] ?: 0) +
                            (yearlyTotals[AttendanceStatus.ABSENT] ?: 0) +
                            (yearlyTotals[AttendanceStatus.ON_LEAVE] ?: 0)

                    val presentDays = yearlyTotals[AttendanceStatus.PRESENT] ?: 0
                    val attendancePercentage = if (totalWorkingDays > 0) {
                        (presentDays.toFloat() / totalWorkingDays.toFloat() * 100).toInt()
                    } else {
                        0
                    }

                    Text(
                        text = "Overall Attendance: $attendancePercentage%",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = attendancePercentage / 100f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(MaterialTheme.shapes.small)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Monthly breakdown
            Text(
                text = "Monthly Breakdown",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(months.indices.toList()) { index ->
                    val monthNumber = index + 1
                    val monthData = yearlyData[monthNumber] ?: emptyMap()

                    val presentDays = monthData[AttendanceStatus.PRESENT] ?: 0
                    val leaveDays = monthData[AttendanceStatus.ON_LEAVE] ?: 0
                    val absentDays = monthData[AttendanceStatus.ABSENT] ?: 0

                    val totalDays = presentDays + leaveDays + absentDays
                    val attendanceRate = if (totalDays > 0) {
                        (presentDays.toFloat() / totalDays.toFloat())
                    } else {
                        0f
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Navigate to monthly details
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = months[index],
                                style = MaterialTheme.typography.titleMedium
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            LinearProgressIndicator(
                                progress = attendanceRate,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(MaterialTheme.shapes.small)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = if (totalDays > 0) "${(attendanceRate * 100).toInt()}%" else "N/A",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}