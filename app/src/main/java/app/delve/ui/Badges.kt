package app.delve.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.delve.domain.Mods
import app.delve.domain.Skill
import app.delve.ui.theme.C
import app.delve.ui.theme.T

/** Stat icons (stat_N): health, to hit, damage, armour, light, chase, luck, loot, torch, traps, healing, prices. */
fun statIcon(i: Int) = "stat_$i"

/** One of the hero's numbers on a small paper tile: its icon, the value, the name. */
@Composable
fun StatTile(icon: Int, value: String, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier.paper(C.floor).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Pic(statIcon(icon), Modifier.size(34.dp))
        Text(value, style = T.Number.copy(fontSize = T.Heading.fontSize), maxLines = 1)
        Text(label, style = T.Small.copy(fontSize = T.Eyebrow.fontSize), maxLines = 1)
    }
}

/** Mods as small icon + text lines (gear, skills, companions). */
@Composable
fun ModIcons(m: Mods, ru: Boolean, modifier: Modifier = Modifier, color: Color = C.ink2, center: Boolean = false) {
    Column(modifier, horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        for ((i, t) in m.parts(ru)) Row(verticalAlignment = Alignment.CenterVertically) {
            Pic(statIcon(i), Modifier.size(18.dp))
            HGap(4.dp)
            Text(t, style = T.Small.copy(color = color))
        }
    }
}

/**
 * A skill as a medallion: its emblem inside a ring. Chosen: a gold ring and a warm glow. Open to choose: a
 * gold dashed hint. Locked: faded, with the level it opens at.
 */
@Composable
fun SkillMedal(skill: Skill, ru: Boolean, chosen: Boolean, other: Boolean, open: Boolean, can: Boolean, modifier: Modifier = Modifier, size: Dp = 74.dp, onClick: () -> Unit) {
    val dim = (other && !chosen) || !open
    Column(modifier.alpha(if (dim) .42f else 1f).clickable(enabled = can, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size + 12.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(size + 12.dp).drawBehind {
                if (chosen) drawCircle(Brush.radialGradient(listOf(C.flame.copy(alpha = .45f), Color.Transparent)))
                if (can) drawCircle(C.gold, radius = size.toPx() / 2 + 4.dp.toPx(), style = Stroke(2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
            })
            Box(Modifier.size(size).clip(CircleShape).background(if (chosen) C.ink else C.floor)
                .border(if (chosen) 3.dp else 1.5.dp, if (chosen) C.gold else C.ink.copy(alpha = .6f), CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(size - 8.dp).clip(CircleShape).background(C.floor), contentAlignment = Alignment.Center) {
                    Pic("skill_${skill.id}", Modifier.size(size * .72f))
                }
            }
        }
        Text(skill.name(ru), style = T.Strong.copy(fontSize = T.Small.fontSize, color = if (chosen) C.ink else C.ink2), textAlign = TextAlign.Center, maxLines = 2)
        ModIcons(skill.mods, ru, center = true, color = if (chosen) C.green else C.muted)
    }
}

/** The level badge between a pair of skills. */
@Composable
fun LevelKnot(level: Int, open: Boolean, ru: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 26.dp)) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(if (open) C.gold else C.paper2).border(1.5.dp, C.ink, CircleShape), contentAlignment = Alignment.Center) {
            Text("$level", style = T.Heading.copy(color = C.ink))
        }
        Text(if (open) w(ru, "или", "or") else w(ru, "с $level ур.", "at lvl $level"), style = T.Note.copy(fontSize = T.Small.fontSize * 1.3f, color = C.muted))
    }
}

/** A horizontal ink rule with a soft fade at both ends, used between tiers. */
@Composable
fun FadeRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 6.dp).size(1.dp).drawBehind {
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, C.ink.copy(alpha = .35f), Color.Transparent)), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
    })
}

@Suppress("unused") private val spacing = Arrangement.spacedBy(4.dp)
