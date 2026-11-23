package com.sparkborne.app.ui.hero

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sparkborne.app.ui.theme.RpgTheme
import com.sparkborne.game.model.HeroAppearance
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Character creation (and, later, the Mirror for restyling). Purely cosmetic.
 *
 * @param gearLook the hero's currently equipped look, used for the "In Gear" preview toggle.
 */
@Composable
fun CharacterCreationScreen(
    initial: HeroAppearance,
    gearLook: HeroLook?,
    isNew: Boolean,
    errorMessage: String? = null,
    onConfirm: (HeroAppearance) -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val colors = RpgTheme.colors
    var look by remember { mutableStateOf(initial) }
    var inGear by remember { mutableStateOf(!isNew && gearLook != null) }
    val preview = if (inGear && gearLook != null) gearLook.copy(appearance = look) else HeroLook(look, weaponId = gearLook?.weaponId ?: "wpn_iron_greatsword")

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.obsidianBg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text8(if (isNew) "FORGE YOUR HERO" else "THE MIRROR", size = 20.sp, color = GoldTrim, weight = FontWeight.ExtraBold, letterSpacing = 3.sp)
            Text8(
                if (isNew) "Every legend begins with a face. Choose yours." else "A fresh look costs nothing but a moment's vanity.",
                size = 11.sp,
                color = colors.textMuted,
                align = TextAlign.Center,
                italic = true,
                weight = FontWeight.Normal,
            )

            CreationStage(preview)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (gearLook != null) {
                    ToggleChip("BARE", !inGear) { inGear = false }
                    ToggleChip("IN GEAR", inGear) { inGear = true }
                }
                PixelButton("🎲 RANDOMIZE", colors.accentSecondary, small = true) { look = randomLook(look.name) }
            }

            NameField(look.name) { look = look.copy(name = it.take(HeroAppearance.NAME_MAX)) }

            CreationSection("BODY") {
                SwatchPicker("Skin", HeroPalette.skin, HeroPalette.skinNames, look.skinTone) { look = look.copy(skinTone = it) }
                ArrowPicker("Build", HeroPalette.buildNames, look.build) { look = look.copy(build = it) }
            }
            CreationSection("FACE") {
                SwatchPicker("Eyes", HeroPalette.eyes, HeroPalette.eyeNames, look.eyeColor) { look = look.copy(eyeColor = it) }
                ArrowPicker("Facial Hair", HeroPalette.facialHairNames, look.facialHair) { look = look.copy(facialHair = it) }
                ArrowPicker("Mark", HeroPalette.faceMarkNames, look.faceMark) { look = look.copy(faceMark = it) }
            }
            CreationSection("HAIR") {
                ArrowPicker("Style", HeroPalette.hairStyleNames, look.hairStyle) { look = look.copy(hairStyle = it) }
                SwatchPicker("Colour", HeroPalette.hair, HeroPalette.hairNames, look.hairColor) { look = look.copy(hairColor = it) }
            }
            CreationSection("ATTIRE") {
                SwatchPicker("Tunic", HeroPalette.tunics, HeroPalette.tunicNames, look.tunic) { look = look.copy(tunic = it) }
                SwatchPicker("Cape", HeroPalette.capes, HeroPalette.capeNames, look.cape) { look = look.copy(cape = it) }
            }

            if (errorMessage != null) Text8("⚠ $errorMessage", size = 10.sp, color = colors.crimsonHp, align = TextAlign.Center)
            val nameOk = look.name.isNotBlank()
            PixelButton(
                if (isNew) "⚔ BEGIN YOUR LEGEND" else "🪞 KEEP THIS LOOK",
                GoldTrim,
                enabled = nameOk,
            ) { onConfirm(look) }
            if (!nameOk) Text8("Your hero needs a name.", size = 10.sp, color = colors.textMuted)
            if (onCancel != null) {
                Text8("✕ leave the mirror", size = 11.sp, color = colors.textMuted, modifier = Modifier.clickable(onClick = onCancel).padding(8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun randomLook(name: String): HeroAppearance {
    val r = Random(System.nanoTime())
    return HeroAppearance(
        name = name,
        skinTone = r.nextInt(HeroAppearance.SKIN_TONES),
        build = r.nextInt(HeroAppearance.BUILDS),
        hairStyle = r.nextInt(HeroAppearance.HAIR_STYLES),
        hairColor = r.nextInt(HeroAppearance.HAIR_COLORS),
        eyeColor = r.nextInt(HeroAppearance.EYE_COLORS),
        facialHair = if (r.nextInt(3) == 0) r.nextInt(HeroAppearance.FACIAL_HAIR) else 0,
        faceMark = if (r.nextInt(3) == 0) r.nextInt(HeroAppearance.FACE_MARKS) else 0,
        cape = r.nextInt(HeroAppearance.CAPES),
        tunic = r.nextInt(HeroAppearance.TUNICS),
    )
}

@Composable
private fun CreationStage(look: HeroLook) {
    val colors = RpgTheme.colors
    val accent = colors.accentPrimary
    val tr = rememberInfiniteTransition(label = "stage")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "stage_t")
    Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val u = floor(3.dp.toPx())
            val w = size.width
            val h = size.height
            chamfer(0f, 0f, w, h, u, InkDark)
            drawRect(Brush.verticalGradient(listOf(Color(0xFF181A26), Color(0xFF0C0D14))), Offset(u, u), Size(w - 2 * u, h - 2 * u))
            // spotlight
            val c = Offset(w / 2, h * 0.45f)
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent), c, h * 0.55f), h * 0.55f, c)
            // pillars
            listOf(w * 0.1f, w * 0.9f).forEach { px ->
                drawRect(Color(0xFF2B2E3A), Offset(px - 5 * u, h * 0.12f), Size(10 * u, h * 0.78f))
                drawRect(Color(0xFF3D4252), Offset(px - 5 * u, h * 0.12f), Size(2 * u, h * 0.78f))
                drawRect(Color(0xFF454A5C), Offset(px - 7 * u, h * 0.1f), Size(14 * u, 3 * u))
                val fl = 0.7f + 0.3f * sin(t * 6.283f * 3 + px)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFA040).copy(alpha = 0.35f * fl), Color.Transparent), Offset(px, h * 0.08f), w * 0.15f), w * 0.15f, Offset(px, h * 0.08f))
                drawRect(Color(0xFFFF7A3D), Offset(px - u, h * 0.08f - 2 * u), Size(2 * u, 3 * u))
            }
            // rune circle floor
            val fy = h * 0.86f
            drawOval(accent.copy(alpha = 0.18f), Offset(w / 2 - w * 0.28f, fy - h * 0.05f), Size(w * 0.56f, h * 0.1f))
            drawOval(accent.copy(alpha = 0.8f), Offset(w / 2 - w * 0.26f, fy - h * 0.045f), Size(w * 0.52f, h * 0.09f), style = Stroke(u))
            for (i in 0 until 14) {
                val a = (i / 14f + t / 2f) * 6.283f
                drawRect(accent, Offset(floor((w / 2 + kotlin.math.cos(a) * w * 0.26f) / u) * u, floor((fy + sin(a) * h * 0.045f) / u) * u), Size(u, u))
            }
        }
        Box(Modifier.padding(top = 8.dp)) { HeroSprite(look, 260.dp) }
    }
}

@Composable
private fun ToggleChip(label: String, on: Boolean, onClick: () -> Unit) {
    val colors = RpgTheme.colors
    Box(
        Modifier
            .pixelFrame(fill = if (on) GoldTrim else InkDark, edge = if (on) GoldTrim.mixWith(Color.White, 0.3f) else colors.pixelBorderBright, style = FrameStyle.PLATE, unit = 2.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) { Text8(label, size = 10.sp, color = if (on) InkDark else colors.textMuted, weight = FontWeight.ExtraBold) }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    val colors = RpgTheme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text8("NAME", size = 10.sp, color = colors.textMuted, letterSpacing = 2.sp)
        Box(
            Modifier
                .fillMaxWidth()
                .pixelFrame(fill = InkDark, edge = GoldTrim.copy(alpha = 0.7f), style = FrameStyle.SOCKET, unit = 2.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (value.isEmpty()) Text8("Name your hero…", size = 16.sp, color = colors.textMuted.copy(alpha = 0.5f), weight = FontWeight.Normal)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = GoldTrim, textAlign = TextAlign.Center, letterSpacing = 2.sp),
                cursorBrush = SolidColor(GoldTrim),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CreationSection(title: String, content: @Composable () -> Unit) {
    val colors = RpgTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .pixelFrame(fill = colors.panelSurface, edge = colors.pixelBorderBright)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RuneHeader(title, color = GoldTrim)
            content()
        }
    }
}

@Composable
private fun ArrowPicker(label: String, names: List<String>, index: Int, onChange: (Int) -> Unit) {
    val colors = RpgTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text8(label.uppercase(), size = 10.sp, color = colors.textMuted, letterSpacing = 1.5.sp, modifier = Modifier.width(92.dp))
        PixelButton("◀", colors.pixelBorderBright, small = true) { onChange((index - 1 + names.size) % names.size) }
        Text8(names.getOrElse(index) { "?" }, size = 12.sp, color = colors.textPrimary, align = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1)
        PixelButton("▶", colors.pixelBorderBright, small = true) { onChange((index + 1) % names.size) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwatchPicker(label: String, swatches: List<Color?>, names: List<String>, index: Int, onChange: (Int) -> Unit) {
    val colors = RpgTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text8(label.uppercase(), size = 10.sp, color = colors.textMuted, letterSpacing = 1.5.sp, modifier = Modifier.width(92.dp))
            Text8(names.getOrElse(index) { "" }, size = 12.sp, color = colors.textPrimary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            swatches.forEachIndexed { i, c ->
                val selected = i == index
                Box(
                    Modifier
                        .size(32.dp)
                        .pixelFrame(fill = c ?: Color(0xFF1C1F2A), edge = if (selected) GoldTrim else colors.pixelBorder, style = if (selected) FrameStyle.WINDOW else FrameStyle.SOCKET, unit = 2.dp)
                        .clickable { onChange(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (c == null) Text8("∅", size = 13.sp, color = colors.textMuted)
                }
            }
        }
    }
}
