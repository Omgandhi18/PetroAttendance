package com.petroattendance


import YearlyAttendanceScreen
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

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

//        composable(route = Screen.EmployeeManagement.route) {
//            AdminUserManagementScreen(navController = navController)
//        }
//
//        composable(route = Screen.Reports.route) {
//            ReportGenerationScreen(navController = navController)
//        }
    }
}