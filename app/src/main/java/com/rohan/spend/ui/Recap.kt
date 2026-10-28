package com.rohan.spend.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.rohan.spend.data.Recap
import com.rohan.spend.data.date
import com.rohan.spend.data.inr
import com.rohan.spend.data.recap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.abs

fun YearMonth.monthName(): String = month.getDisplayName(JTextStyle.FULL, Locale.getDefault())

/** A month told as one tall card, with a button to share it as a picture. */
@Composable
fun RecapScreen(ym: YearMonth, s: AppState, nav: Nav) {
    val st = s.stats
    val r = remember(st, ym, s.target) { recap(s.netted, st::counts, s::catOf, ym, s.today, usual = s.target) }
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val share = { scope.launch { shareImage(ctx, layer.toImageBitmap(), ym) }; Unit }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
        item { TopBar("${ym.monthName()} recap", nav::pop) { RoundButton(Icons.Rounded.Share, "Share", share) } }
        item {
            // Recorded as it draws, so Share sends exactly what's on screen.
            Box(
                Modifier.rise(0).padding(top = 6.dp)
                    .drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }
                    .background(C.bg),
            ) { RecapCard(r) }
        }
        item { PrimaryButton("Share", share, Modifier.rise(1).padding(top = 18.dp)) }
    }
}

private suspend fun shareImage(ctx: Context, img: ImageBitmap, ym: YearMonth) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        // The captured bitmap can live on the GPU, which can't be compressed directly.
        val bmp = img.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
        File(dir, "spend-recap-$ym.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(null, uri)
    ctx.startActivity(Intent.createChooser(send, "Share your ${ym.monthName()} recap"))
}

private val onDark = Color.White.copy(alpha = .62f)
private val recapDay = DateTimeFormatter.ofPattern("d MMM")

@Composable
fun RecapCard(r: Recap) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(C.ink).padding(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.month.format(DateTimeFormatter.ofPattern("MMMM yyyy")).uppercase(), style = T.tiny.copy(color = onDark, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp),
                modifier = Modifier.weight(1f))
            Text("Spend Lens", style = T.tiny.copy(color = onDark))
        }
        Spacer(Modifier.height(18.dp))
        Text("You spent", style = T.label.copy(color = onDark))
        Text(inr(r.spent), style = T.display.copy(color = Color.White), maxLines = 1)
        r.vsUsual?.let { pct ->
            val more = pct > 0
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (pct == 0) "Right on usual" else "${abs(pct)}% ${if (more) "more" else "less"} than usual",
                    style = T.tiny.copy(color = if (more) C.peachSoft else C.greenBright, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = .08f)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("usual ${inr(r.usual)}", style = T.tiny.copy(color = onDark))
            }
        }
        Spacer(Modifier.height(20.dp))
        RecapDays(r.daily)
        Spacer(Modifier.height(18.dp))
        val tiles = buildList<@Composable RowScope.() -> Unit> {
            r.top?.let { c -> add { RecapTile("Top category", inr(c.total), c.cat.label) } }
            r.shift?.let { c ->
                add {
                    RecapTile("Biggest change", (if (c.by > 0) "+" else "−") + inr(abs(c.by)),
                        "${c.cat.label}${if (c.before > 0) " · ${if (c.pct > 0) "+" else ""}${c.pct}%" else ""} vs usual",
                        if (c.by > 0) C.peachSoft else C.greenBright)
                }
            }
            add { RecapTile("No-spend days", "${r.noSpend}", "out of ${r.days}") }
            add { RecapTile("Payments", "${r.count}", if (r.count > 0) "about ${inr(r.spent / r.count)} each" else "none counted") }
            r.biggest?.let { t -> add { RecapTile("Biggest payment", inr(t.amount), "${t.payee} · ${t.date().format(recapDay)}") } }
            r.place?.let { p -> add { RecapTile("Top place", p.payee, "${inr(p.total)} · ${if (p.visits == 1) "1 visit" else "${p.visits} visits"}") } }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tiles.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { it() }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** One bar per day; empty days show as dots. */
@Composable
private fun RecapDays(daily: List<Long>) {
    val max = (daily.maxOrNull() ?: 0L).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        daily.forEach { v ->
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                if (v == 0L) Box(Modifier.size(4.dp).clip(CircleShape).background(C.greenBright.copy(alpha = .7f)))
                else Box(Modifier.fillMaxWidth().fillMaxHeight((v.toFloat() / max).coerceAtLeast(.08f)).clip(RoundedCornerShape(2.dp)).background(C.heat[2]))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text("1", style = T.tiny.copy(color = onDark, fontSize = 11.sp), modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(C.greenBright))
            Spacer(Modifier.width(5.dp))
            Text("no spending", style = T.tiny.copy(color = onDark, fontSize = 11.sp))
        }
        Text("${daily.size}", style = T.tiny.copy(color = onDark, fontSize = 11.sp), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun RowScope.RecapTile(label: String, big: String, sub: String, accent: Color = Color.White) {
    Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .07f)).padding(14.dp)) {
        Text(label, style = T.tiny.copy(color = onDark, fontSize = 12.sp))
        Spacer(Modifier.height(4.dp))
        Text(big, style = T.h2.copy(color = accent, fontSize = 20.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, style = T.tiny.copy(color = onDark, fontSize = 12.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Home's nudge on the first days of a month to look back at the last one. */
@Composable
fun RecapNudge(ym: YearMonth, spent: Long, modifier: Modifier = Modifier, onOpen: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.ink).press(onOpen).padding(start = 18.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Your ${ym.monthName()} recap", style = T.h3.copy(color = Color.White))
            Text("${inr(spent)} spent · tap to see how it went", style = T.tiny.copy(color = onDark), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(40.dp).clip(CircleShape).press(onClose), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, "Hide recap", tint = onDark, modifier = Modifier.size(18.dp))
        }
    }
}

/** True on the 1st to 5th, when last month is worth a look. */
fun recapDue(today: LocalDate): Boolean = today.dayOfMonth <= 5
