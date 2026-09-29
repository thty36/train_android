package com.example.trainlog.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trainlog.data.entity.DayPlan
import com.example.trainlog.data.repo.TrainRepository
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CalendarUiState(
    val year: Int,
    val month: Int,
    val today: LocalDate,
    val plansByDate: Map<String, DayPlan> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val repo: TrainRepository) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val shownMonth = MutableStateFlow(today.withDayOfMonth(1))

    val uiState: StateFlow<CalendarUiState> = shownMonth
        .flatMapLatest { month ->
            repo.observeMonthPlans(month.year, month.monthValue)
                .map { plans ->
                    CalendarUiState(
                        year = month.year,
                        month = month.monthValue,
                        today = today,
                        plansByDate = plans.associateBy { it.date },
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CalendarUiState(today.year, today.monthValue, today),
        )

    fun prevMonth() {
        shownMonth.value = shownMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        shownMonth.value = shownMonth.value.plusMonths(1)
    }

    fun goToday() {
        shownMonth.value = today.withDayOfMonth(1)
    }
}
