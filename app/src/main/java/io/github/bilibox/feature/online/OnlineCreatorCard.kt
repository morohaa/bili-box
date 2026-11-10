package io.github.bilibox.feature.online

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.R
import io.github.bilibox.core.ui.component.RemoteCover
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.PlaybackDivider
import io.github.bilibox.core.ui.theme.PlaybackMuted
import io.github.bilibox.core.ui.theme.PlaybackSurface
import io.github.bilibox.core.ui.theme.PlaybackText

/** Creator card with the reference design's pink accent line on the left. */
@Composable
internal fun OnlineCreatorCard(
    name: String,
    avatar: String,
    fans: Long?,
    archiveCount: Long?,
    level: Int?,
    onFollowUnavailable: () -> Unit,
) {
    val accent = OnlinePink
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(vertical = 8.dp)
            .drawBehind {
                drawRect(accent, size = Size(4.dp.toPx(), size.height))
            }
            .padding(start = 12.dp, top = 4.dp, end = 0.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteCover(
            avatar,
            Modifier.size(42.dp)
                .shadow(4.dp, CircleShape, clip = false)
                .border(2.dp, PlaybackSurface, CircleShape),
            shape = CircleShape,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    color = PlaybackText,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                level?.let { current ->
                    Spacer(Modifier.width(5.dp))
                    Image(
                        painterResource(levelBadgeResource(current)),
                        contentDescription = "LV$current",
                        modifier = Modifier.width(20.dp).height(10.dp),
                    )
                }
            }
            CreatorStats(fans, archiveCount)
        }
        Spacer(Modifier.width(8.dp))
        FollowButton(onFollowUnavailable)
    }
}

@Composable
private fun CreatorStats(fans: Long?, archiveCount: Long?) {
    if (fans == null || archiveCount == null) {
        Text(
            "哔哩哔哩 UP 主",
            color = PlaybackMuted,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${formatCompactCount(fans)} 粉丝",
            color = PlaybackMuted,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Text("·", color = Color(0xFFC9CCD0), fontSize = 12.sp, lineHeight = 12.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            "${formatCompactCount(archiveCount)} 视频",
            color = PlaybackMuted,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun FollowButton(onClick: () -> Unit) {
    val tint = PlaybackMuted
    Box(
        Modifier.height(44.dp)
            .semantics {
                contentDescription = "关注 UP 主"
                stateDescription = "当前版本暂不支持"
            }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.height(32.dp)
                .widthIn(min = 66.dp)
                .background(PlaybackDivider, CircleShape)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            Canvas(Modifier.size(14.dp)) {
                val inset = size.width * 5f / 24f
                drawLine(
                    tint,
                    Offset(center.x, inset),
                    Offset(center.x, size.height - inset),
                    1.4.dp.toPx(),
                    StrokeCap.Round,
                )
                drawLine(
                    tint,
                    Offset(inset, center.y),
                    Offset(size.width - inset, center.y),
                    1.4.dp.toPx(),
                    StrokeCap.Round,
                )
            }
            Text(
                "关注",
                color = PlaybackMuted,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private val LevelBadgeDrawables = intArrayOf(
    R.drawable.ic_bili_level_0,
    R.drawable.ic_bili_level_1,
    R.drawable.ic_bili_level_2,
    R.drawable.ic_bili_level_3,
    R.drawable.ic_bili_level_4,
    R.drawable.ic_bili_level_5,
    R.drawable.ic_bili_level_6,
    R.drawable.ic_bili_level_7,
)

private fun levelBadgeResource(level: Int): Int = LevelBadgeDrawables[level.coerceIn(0, LevelBadgeDrawables.lastIndex)]
