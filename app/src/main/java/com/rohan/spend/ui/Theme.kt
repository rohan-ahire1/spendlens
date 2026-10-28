package com.rohan.spend.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.CreditScore
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.DirectionsSubway
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.LocalAtm
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rohan.spend.R
import com.rohan.spend.data.Cat

/** Frost Light palette, lifted from the design canvas. */
object C {
    val bg = Color(0xFFEEF0F6)
    val ink = Color(0xFF15161B)
    val muted = Color(0xFF5F6270)
    val subtle = Color(0xFF6B6E7A)
    val faint = Color(0xFF9A9CAA)
    val line = Color(0x0F15161B)
    val track = Color(0x1215161B)
    val tint = Color(0xFF8B7DFF)
    val peach = Color(0xFFFF8F6B)
    val peachSoft = Color(0xFFFFB38A)
    val mint = Color(0xFF7FD8C8)
    val sun = Color(0xFFFFD66B)
    val green = Color(0xFF178A57)
    val greenBright = Color(0xFF3CBF86)
    val red = Color(0xFFB3261E)
    val warnInk = Color(0xFFA64D0E)
    val warnBg = Color(0xFFFFEEDD)
}

@OptIn(ExperimentalTextApi::class)
private fun inter(w: Int) = Font(R.font.inter, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

/** Inter, the same face Linear uses, from one bundled variable font. */
val Inter = FontFamily(inter(400), inter(500), inter(600), inter(700))

object T {
    private const val TNUM = "tnum"
    val hero = TextStyle(fontFamily = Inter, fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.6).sp, color = C.ink, fontFeatureSettings = TNUM)
    val big = TextStyle(fontFamily = Inter, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp, color = C.ink, lineHeight = 37.sp)
    val title = TextStyle(fontFamily = Inter, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.9).sp, color = C.ink)
    val h2 = TextStyle(fontFamily = Inter, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, color = C.ink)
    val h3 = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = C.ink)
    val body = TextStyle(fontFamily = Inter, fontSize = 15.sp, color = C.ink, letterSpacing = (-0.1).sp)
    val num = body.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = TNUM)
    val label = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = C.muted)
    val small = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = C.muted, lineHeight = 18.sp)
    val tiny = TextStyle(fontFamily = Inter, fontSize = 12.sp, color = C.subtle)
}

data class CatStyle(val icon: ImageVector, val fg: Color, val bg: Color)

fun Cat.style(): CatStyle = when (this) {
    Cat.GROCERIES -> CatStyle(Icons.Rounded.LocalGroceryStore, Color(0xFF9A7300), Color(0xFFFFF3CC))
    Cat.FOOD_DELIVERY -> CatStyle(Icons.Rounded.DeliveryDining, Color(0xFFC4501F), Color(0xFFFFE9E0))
    Cat.EATING_OUT -> CatStyle(Icons.Rounded.Restaurant, Color(0xFFB8336A), Color(0xFFFFE4EF))
    Cat.COMMUTE -> CatStyle(Icons.Rounded.DirectionsSubway, Color(0xFF1F6FC4), Color(0xFFDEF0FF))
    Cat.SUBSCRIPTIONS -> CatStyle(Icons.Rounded.CreditCard, Color(0xFF4B3FD6), Color(0xFFE8E5FF))
    Cat.SHOPPING -> CatStyle(Icons.Rounded.ShoppingBag, Color(0xFF7A3FC4), Color(0xFFF1E5FF))
    Cat.BILLS -> CatStyle(Icons.Rounded.Bolt, Color(0xFF8A6A00), Color(0xFFFFF6D6))
    Cat.MEDICAL -> CatStyle(Icons.Rounded.LocalPharmacy, Color(0xFFB3261E), Color(0xFFFFE5E3))
    Cat.MOVIES -> CatStyle(Icons.Rounded.Movie, Color(0xFF5B4A9E), Color(0xFFECE8FA))
    Cat.TRAVEL -> CatStyle(Icons.Rounded.Flight, Color(0xFF0F7C8C), Color(0xFFDDF5F7))
    Cat.SALON -> CatStyle(Icons.Rounded.ContentCut, Color(0xFF9E4A7A), Color(0xFFF8E6F0))
    Cat.CASH -> CatStyle(Icons.Rounded.LocalAtm, Color(0xFF3F6B2A), Color(0xFFE6F2DD))
    Cat.PEOPLE -> CatStyle(Icons.Rounded.Person, Color(0xFF5F6270), Color(0xFFF0F0F4))
    Cat.SMALL -> CatStyle(Icons.Rounded.Payments, Color(0xFF178A57), Color(0xFFDFF5EA))
    Cat.FAMILY -> CatStyle(Icons.Rounded.FamilyRestroom, Color(0xFFC4501F), Color(0xFFFFEEDD))
    Cat.INVESTING -> CatStyle(Icons.AutoMirrored.Rounded.TrendingUp, Color(0xFF178A57), Color(0xFFDFF5EA))
    Cat.CARD_BILL -> CatStyle(Icons.Rounded.CreditScore, Color(0xFF15161B), Color(0xFFE9EAF0))
    Cat.ONE_OFF -> CatStyle(Icons.Rounded.Star, Color(0xFF9A7300), Color(0xFFFFF3CC))
    Cat.SALARY -> CatStyle(Icons.Rounded.Work, Color(0xFF178A57), Color(0xFFDFF5EA))
    Cat.REFUND -> CatStyle(Icons.AutoMirrored.Rounded.Undo, Color(0xFF178A57), Color(0xFFDFF5EA))
    Cat.RECEIVED -> CatStyle(Icons.Rounded.SouthWest, Color(0xFF178A57), Color(0xFFDFF5EA))
}
