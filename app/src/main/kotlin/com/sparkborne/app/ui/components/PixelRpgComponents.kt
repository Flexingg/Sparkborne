package com.sparkborne.app.ui.components

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sparkborne.app.R
import com.sparkborne.app.data.AppUpdateInfo
import com.sparkborne.app.ui.theme.RpgColors
import com.sparkborne.game.model.MonsterAffinity

@DrawableRes
fun monsterDrawableFor(affinity: MonsterAffinity): Int {
    return when (affinity) {
        MonsterAffinity.EMBERHEART -> R.drawable.pixel_monster_ember
        MonsterAffinity.IRONCLAD -> R.drawable.pixel_monster_ironclad
        MonsterAffinity.STORMSTRIDER -> R.drawable.pixel_monster_storm
        MonsterAffinity.DEEPFORGE -> R.drawable.pixel_monster_deepforge
    }
}

/**
 * Renders a crisp 64-bit pixel art sprite using nearest-neighbor sampling ([FilterQuality.None])
 * inside a WalkScape-inspired beveled dark frame.
 */
@Composable
fun PixelSprite(
    @DrawableRes drawableRes: Int,
    contentDescription: String,
    size: Dp,
    modifier: Modifier = Modifier,
    borderColor: Color = RpgColors.PixelBorderBright,
) {
    val context = LocalContext.current
    val bitmap = remember(drawableRes) {
        BitmapFactory.decodeResource(context.resources, drawableRes).asImageBitmap()
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(RpgColors.PanelSurface)
            .border(2.dp, borderColor, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.None,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
fun PixelBannerImage(
    @DrawableRes drawableRes: Int,
    contentDescription: String,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap = remember(drawableRes) {
        BitmapFactory.decodeResource(context.resources, drawableRes).asImageBitmap()
    }
    Image(
        bitmap = bitmap,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.None,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    )
}

/**
 * WalkScape-style dark slate RPG panel with crisp pixel border.
 */
@Composable
fun RpgPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = RpgColors.PixelBorder,
    containerColor: Color = RpgColors.PanelSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = BorderStroke(1.5.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/**
 * Chunky animated RPG progress bar with crisp border and monospace value readout.
 */
@Composable
fun RpgProgressBar(
    label: String,
    valueText: String,
    progress: Float,
    fillColor: Color,
    modifier: Modifier = Modifier,
    barHeight: Dp = 10.dp,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350),
        label = "rpg_bar",
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = RpgColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                color = fillColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(RoundedCornerShape(4.dp))
                .background(RpgColors.ObsidianBg)
                .border(1.dp, RpgColors.PixelBorder, RoundedCornerShape(4.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .background(fillColor),
            )
        }
    }
}

@Composable
fun RpgStatChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = RpgColors.ObsidianBg,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = RpgColors.TextMuted,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun UpdateAvailableBanner(
    update: AppUpdateInfo,
    downloadProgress: Float?,
    onDownloadAndInstall: () -> Unit,
) {
    RpgPanel(
        borderColor = RpgColors.EmberGold,
        containerColor = Color(0xFF241D11),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Update Ready · ${update.tagName}",
                    style = MaterialTheme.typography.titleSmall,
                    color = RpgColors.EmberGold,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Updates in-place without losing your save or API key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RpgColors.TextMuted,
                )
            }
            Button(
                onClick = onDownloadAndInstall,
                enabled = downloadProgress == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RpgColors.EmberGold,
                    contentColor = Color(0xFF1A1205),
                ),
            ) {
                Text(if (downloadProgress == null) "Update" else "${(downloadProgress * 100).toInt()}%")
            }
        }
        if (downloadProgress != null) {
            LinearProgressIndicator(
                progress = { downloadProgress },
                modifier = Modifier.fillMaxWidth(),
                color = RpgColors.EmberGold,
            )
        }
    }
}
