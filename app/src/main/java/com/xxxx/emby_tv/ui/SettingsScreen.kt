package com.xxxx.emby_tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xxxx.emby_tv.data.local.PreferencesManager
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.ui.home.HomeText
import com.xxxx.emby_tv.ui.theme.ThemeColorManager

/** Categories that already exist in this app. No danmaku and no new engines. */
object SettingsCatalog {
    val categories = listOf("界面与显示", "按键", "播放", "字幕", "服务器", "代理")

    fun allows(name: String): Boolean = name in categories
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val themes = ThemeColorManager.getThemeColors(context)
    var selected by remember { mutableIntStateOf(0) }
    var themeId by remember { mutableStateOf(prefs.themeId) }
    var doubleBack by remember { mutableStateOf(prefs.doubleBackToExit) }
    var externalMode by remember { mutableIntStateOf(prefs.externalPlayerMode) }
    var sourcePref by remember { mutableIntStateOf(prefs.sourcePreference) }
    var subtitlePad by remember { mutableStateOf(prefs.subtitleBottomPadding) }
    var proxyOn by remember { mutableStateOf(prefs.proxyEnabled) }
    val server = remember { EmbyRepository.getInstance(context).serverUrl.orEmpty() }
    val focus = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    Row(Modifier.fillMaxSize().background(Color(0xFF16181D))) {
        Column(Modifier.fillMaxHeight().fillMaxWidth(0.36f).padding(28.dp)) {
            HomeText("设置", 28.sp, Color.White, fontWeight = FontWeight.SemiBold)
            SettingsCatalog.categories.forEachIndexed { index, name ->
                CategoryRow(name, index == 0, focus, index == selected) { selected = index }
            }
        }
        Column(Modifier.fillMaxSize().padding(28.dp)) {
            val title = SettingsCatalog.categories[selected]
            HomeText(title, 28.sp, Color.White, fontWeight = FontWeight.SemiBold)
            when (title) {
                "界面与显示" -> themes.forEach { theme ->
                    val on = theme.id == themeId
                    SettingLine(theme.name, if (on) "当前" else "") {
                        themeId = theme.id
                        prefs.themeId = theme.id
                    }
                }
                "按键" -> SettingLine("连按两次返回退出", if (doubleBack) "开" else "关") {
                    doubleBack = !doubleBack
                    prefs.doubleBackToExit = doubleBack
                }
                "播放" -> {
                    val modes = listOf("始终内置播放器", "总是外部播放器", "仅杜比视界用外部播放器")
                    SettingLine("外部播放器", modes[externalMode.coerceIn(0, modes.lastIndex)]) {
                        externalMode = (externalMode + 1) % modes.size
                        prefs.externalPlayerMode = externalMode
                    }
                    val sources = listOf("杜比视界优先", "HDR优先", "流畅优先", "默认排序")
                    SettingLine("多版本优先", sources[sourcePref.coerceIn(0, sources.lastIndex)]) {
                        sourcePref = (sourcePref + 1) % sources.size
                        prefs.sourcePreference = sourcePref
                    }
                }
                "字幕" -> SettingLine("字幕底部边距", subtitlePad.toInt().toString()) {
                    subtitlePad = if (subtitlePad >= 80f) 16f else subtitlePad + 8f
                    prefs.subtitleBottomPadding = subtitlePad
                }
                "服务器" -> HomeText(
                    if (server.isBlank()) "未登录" else server,
                    16.sp,
                    Color.White,
                    modifier = Modifier.padding(top = 18.dp),
                )
                "代理" -> SettingLine("使用代理", if (proxyOn) "开" else "关") {
                    proxyOn = !proxyOn
                    prefs.proxyEnabled = proxyOn
                }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }
}

@Composable
private fun CategoryRow(
    name: String,
    first: Boolean,
    focus: FocusRequester,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    HomeText(
        name,
        18.sp,
        if (focused) Color(0xFF161616) else Color.White,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color(0xFFE6E6E6) else if (selected) Color(0xFF2A2A2A) else Color.Transparent)
            .then(if (first) Modifier.focusRequester(focus) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onSelect()
            }
            .focusable()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingLine(title: String, value: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color(0xFFE6E6E6) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        HomeText(title, 16.sp, if (focused) Color(0xFF161616) else Color.White, modifier = Modifier.weight(1f))
        if (value.isNotBlank()) {
            HomeText(value, 14.sp, if (focused) Color(0xFF4A4A4A) else Color(0xFFB5B5B5))
        }
    }
}
