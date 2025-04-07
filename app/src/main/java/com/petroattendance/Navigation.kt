package com.petroattendance


import YearlyAttendanceScreen
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")

    // Employee screens
    object AttendanceScreen : Screen("attendance_screen")
    object MarkAttendance : Screen("mark_attendance")
    object MonthlyAttendance : Screen("monthly_attendance")
    object YearlyAttendance : Screen("yearly_attendance")

    // Admin screens
    object AdminHome : Screen("admin_main_screen")
    object EmployeeManagement : Screen("employee_management")
    object Reports : Screen("reports")

    // New screens
    // New screens - fixed route name to match what's being used in navigation
    object EmployeeDetailedAttendance : Screen("employee_attendance_detail/{employeeId}/{employeeName}/{month}/{year}")

    // Helper function to create route with parameters - fixed to match what's being used
    fun createEmployeeDetailedAttendanceRoute(employeeId: String, employeeName: String, month: Int, year: Int): String {
        return "employee_attendance_detail/$employeeId/$employeeName/$month/$year"
    }
}

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(route = Screen.Splash.route) {
            SplashScreen(navController = navController)
        }
        composable(route = Screen.Login.route) {
            LoginScreen(navController = navController)
        }

        // Employee screens
        composable(route = Screen.MarkAttendance.route) {
            EmployeeMainScreen(navController = navController)
        }

        composable(route = Screen.MonthlyAttendance.route) {
            MonthlyAttendanceScreen(navController = navController, userId = "0")
        }

        composable(route = Screen.YearlyAttendance.route) {
            YearlyAttendanceScreen(navController = navController, userId = "0")
        }

        // Admin screens
        composable(route = Screen.AdminHome.route) {
            AdminMainScreen(navController = navController)
        }
        composable("admin_employees") {
            AdminEmployeeManagementScreen(navController, PaddingValues())
        }

//        composable(route = Screen.EmployeeManagement.route) {
//            AdminUserManagementScreen(navController = navController)
//        }
//
//        composable(route = Screen.Reports.route) {
//            ReportGenerationScreen(navController = navController)
//        }
        // New route for employee detailed attendance
        composable(
            route = "employee_attendance_detail/{employeeId}/{employeeName}/{month}/{year}",
            arguments = listOf(
                navArgument("employeeId") { type = NavType.StringType },
                navArgument("employeeName") { type = NavType.StringType },
                navArgument("month") { type = NavType.IntType },
                navArgument("year") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val employeeId = backStackEntry.arguments?.getString("employeeId") ?: ""
            val employeeName = backStackEntry.arguments?.getString("employeeName") ?: ""
            val month = backStackEntry.arguments?.getInt("month") ?: 1
            val year = backStackEntry.arguments?.getInt("year") ?: 2025

            EmployeeDetailedAttendanceScreen(
                navController = navController,
                padding = PaddingValues(),  // You may need to adjust this based on your layout
                employeeId = employeeId,
                employeeName = employeeName,
                month = month,
                year = year
            )
        }
    }
}