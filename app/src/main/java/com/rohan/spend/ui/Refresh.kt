package com.rohan.spend.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * Pull down to check for new messages. The spinner only shows for a pull, so the auto check on
 * open and Home's refresh button don't get a second spinner at the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Refreshable(refreshing: Boolean, onRefresh: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    var pulled by remember { mutableStateOf(false) }
    // The flag can lag the pull by a frame, so give it a moment before letting go.
    LaunchedEffect(pulled, refreshing) { if (pulled && !refreshing) { delay(400); pulled = false } }
    val busy = pulled
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        busy, { pulled = true; onRefresh() }, Modifier.fillMaxSize(), state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state, isRefreshing = busy, containerColor = C.card, color = C.ink,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
            )
        },
        content = content,
    )
}
