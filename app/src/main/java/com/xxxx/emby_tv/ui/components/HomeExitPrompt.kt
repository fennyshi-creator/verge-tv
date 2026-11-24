package com.xxxx.emby_tv.ui.components

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.xxxx.emby_tv.R

/**
 * Home-only confirmation before leaving the app. Continue watching dismisses
 * the prompt. Exit finishes the activity. Detail and library do not use this.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeExitPrompt(onContinue: () -> Unit) {
    val context = LocalContext.current
    val continueFocus = remember { FocusRequester() }
    val exitFocus = remember { FocusRequester() }
    var slot by remember { mutableIntStateOf(0) }
    val slide by animateDpAsState(
        targetValue = if (slot == 0) 0.dp else SLOT_WIDTH + SLOT_GAP,
        animationSpec = tween(durationMillis = 180),
        label = "exit-selection",
    )
    BackHandler(onBack = onContinue)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.38f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xFF1C1C1C).copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Text(
                text = stringResource(R.string.exit_app_title),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Box {
                Box(
                    modifier = Modifier
                        .offset(x = slide)
                        .width(SLOT_WIDTH)
                        .height(SLOT_HEIGHT)
                        .background(Color.White, RoundedCornerShape(50)),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(SLOT_GAP)) {
                    ExitChoice(
                        label = stringResource(R.string.continue_watching),
                        selected = slot == 0,
                        requester = continueFocus,
                        other = exitFocus,
                        otherIsRight = true,
                        onFocus = { slot = 0 },
                        onClick = onContinue,
                    )
                    ExitChoice(
                        label = stringResource(R.string.exit_app),
                        selected = slot == 1,
                        requester = exitFocus,
                        other = continueFocus,
                        otherIsRight = false,
                        onFocus = { slot = 1 },
                        onClick = { (context as? Activity)?.finish() },
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        try {
            continueFocus.requestFocus()
        } catch (_: Exception) {
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ExitChoice(
    label: String,
    selected: Boolean,
    requester: FocusRequester,
    other: FocusRequester,
    otherIsRight: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(SLOT_WIDTH)
            .height(SLOT_HEIGHT)
            .focusRequester(requester)
            .focusProperties {
                if (otherIsRight) right = other else left = other
            }
            .onFocusChanged { if (it.isFocused) onFocus() }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF161616) else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private val SLOT_WIDTH = 148.dp
private val SLOT_HEIGHT = 44.dp
private val SLOT_GAP = 12.dp
