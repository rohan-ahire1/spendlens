package com.rohan.spend.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Payments the user hid. Nothing else lists them, so this is the way back. */
@Composable
fun HiddenScreen(s: AppState, vm: AppViewModel, nav: Nav) {
    val list = remember(s.txns, s.hidden) { s.txns.filter { it.id in s.hidden }.sortedByDescending { it.time } }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
    ) {
        item { TopBar("Hidden payments", nav::pop) }
        item {
            Text("These stay out of every total, list and search. Tap one to see it, or bring it back with the eye.",
                style = T.small, modifier = Modifier.rise(0).padding(top = 4.dp, bottom = 8.dp))
        }
        if (list.isEmpty()) item { EmptyNote("Nothing hidden. Hide a payment from its page to keep it out of your totals.") }
        items(list, key = { it.id }) { t ->
            Row(Modifier.animateItem().fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    TxnRow(t, s.catOf(t), s.notes[t.id], { nav.push(Route.TxnDetail(t.id)) }, showDay = true)
                }
                Spacer(Modifier.width(10.dp))
                RoundButton(Icons.Rounded.Visibility, "Count again", { vm.toggleHidden(t) }, size = 36.dp)
            }
        }
    }
}
