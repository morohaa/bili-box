package io.github.bilibox.feature.player

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.bilibox.R

@Composable
internal fun PlayerBookmarkButton(onClick: () -> Unit, adding: Boolean = false) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            painter = painterResource(R.drawable.ic_player_bookmark),
            contentDescription = if (adding) "添加标记" else "打开标记列表",
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}
