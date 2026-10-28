package com.rohan.spend.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohan.spend.data.Cat

/** Where the user is. Tabs sit at the bottom of the stack; detail screens push on top. */
sealed interface Route {
    data class Tabbed(val tab: Tab) : Route
    data class TxnDetail(val id: Long) : Route
    data class Category(val cat: Cat) : Route
    data object Search : Route
}

class Nav(start: Route) {
    val stack = mutableStateListOf(start)
    val top: Route get() = stack.last()
    var forward = true
        private set

    fun push(r: Route) { forward = true; stack.add(r) }
    fun pop() { if (stack.size > 1) { forward = false; stack.removeAt(stack.lastIndex) } }
    fun tab(t: Tab) { forward = true; stack.clear(); stack.add(Route.Tabbed(t)) }
}

@Composable
fun SpendApp(vm: AppViewModel, onAskPermission: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val base = Typography()
    val type = Typography(
        headlineSmall = base.headlineSmall.copy(fontFamily = Plex, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = Plex),
        bodyMedium = base.bodyMedium.copy(fontFamily = Plex),
        bodySmall = base.bodySmall.copy(fontFamily = Plex),
        labelLarge = base.labelLarge.copy(fontFamily = Plex),
    )
    MaterialTheme(typography = type) { ProvideTextStyle(T.body) { Root(vm, s, onAskPermission) } }
}

@Composable
private fun Root(vm: AppViewModel, s: AppState, onAskPermission: () -> Unit) {
    FrostBackground(animate = s.stage != Stage.READY, blobs = s.stage != Stage.READY) {
        AnimatedContent(
            targetState = s.stage,
            transitionSpec = { (fadeIn(tween(500)) + scaleIn(initialScale = .96f)) togetherWith fadeOut(tween(300)) },
            label = "stage",
        ) { stage ->
            when (stage) {
                Stage.NEEDS_PERMISSION -> OnboardingScreen(onAskPermission)
                Stage.SCANNING -> ScanScreen(s)
                Stage.READY -> Main(vm, s)
            }
        }
    }
}

@Composable
private fun Main(vm: AppViewModel, s: AppState) {
    val nav = remember { Nav(Route.Tabbed(Tab.HOME)) }
    val currentTab = (nav.stack.first() as Route.Tabbed).tab
    BackHandler(enabled = nav.stack.size > 1 || currentTab != Tab.HOME) {
        if (nav.stack.size > 1) nav.pop() else nav.tab(Tab.HOME)
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = nav.top,
            transitionSpec = {
                val tabSwitch = initialState is Route.Tabbed && targetState is Route.Tabbed
                when {
                    tabSwitch -> fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                    nav.forward -> (slideInHorizontally(tween(360)) { it / 3 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(360)) { -it / 6 } + fadeOut(tween(200)))
                    else -> (slideInHorizontally(tween(360)) { -it / 6 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(360)) { it / 3 } + fadeOut(tween(200)))
                }
            },
            label = "route",
        ) { r ->
            when (r) {
                is Route.Tabbed -> when (r.tab) {
                    Tab.HOME -> HomeScreen(s, vm, nav)
                    Tab.ACTIVITY -> ActivityScreen(s, nav)
                    Tab.INSIGHTS -> InsightsScreen(s, nav)
                    Tab.ACCOUNTS -> AccountsScreen(s, vm)
                }
                is Route.TxnDetail -> s.txns.firstOrNull { it.id == r.id }
                    ?.let { TxnScreen(it, s, vm, nav) } ?: LaunchedEffect(r) { nav.pop() }
                is Route.Category -> CategoryScreen(r.cat, s, nav)
                Route.Search -> SearchScreen(s, nav)
            }
        }
        if (nav.top is Route.Tabbed) {
            BottomBar(currentTab, nav::tab, Modifier.align(Alignment.BottomCenter))
        }
    }
}
