package com.rohan.spend.ui

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.LocalDate

/** [AppState.today] for composables that don't get the state, so "Today" labels turn over with Stats. */
val LocalToday = staticCompositionLocalOf { LocalDate.now() }
