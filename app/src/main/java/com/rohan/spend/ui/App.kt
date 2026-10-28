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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rohan.spend.data.Cat

/** Where the user is. Tabs sit at the bottom of the stack; detail screens push on top. */
sealed interface Route {
    data class Tabbed(val tab: Tab) : Route
    data class TxnDetail(val id: Long) : Route
    /** [month] is the month the user was looking at when they opened it, or null for "now". */
    data class Category(val cat: Cat, val month: String? = null) : Route
    data class Day(val epochDay: Long) : Route
    data class Cats(val month: String) : Route
    data object Search : Route
    data class Recap(val month: String) : Route
}

/** One screen on the stack. [key] is unique so two visits to the same route keep separate state. */
data class Entry(val key: Int, val route: Route)

class Nav(start: List<Entry>, private var next: Int) {
    val stack = mutableStateListOf<Entry>().apply { addAll(start) }
    val top: Entry get() = stack.last()
    var forward = true
        private set
    /** Called with the key of every screen that leaves for good, so its saved state can be dropped. */
    var onDrop: (Int) -> Unit = {}

    fun push(r: Route) { forward = true; stack.add(Entry(next++, r)) }
    fun pop() { if (stack.size > 1) { forward = false; onDrop(stack.removeAt(stack.lastIndex).key) } }
    fun tab(t: Tab) {
        forward = true
        // Each tab keeps a fixed key, so its month, filters and scroll survive switching away and back.
        stack.drop(1).forEach { onDrop(it.key) }
        stack.clear()
        stack.add(Entry(-1 - t.ordinal, Route.Tabbed(t)))
    }

    companion object {
        fun start() = Nav(listOf(Entry(-1 - Tab.HOME.ordinal, Route.Tabbed(Tab.HOME))), 0)

        // Rotation or the system killing the app in the background should land you where you were.
        val Saver: Saver<Nav, Any> = listSaver(
            save = { n -> listOf(n.next.toString()) + n.stack.map { "${it.key}|${encode(it.route)}" } },
            restore = { l ->
                val entries = l.drop(1).mapNotNull { line ->
                    val (k, r) = line.split("|", limit = 2)
                    decode(r)?.let { Entry(k.toInt(), it) }
                }
                if (entries.firstOrNull()?.route is Route.Tabbed) Nav(entries, l[0].toInt()) else start()
            },
        )

        private fun encode(r: Route): String = when (r) {
            is Route.Tabbed -> "tab:${r.tab.name}"
            is Route.TxnDetail -> "txn:${r.id}"
            is Route.Category -> "cat:${r.cat.name}:${r.month ?: ""}"
            is Route.Day -> "day:${r.epochDay}"
            is Route.Cats -> "cats:${r.month}"
            Route.Search -> "search"
            is Route.Recap -> "recap:${r.month}"
        }

        private fun decode(s: String): Route? = runCatching {
            val p = s.split(":")
            when (p[0]) {
                "tab" -> Route.Tabbed(Tab.valueOf(p[1]))
                "txn" -> Route.TxnDetail(p[1].toLong())
                "cat" -> Route.Category(Cat.valueOf(p[1]), p.getOrNull(2)?.ifEmpty { null })
                "day" -> Route.Day(p[1].toLong())
                "cats" -> Route.Cats(p[1])
                "search" -> Route.Search
                "recap" -> Route.Recap(p[1])
                else -> null
            }
        }.getOrNull()
    }
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
    val nav = rememberSaveable(saver = Nav.Saver) { Nav.start() }
    // Screens leave composition when something is pushed on top; this keeps their saved state until they come back.
    val saved = rememberSaveableStateHolder()
    nav.onDrop = saved::removeState
    val currentTab = (nav.stack.first().route as Route.Tabbed).tab
    BackHandler(enabled = nav.stack.size > 1 || currentTab != Tab.HOME) {
        if (nav.stack.size > 1) nav.pop() else nav.tab(Tab.HOME)
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = nav.top,
            transitionSpec = {
                val tabSwitch = initialState.route is Route.Tabbed && targetState.route is Route.Tabbed
                when {
                    tabSwitch -> fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                    nav.forward -> (slideInHorizontally(tween(360)) { it / 3 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(360)) { -it / 6 } + fadeOut(tween(200)))
                    else -> (slideInHorizontally(tween(360)) { -it / 6 } + fadeIn(tween(300))) togetherWith
                        (slideOutHorizontally(tween(360)) { it / 3 } + fadeOut(tween(200)))
                }
            },
            label = "route",
        ) { e ->
            saved.SaveableStateProvider(e.key) {
                when (val r = e.route) {
                    is Route.Tabbed -> when (r.tab) {
                        Tab.HOME -> HomeScreen(s, vm, nav)
                        Tab.ACTIVITY -> ActivityScreen(s, nav)
                        Tab.INSIGHTS -> InsightsScreen(s, nav)
                        Tab.ACCOUNTS -> AccountsScreen(s, vm, nav)
                    }
                    is Route.TxnDetail -> s.txns.firstOrNull { it.id == r.id }
                        ?.let { TxnScreen(it, s, vm, nav) } ?: LaunchedEffect(r) { nav.pop() }
                    is Route.Category -> CategoryScreen(r.cat, r.month?.let(java.time.YearMonth::parse), s, nav)
                    is Route.Day -> DayScreen(java.time.LocalDate.ofEpochDay(r.epochDay), s, nav)
                    Route.Search -> SearchScreen(s, nav)
                    is Route.Cats -> CatsScreen(java.time.YearMonth.parse(r.month), s, nav)
                    is Route.Recap -> RecapScreen(java.time.YearMonth.parse(r.month), s, nav)
                }
            }
        }
        if (nav.top.route is Route.Tabbed) {
            BottomBar(currentTab, nav::tab, Modifier.align(Alignment.BottomCenter))
        }
    }
}
