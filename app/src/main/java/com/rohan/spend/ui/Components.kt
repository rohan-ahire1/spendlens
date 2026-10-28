package com.rohan.spend.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.rohan.spend.data.Cat
import com.rohan.spend.data.Txn
import com.rohan.spend.data.date
import com.rohan.spend.data.inr
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

// ---------- Surfaces ----------

/**
 * Soft coloured light behind the frosted cards. It drifts only while [animate] is on:
 * a moving background forces every card shadow to redraw each frame, which costs battery.
 */
@Composable
fun FrostBackground(modifier: Modifier = Modifier, animate: Boolean = true, blobs: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    val glow by animateFloatAsState(if (blobs) 1f else 0f, tween(900), label = "glow")
    val pa = remember { Animatable(.5f) }
    val pb = remember { Animatable(.5f) }
    LaunchedEffect(animate) {
        if (!animate) {
            launch { pa.animateTo(.5f, tween(1600, easing = FastOutSlowInEasing)) }
            pb.animateTo(.5f, tween(1600, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        launch { while (true) { pa.animateTo(1f, tween(7000, easing = LinearEasing)); pa.animateTo(0f, tween(14000, easing = LinearEasing)) } }
        while (true) { pb.animateTo(0f, tween(9500, easing = LinearEasing)); pb.animateTo(1f, tween(19000, easing = LinearEasing)) }
    }
    Box(modifier.fillMaxSize().background(C.bg)) {
        if (glow > 0f) Canvas(Modifier.fillMaxSize()) {
            val a = pa.value
            val b = pb.value
            val w = size.width
            val h = size.height
            fun blob(color: Color, x: Float, y: Float, r: Float) = drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = .55f * glow), color.copy(alpha = 0f)), Offset(x, y), r),
                radius = r, center = Offset(x, y),
            )
            blob(C.tint, w * (.15f + .25f * a), h * (.08f + .06f * b), w * .75f)
            blob(C.peach, w * (.95f - .2f * b), h * (.22f + .08f * a), w * .65f)
            blob(C.mint, w * (.3f + .3f * b), h * (.62f - .08f * a), w * .7f)
        }
        content()
    }
}

/** A flat white card on the cool ground. No shadows: they stalled rendering to ~2s a frame. */
fun Modifier.frost(radius: Dp = 24.dp, shape: Shape = RoundedCornerShape(radius)): Modifier = this
    .clip(shape)
    .background(C.card)

@Composable
fun FrostCard(modifier: Modifier = Modifier, radius: Dp = 24.dp, pad: Dp = 18.dp, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().then(if (onClick != null) Modifier.press(onClick) else Modifier).frost(radius).padding(pad)) {
        content()
    }
}

// ---------- Motion ----------

/** Shrinks a touch on press, springs back, and gives a light tick. */
fun Modifier.press(onClick: () -> Unit, haptic: Boolean = false): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, spring(dampingRatio = .6f, stiffness = Spring.StiffnessMedium), label = "press")
    val feel = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(src, indication = null) {
            if (haptic) feel.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Fades and lifts content in, staggered by [index]. */
fun Modifier.rise(index: Int = 0): Modifier = composed {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(40L * index.coerceAtMost(10))
        p.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 24.dp.toPx()
    }
}

/** A rupee figure that counts up to its value. */
@Composable
fun CountUp(paise: Long, style: TextStyle, modifier: Modifier = Modifier, signed: Boolean = false) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(paise) { anim.animateTo(paise.toFloat(), tween(900, easing = FastOutSlowInEasing)) }
    Text(inr(anim.value.roundToLong(), signed), style = style, modifier = modifier, maxLines = 1)
}

// ---------- Small pieces ----------

@Composable
fun CatIcon(cat: Cat, size: Dp = 40.dp) {
    val s = cat.style()
    Box(Modifier.size(size).clip(RoundedCornerShape(size * .32f)).background(s.bg), contentAlignment = Alignment.Center) {
        Icon(s.icon, null, tint = s.fg, modifier = Modifier.size(size * .5f))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (selected) C.ink else Color.White.copy(alpha = .7f), label = "chipBg")
    val fg by animateColorAsState(if (selected) Color.White else C.ink, label = "chipFg")
    Box(
        modifier.press(onClick, haptic = true).clip(CircleShape).background(bg)
            .border(1.dp, if (selected) C.ink else Color.White, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, style = T.label.copy(color = fg)) }
}

@Composable
fun FrostSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (checked) 20.dp else 2.dp, spring(dampingRatio = .65f, stiffness = 600f), label = "knob")
    val track by animateColorAsState(if (checked) C.ink else C.track, label = "track")
    Box(Modifier.width(46.dp).height(28.dp).clip(CircleShape).background(track).press({ onChange(!checked) }, haptic = true)) {
        Box(Modifier.offset(x = x, y = 2.dp).size(24.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = T.h3, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
        Text(title, style = T.h3, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

@Composable
fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, size: Dp = 42.dp) {
    Box(Modifier.size(size).press(onClick).frost(shape = CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = C.ink, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(56.dp).press(onClick, haptic = true)
            .shadow(14.dp, CircleShape, spotColor = C.tint).clip(CircleShape).background(C.ink),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.h3.copy(color = Color.White)) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(50.dp).press(onClick), contentAlignment = Alignment.Center) {
        Text(text, style = T.label.copy(color = C.ink))
    }
}

@Composable
fun Pill(text: String, fg: Color, bg: Color) {
    Text(text, style = T.tiny.copy(color = fg), modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 8.dp, vertical = 3.dp))
}

/** A horizontal bar that grows in from the left. */
@Composable
fun GrowBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(C.track)) {
        Box(Modifier.fillMaxWidth(f).height(height).clip(CircleShape).background(color))
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a")
private val dayFmt = DateTimeFormatter.ofPattern("d MMM")

fun dayLabel(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> d.format(if (d.year == today.year) dayFmt else DateTimeFormatter.ofPattern("d MMM yyyy"))
}

fun Txn.timeLabel(): String = java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).format(timeFmt)

@Composable
fun TxnRow(t: Txn, cat: Cat, note: String?, onClick: () -> Unit, showDay: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().press(onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatIcon(cat)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.payee, style = T.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildString {
                append(cat.label)
                append(" · ")
                append(if (showDay) dayLabel(t.date()) else t.timeLabel())
                if (!note.isNullOrBlank()) append(" · ").append(note)
            }
            Text(sub, style = T.tiny, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(inr(t.signed, signed = !t.debit), style = T.num.copy(color = if (t.debit) C.ink else C.green))
    }
}

// ---------- Bottom bar ----------

enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    ACTIVITY("Activity", Icons.AutoMirrored.Rounded.ReceiptLong),
    INSIGHTS("Insights", Icons.Rounded.Insights),
    ACCOUNTS("Accounts", Icons.Rounded.AccountBalance),
}

/** Floating dark pill of text tabs with a white indicator that slides between them. */
@Composable
fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val gap = 4.dp
    // Fade the list out under the bar so rows never show through it.
    val scrim = Brush.verticalGradient(0f to Color.Transparent, .45f to C.bg.copy(alpha = .95f), 1f to C.bg)
    Box(modifier.fillMaxWidth().background(scrim).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 16.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(60.dp).clip(CircleShape).background(C.ink).padding(5.dp)) {
            val n = Tab.entries.size
            val itemW = (maxWidth - gap * (n - 1)) / n
            val x by animateDpAsState((itemW + gap) * selected.ordinal, spring(dampingRatio = .75f, stiffness = 380f), label = "pill")
            Box(Modifier.offset(x = x).width(itemW).fillMaxHeight().clip(CircleShape).background(Color.White))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Tab.entries.forEach { tab ->
                    val on = tab == selected
                    val tint by animateColorAsState(if (on) C.ink else C.barOff, label = "tabTint")
                    Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).press({ onSelect(tab) }, haptic = true), contentAlignment = Alignment.Center) {
                        Text(tab.label, style = TextStyle(fontFamily = Plex, fontSize = 13.sp, color = tint, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Keeps the bottom of a scrolling list clear of the floating bar. */
val BarClearance = 120.dp

@Composable
fun EmptyNote(text: String) {
    Text(text, style = T.small, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}
