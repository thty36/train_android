package com.example.trainlog.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.trainlog.ui.calendar.CalendarScreen
import com.example.trainlog.ui.plan.PlanDetailScreen
import com.example.trainlog.ui.templates.TemplateEditScreen
import com.example.trainlog.ui.templates.TemplateListScreen

object Routes {
    const val CALENDAR = "calendar"
    const val TEMPLATES = "templates"

    fun plan(date: String) = "plan/$date"
    fun templateEdit(templateId: Long) = "templateEdit/$templateId"
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.CALENDAR) {
        composable(Routes.CALENDAR) {
            CalendarScreen(navController)
        }
        composable("plan/{date}") { entry ->
            PlanDetailScreen(
                navController = navController,
                dateArg = entry.arguments?.getString("date").orEmpty(),
            )
        }
        composable(Routes.TEMPLATES) {
            TemplateListScreen(navController)
        }
        composable("templateEdit/{templateId}") { entry ->
            TemplateEditScreen(
                navController = navController,
                templateId = entry.arguments?.getString("templateId")?.toLongOrNull() ?: 0L,
            )
        }
    }
}

fun NavHostController.navigateReplacing(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}
