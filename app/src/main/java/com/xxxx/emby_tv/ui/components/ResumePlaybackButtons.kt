package com.xxxx.emby_tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.xxxx.emby_tv.R

/**
 * 继续播放 / 从头开始。不倒计时，左右键由播放页直接对应这两个动作。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ResumePlaybackButtons(
    onPlayFromStart: () -> Unit,
    onContinue: () -> Unit,
) {
    val continueButtonFocusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        Row(
            modifier = Modifier
                .padding(bottom = 80.dp, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onPlayFromStart,
                modifier = Modifier.height(48.dp),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(28.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                border = ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))),
                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                ),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.1f),
                    focusedContainerColor = Color.White.copy(alpha = 0.7f),
                    contentColor = Color.White,
                    focusedContentColor = Color.Black
                )
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.play_from_start),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Surface(
                onClick = onContinue,
                modifier = Modifier
                    .height(48.dp)
                    .focusRequester(continueButtonFocusRequester),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(28.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                border = ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))),
                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                ),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.1f),
                    focusedContainerColor = Color.White.copy(alpha = 0.9f),
                    contentColor = Color.White,
                    focusedContentColor = Color.Black
                )
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.continue_playback),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        try {
            continueButtonFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }
}
