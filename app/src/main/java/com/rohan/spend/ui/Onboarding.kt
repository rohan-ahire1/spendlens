package com.rohan.spend.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.rohan.spend.data.Categorizer
import com.rohan.spend.data.groupIndian

@Composable
fun OnboardingScreen(onAllow: () -> Unit) {
    var how by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.rise(0).size(64.dp).clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(C.tint, C.peach))), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Sms, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("Your spending,\nread from your\nbank messages.", style = T.big, modifier = Modifier.rise(1))
        Spacer(Modifier.height(14.dp))
        Text("No bank login, no linking. Spend reads the SMS your bank already sends you and sorts it.",
            style = T.small.copy(fontSize = T.body.fontSize, lineHeight = T.body.fontSize * 1.4f), modifier = Modifier.rise(2))
        Spacer(Modifier.height(28.dp))
        FrostCard(Modifier.rise(3)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Point(Icons.Rounded.Sms, "Reads bank messages only", "OTPs and personal chats are skipped.")
                Point(Icons.Rounded.WifiOff, "Never leaves the phone", "No internet permission. Nothing is uploaded.")
                Point(Icons.Rounded.Lock, "You stay in control", "Turn off SMS access any time in settings.")
            }
        }
        Spacer(Modifier.weight(1f))
        AnimatedVisibility(how, enter = fadeIn() + expandVertically()) {
            Text(
                "Banks send a message for every UPI, card and account debit or credit. Spend finds those, pulls out the amount, " +
                    "who you paid and the account, then puts each one in a category. You can fix any category and it learns.",
                style = T.small, modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        PrimaryButton("Allow SMS access", onAllow, Modifier.rise(4))
        GhostButton(if (how) "Got it" else "How does this work?", { how = !how })
    }
}

@Composable
private fun Point(icon: ImageVector, title: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = C.ink, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = T.body.copy(fontWeight = T.h3.fontWeight))
            Text(sub, style = T.tiny)
        }
    }
}

@Composable
fun ScanScreen(s: AppState) {
    val target = if (s.total == 0) 0f else s.done.toFloat() / s.total
    val progress by animateFloatAsState(target, tween(400, easing = FastOutSlowInEasing), label = "scan")
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(.8f))
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().rotate(angle)) {
                drawCircle(Brush.sweepGradient(listOf(C.tint.copy(alpha = 0f), C.tint.copy(alpha = .35f), C.peach.copy(alpha = 0f))),
                    radius = size.minDimension / 2)
            }
            Canvas(Modifier.size(180.dp)) {
                val st = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
                drawArc(Color.White.copy(alpha = .8f), 0f, 360f, false, style = st)
                drawArc(Brush.sweepGradient(listOf(C.tint, C.peach, C.tint), Offset(size.width / 2, size.height / 2)),
                    -90f, 360f * progress, false, style = st)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(progress * 100).toInt()}%", style = T.title)
                Text("${groupIndian(s.done.toLong())} of ${groupIndian(s.total.toLong())}", style = T.tiny)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("Reading your messages", style = T.h2)
        Text("Picking out UPI, card and bank payments", style = T.small,
            modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(24.dp))
        FrostCard(Modifier.animateContentSize()) {
            Column {
                if (s.justFound.isEmpty()) EmptyNote("Looking for bank messages…")
                s.justFound.forEach { t ->
                    androidx.compose.runtime.key(t.id) {
                        Box(Modifier.rise()) { TxnRow(t, Categorizer.auto(t), null, {}, showDay = true) }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Everything stays on this phone.", style = T.tiny, modifier = Modifier.padding(bottom = 16.dp))
    }
}
