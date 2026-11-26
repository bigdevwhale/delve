package app.delve.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.delve.domain.Item
import app.delve.domain.Mods
import app.delve.domain.Rarity
import app.delve.domain.Slot
import app.delve.ui.theme.C
import app.delve.ui.theme.T

@Composable
fun ru(): Boolean = LocalConfiguration.current.locales[0].language == "ru"
fun w(ru: Boolean, a: String, b: String) = if (ru) a else b

/** Russian plural: 1 монета, 2 монеты, 5 монет. */
fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10; val m100 = n % 100
    return when { m10 == 1 && m100 != 11 -> one; m10 in 2..4 && m100 !in 12..14 -> few; else -> many }
}

fun coins(n: Int, ru: Boolean) = if (ru) "$n " + plural(n, "монета", "монеты", "монет") else "$n coin" + if (n == 1) "" else "s"
fun metres(m: Int, ru: Boolean) = if (m >= 1000) "%.1f %s".format(m / 1000.0, if (ru) "км" else "km") else "$m " + if (ru) "м" else "m"

/** Drawables by name: the art is cut from Codex sheets into res/drawable-nodpi (tools/cut.py). */
object Art {
    private val ids = HashMap<String, Int>()

    @SuppressLint("DiscouragedApi")
    fun id(c: Context, name: String): Int = ids.getOrPut(name) { c.resources.getIdentifier(name, "drawable", c.packageName) }

    fun beast(id: String) = "beast_$id"
    fun item(i: Int) = "item_$i"
    fun room(i: Int) = "room_$i"
    fun hero(cls: String, female: Boolean) = "hero_${cls}_${if (female) "f" else "m"}"
    fun camp(id: String) = "camp_$id"
}

@Composable
fun Pic(name: String, modifier: Modifier = Modifier, tint: ColorFilter? = null, alpha: Float = 1f) {
    val c = LocalContext.current
    val id = remember(name) { Art.id(c, name) }
    if (id != 0) Image(painterResource(id), null, modifier, colorFilter = tint, alpha = alpha)
    else Box(modifier)
}

/** A creature never met: a dark ink silhouette. */
val Silhouette = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(0f, 0f, 0f, 0f, 74f, 0f, 0f, 0f, 0f, 64f, 0f, 0f, 0f, 0f, 56f, 0f, 0f, 0f, .85f, 0f)))

fun rarityColor(r: Rarity) = when (r) { Rarity.COMMON -> C.muted; Rarity.FINE -> C.green; Rarity.RARE -> C.blue; Rarity.LEGEND -> C.gold }

fun slotName(s: Slot, ru: Boolean) = when (s) {
    Slot.WEAPON -> w(ru, "Оружие", "Weapon"); Slot.ARMOR -> w(ru, "Доспех", "Armour"); Slot.CHARM -> w(ru, "Амулет", "Charm"); Slot.LIGHT -> w(ru, "Свет", "Light")
}

/** A sheet of notebook paper: a soft fill, an ink rule and a hairline inside it. */
fun Modifier.paper(fill: Color = C.paper, ink: Color = C.ink, radius: Dp = 10.dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(fill)
    .drawBehind {
        val r = radius.toPx()
        drawRoundRect(ink, cornerRadius = CornerRadius(r), style = Stroke(1.4.dp.toPx()))
        val i = 3.5.dp.toPx()
        drawRoundRect(ink.copy(alpha = .28f), topLeft = Offset(i, i), size = Size(size.width - 2 * i, size.height - 2 * i), cornerRadius = CornerRadius((r - i).coerceAtLeast(1f)), style = Stroke(.8.dp.toPx()))
    }

@Composable
fun Card(modifier: Modifier = Modifier, fill: Color = C.paper, pad: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.paper(fill).padding(pad), content = content)
}

/** A section heading: a small caps eyebrow, then the title between two ink rules with a diamond. */
@Composable
fun Heading(eyebrow: String?, title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (eyebrow != null) Text(eyebrow.uppercase(), style = T.Eyebrow)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Rule(Modifier.weight(1f))
            Text(title, style = T.Title, modifier = Modifier.padding(horizontal = 10.dp), textAlign = TextAlign.Center)
            Rule(Modifier.weight(1f))
        }
    }
}

@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = C.ink) {
    Box(modifier.height(10.dp).drawBehind {
        val y = size.height / 2
        drawLine(color.copy(alpha = .6f), Offset(0f, y), Offset(size.width - 8.dp.toPx(), y), 1.dp.toPx())
        val p = Path().apply { val x = size.width - 4.dp.toPx(); moveTo(x, y - 3.dp.toPx()); lineTo(x + 3.dp.toPx(), y); lineTo(x, y + 3.dp.toPx()); lineTo(x - 3.dp.toPx(), y); close() }
        drawPath(p, color.copy(alpha = .7f))
    })
}

enum class BtnKind { INK, LINE, WAX, GOLD }

@Composable
fun Btn(text: String, modifier: Modifier = Modifier, kind: BtnKind = BtnKind.INK, enabled: Boolean = true, sub: String? = null, onClick: () -> Unit) {
    val (bg, fg, edge) = when (kind) {
        BtnKind.INK -> Triple(C.ink, C.paper, C.ink)
        BtnKind.LINE -> Triple(Color.Transparent, C.ink, C.ink)
        BtnKind.WAX -> Triple(C.red, C.floor, C.redDeep)
        BtnKind.GOLD -> Triple(C.gold, C.ink, C.ink)
    }
    val a = if (enabled) 1f else .38f
    Column(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).background(bg.copy(alpha = bg.alpha * a)).border(1.4.dp, edge.copy(alpha = a), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = T.Button.copy(color = fg.copy(alpha = a)), textAlign = TextAlign.Center)
        if (sub != null) Text(sub, style = T.Small.copy(color = fg.copy(alpha = .75f * a)), textAlign = TextAlign.Center)
    }
}

/** A thin bar like a pencil-drawn gauge. */
@Composable
fun Bar(value: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 7.dp) {
    Box(modifier.height(height).fillMaxWidth().clip(RoundedCornerShape(height)).background(C.ink.copy(alpha = .12f)).border(1.dp, C.ink.copy(alpha = .5f), RoundedCornerShape(height))) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(height)).background(color))
    }
}

@Composable
fun Pips(level: Int, max: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until max) Box(Modifier.size(8.dp).clip(CircleShape).background(if (i < level) C.gold else Color.Transparent).border(1.dp, C.ink.copy(alpha = .6f), CircleShape))
    }
}

/** An item's picture on a small paper tile, ringed in the colour of its rarity. */
@Composable
fun ItemTile(item: Item?, art: Int = item?.kind?.art ?: -1, modifier: Modifier = Modifier, size: Dp = 56.dp, onClick: (() -> Unit)? = null) {
    val ring = item?.let { rarityColor(it.rarity) } ?: C.ink.copy(alpha = .3f)
    Box(
        modifier.size(size).clip(RoundedCornerShape(8.dp)).background(C.floor).border(if (item != null && item.rarity != Rarity.COMMON) 2.dp else 1.dp, ring, RoundedCornerShape(8.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        if (art >= 0) Pic(Art.item(art), Modifier.size(size * .78f))
        if (item != null && item.plus > 0) Text("+${item.plus}", style = T.Small.copy(color = C.ink), modifier = Modifier.align(Alignment.BottomEnd).padding(3.dp))
    }
}

@Composable
fun ModLines(m: Mods, ru: Boolean, modifier: Modifier = Modifier, color: Color = C.ink2) {
    Column(modifier) { for (l in m.lines(ru)) Text(l, style = T.Small.copy(color = color)) }
}

@Composable
fun Gap(h: Dp = 12.dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(wd: Dp = 8.dp) = Spacer(Modifier.width(wd))

/** A material or potion with its count, small. */
@Composable
fun StuffChip(art: Int, n: Int, modifier: Modifier = Modifier, label: String? = null) {
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(C.floor).border(1.dp, C.ink.copy(alpha = .35f), RoundedCornerShape(16.dp)).padding(start = 4.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Pic(Art.item(art), Modifier.size(26.dp))
        HGap(4.dp)
        Text(if (label != null) "$label ×$n" else "×$n", style = T.Strong.copy(fontSize = T.Small.fontSize))
    }
}
