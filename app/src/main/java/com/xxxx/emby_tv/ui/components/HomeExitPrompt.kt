package com.xxxx.emby_tv.ui.components

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    BackHandler(onBack = onContinue)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xFF1C1C1C), RoundedCornerShape(20.dp))
                .padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Text(
                text = stringResource(R.string.exit_app_title),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ExitChoice(
                    label = stringResource(R.string.continue_watching),
                    filled = true,
                    modifier = Modifier.focusRequester(continueFocus),
                    onClick = onContinue,
                )
                ExitChoice(
                    label = stringResource(R.string.exit_app),
                    filled = false,
                    onClick = { (context as? Activity)?.finish() },
                )
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
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = modifier,
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = if (filled) Color.White else Color.White.copy(alpha = 0.14f),
            focusedContainerColor = Color.White,
            contentColor = if (filled) Color(0xFF161616) else Color.White,
            focusedContentColor = Color(0xFF161616),
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
