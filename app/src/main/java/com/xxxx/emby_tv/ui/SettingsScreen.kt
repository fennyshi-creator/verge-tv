package com.xxxx.emby_tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.data.local.PreferencesManager
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.ui.home.HomeText
import com.xxxx.emby_tv.ui.update.UpdateLookup

/** Categories that already exist in this app. No danmaku and no new engines. */
object SettingsCatalog {
    val categories = listOf("按键", "播放", "字幕", "服务器", "代理", "检查更新")

    fun allows(name: String): Boolean = name in categories
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    currentVersion: String,
    onCheckUpdate: (onResult: (UpdateLookup) -> Unit) -> Unit,
    onInstallUpdate: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    var selected by remember { mutableIntStateOf(0) }
    var doubleBack by remember { mutableStateOf(prefs.doubleBackToExit) }
    var externalMode by remember { mutableIntStateOf(prefs.externalPlayerMode) }
    var sourcePref by remember { mutableIntStateOf(prefs.sourcePreference) }
    var decoderPreference by remember { mutableIntStateOf(prefs.decoderPreference) }
    var hdrToSdr by remember { mutableStateOf(prefs.hdrToSdr) }
    var decodeToStereo by remember { mutableStateOf(prefs.decodeToStereo) }
    var aacAc3Mode by remember { mutableIntStateOf(prefs.aacAc3Mode) }
    var subtitlePad by remember { mutableStateOf(prefs.subtitleBottomPadding) }
    var proxyOn by remember { mutableStateOf(prefs.proxyEnabled) }
    var updateStatus by remember { mutableStateOf("") }
    var checkingUpdate by remember { mutableStateOf(false) }
    val server = remember { EmbyRepository.getInstance(context).serverUrl.orEmpty() }
    val focus = remember { FocusRequester() }
    val hdrOff = stringResource(R.string.hdr_sdr_off)
    val hdrOn = stringResource(R.string.hdr_sdr_on)
    val decoderAuto = stringResource(R.string.decoder_auto)
    val decoderFfmpeg = stringResource(R.string.decoder_ffmpeg)
    BackHandler(onBack = onBack)
    Row(Modifier.fillMaxSize().background(Color(0xFF16181D))) {
        Column(Modifier.fillMaxHeight().fillMaxWidth(0.36f).padding(28.dp)) {
            HomeText("设置", 28.sp, Color.White, fontWeight = FontWeight.SemiBold)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SettingsCatalog.categories.forEachIndexed { index, name ->
                    CategoryRow(name, index == 0, focus, index == selected) { selected = index }
                }
            }
        }
        Column(Modifier.fillMaxSize().padding(28.dp).verticalScroll(rememberScrollState())) {
            val title = SettingsCatalog.categories[selected]
            HomeText(title, 28.sp, Color.White, fontWeight = FontWeight.SemiBold)
            when (title) {
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
                    SettingLine(stringResource(R.string.hdr_sdr), if (hdrToSdr) hdrOn else hdrOff) {
                        hdrToSdr = !hdrToSdr
                        prefs.hdrToSdr = hdrToSdr
                    }
                    SettingLine(stringResource(R.string.decoder_preference), if (decoderPreference == 1) decoderFfmpeg else decoderAuto) {
                        decoderPreference = if (decoderPreference == 1) 0 else 1
                        prefs.decoderPreference = decoderPreference
                    }
                    SettingHint(stringResource(R.string.audio_compat_hint))
                    SettingLine(
                        "音频直通",
                        (if (decodeToStereo) "关闭" else "自动") + if (decoderPreference == 1) " · 不生效" else "",
                    ) {
                        if (decoderPreference == 1) return@SettingLine
                        decodeToStereo = !decodeToStereo
                        prefs.decodeToStereo = decodeToStereo
                    }
                    SettingHint(stringResource(R.string.audio_passthrough_hint))
                    SettingLine(
                        "AAC多声道转杜比",
                        when (aacAc3Mode) {
                            1 -> "开"
                            2 -> "关"
                            else -> "自动"
                        } + if (decoderPreference == 1) " · 不生效" else "",
                    ) {
                        if (decoderPreference == 1) return@SettingLine
                        aacAc3Mode = when (aacAc3Mode) {
                            0 -> 1
                            1 -> 2
                            else -> 0
                        }
                        prefs.aacAc3Mode = aacAc3Mode
                    }
                    SettingHint(stringResource(R.string.aac_ac3_hint))
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
                "检查更新" -> {
                    HomeText(
                        stringResource(R.string.current_version, currentVersion.ifBlank { "—" }),
                        16.sp,
                        Color.White,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                    SettingLine("检查更新", if (checkingUpdate) "正在检查" else "") {
                        if (checkingUpdate) return@SettingLine
                        checkingUpdate = true
                        updateStatus = "正在检查"
                        onCheckUpdate { result ->
                            checkingUpdate = false
                            when (result) {
                                is UpdateLookup.UpToDate -> updateStatus = "已是最新版本"
                                is UpdateLookup.Newer -> {
                                    updateStatus = "发现新版本 ${result.version}"
                                    onInstallUpdate()
                                }
                                is UpdateLookup.Failed -> updateStatus = result.message
                            }
                        }
                    }
                    if (updateStatus.isNotBlank()) {
                        HomeText(
                            updateStatus,
                            16.sp,
                            Color.White,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
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
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingHint(text: String) {
    HomeText(
        text,
        13.sp,
        Color(0xFFB5B5B5),
        modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 12.dp),
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
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        HomeText(title, 16.sp, if (focused) Color(0xFF161616) else Color.White, modifier = Modifier.weight(1f))
        if (value.isNotBlank()) {
            HomeText(value, 14.sp, if (focused) Color(0xFF4A4A4A) else Color(0xFFB5B5B5))
        }
    }
}
