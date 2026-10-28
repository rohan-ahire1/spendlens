package com.rohan.spend.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rohan.spend.data.Cat

/** One short message at the bottom of the screen. [id] tells two identical messages apart. */
data class Snack(val id: Long, val text: String, val undo: Boolean)

/** The edits an undo can put back. Notes and settings aren't here: nothing undoable touches them. */
data class Edits(val rules: Map<String, Cat>, val txnCats: Map<Long, Cat>, val hidden: Set<Long>) {
    /**
     * Puts back only what changed between [before] and [after], so anything else edited since
     * is kept.
     */
    fun revert(before: Edits, after: Edits) = Edits(
        undoMap(rules, before.rules, after.rules),
        undoMap(txnCats, before.txnCats, after.txnCats),
        hidden - (after.hidden - before.hidden) + (before.hidden - after.hidden),
    )

    companion object {
        fun of(s: AppState) = Edits(s.payeeRules, s.txnCats, s.hidden)
    }
}

private fun <K, V> undoMap(now: Map<K, V>, before: Map<K, V>, after: Map<K, V>): Map<K, V> {
    val out = now.toMutableMap()
    for (k in before.keys + after.keys) {
        if (before[k] == after[k]) continue
        val v = before[k]
        if (v == null) out.remove(k) else out[k] = v
    }
    return out
}

/** The bar's pill is 60dp tall on 16dp of padding; this leaves an 8dp gap above it. */
val SnackLift = 84.dp
val SnackLiftDetail = 16.dp

fun Modifier.snackPadding(lift: Dp): Modifier = navigationBarsPadding().padding(bottom = lift)

/** Dark pill that rises from the bottom, with Undo when the change can be taken back. */
@Composable
fun SnackHost(snack: Snack?, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    // Keep showing the last message while it slides away.
    val last = remember { arrayOfNulls<Snack>(1) }
    if (snack != null) last[0] = snack
    AnimatedVisibility(
        snack != null, modifier,
        enter = slideInVertically(tween(260)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
    ) {
        val s = last[0] ?: return@AnimatedVisibility
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = 52.dp).clip(CircleShape).background(C.ink)
                .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(s.text, style = T.label.copy(color = Color.White), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (s.undo) {
                Spacer(Modifier.width(10.dp))
                Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = .14f)).press(onUndo, haptic = true).padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("Undo", style = T.label.copy(color = Color.White, fontWeight = FontWeight.Bold))
                }
            } else Spacer(Modifier.width(14.dp))
        }
    }
}
