package com.xxxx.emby_tv.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.*
import com.xxxx.emby_tv.LocalServer
import com.xxxx.emby_tv.QrCodeUtils
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.util.DiscoveredServer
import com.xxxx.emby_tv.util.EmbyServerDiscovery
import com.xxxx.emby_tv.data.remote.EmbyApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.xxxx.emby_tv.ui.connect.ConnectForm
import com.xxxx.emby_tv.ui.components.TvInputDialog
import com.xxxx.emby_tv.ui.components.DoubleBackExitHandler
import com.xxxx.emby_tv.data.local.PreferencesManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.navigation.NavController
import com.xxxx.emby_tv.ui.components.MenuDialog
import com.xxxx.emby_tv.ui.theme.ThemeColorManager
import com.xxxx.emby_tv.ui.viewmodel.LoginViewModel
import com.xxxx.emby_tv.ui.viewmodel.MainViewModel


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginScreen(
    loginViewModel: LoginViewModel,
    mainViewModel: MainViewModel,
    navController: NavController,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val themeColor = ThemeColorManager.getThemeColorById(context, mainViewModel.currentThemeId)

    // 按两次返回退出APP（可在 按键设置 中开启）；未开启时保持默认的单次返回退出
    val doubleBackToExit = PreferencesManager(context).doubleBackToExit

    // 解析 savedServerUrl 的辅助函数
    fun parseServerUrl(url: String?): Triple<String, String, String> = ConnectForm.parseServerUrl(url)

    // 在初始化时就解析 savedServerUrl
    val initialParsed = remember { parseServerUrl(loginViewModel.savedServerUrl) }

    // 从 ViewModel 获取保存的值
    var protocol by remember { mutableStateOf(initialParsed.first) }
    var host by remember { mutableStateOf(initialParsed.second) }
    var port by remember { mutableStateOf(initialParsed.third) }
    var username by remember { mutableStateOf(loginViewModel.savedUsername) }
    var password by remember { mutableStateOf(loginViewModel.savedPassword) }
    var passwordVisible by remember { mutableStateOf(false) }

    // Server & QR Code State
    var qrCodeBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var localServerAddress by remember { mutableStateOf("") }
    var localServer by remember { mutableStateOf<LocalServer?>(null) }

    // Focus Requesters
    val hostFocusRequester = remember { FocusRequester() }
    val loginButtonFocusRequester = remember { FocusRequester() }

    // 局域网服务器自动发现
    var discoveredServers by remember { mutableStateOf<List<DiscoveredServer>>(emptyList()) }
    var showServerPicker by remember { mutableStateOf(false) }
    // 服务器Id -> 唯一用户名（该服务器只有一个用户时预填）
    var serverUserHints by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // 进入登录页时扫描局域网 Emby 服务器；
    // 首次登录（未保存过服务器地址）时弹窗让用户选择（即使只有 1 台），
    // 已有保存地址时不弹窗打扰，但结果仍会提供给扫码网页显示
    LaunchedEffect(Unit) {
        val servers = EmbyServerDiscovery.discover(context)
        discoveredServers = servers
        if (servers.isNotEmpty() && host.isEmpty()) {
            showServerPicker = true
        }
        // 并发查询各服务器的公开用户：仅一个用户时记录用户名用于预填
        servers.map { server ->
            async(Dispatchers.IO) {
                val name = fetchSingleUserName(context, server.address)
                server.id to name
            }
        }.awaitAll().forEach { (id, name) ->
            if (name != null) {
                serverUserHints = serverUserHints + (id to name)
            }
        }
    }

    fun applyDiscoveredServer(server: DiscoveredServer) {
        val (p, h, pt) = parseServerUrl(server.address)
        protocol = p
        host = h
        port = pt
        // 服务器只有一个用户时自动填入用户名（不覆盖已输入的内容）
        serverUserHints[server.id]?.let { name ->
            if (username.isEmpty()) username = name
        }
        showServerPicker = false
        // 地址已填好，聚焦到登录按钮方便直接登录
        loginButtonFocusRequester.requestFocus()
    }

    // Initial Focus Logic - 在 host 已经被正确初始化后执行
    LaunchedEffect(Unit) {
        // 给 UI 一点时间完成组合
        kotlinx.coroutines.delay(100)
        if (host.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()) {
            loginButtonFocusRequester.requestFocus()
        } else {
            hostFocusRequester.requestFocus()
        }
    }

    // Dialog states
    var showHostDialog by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }
    var showUsernameDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    fun buildServerUrl(p: String, h: String, pt: String): String {
        return if (h.isNotEmpty()) "$p://$h:$pt" else ""
    }

    // 监听 savedServerUrl 变化（用于外部改变时更新）
    LaunchedEffect(loginViewModel.savedServerUrl) {
        if (!loginViewModel.savedServerUrl.isNullOrEmpty()) {
            val (p, h, pt) = parseServerUrl(loginViewModel.savedServerUrl)
            protocol = p
            host = h
            port = pt
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    val failText = stringResource(id = R.string.login_failed)

    fun onLoginClick(
        p: String = protocol,
        h: String = host,
        pt: String = port,
        user: String = username,
        pass: String = password,
    ) {
        val builtServerUrl = buildServerUrl(p, h, pt)

        loginViewModel.login(
            serverUrl = builtServerUrl,
            username = user,
            password = pass,
            onSuccess = {
                localServer?.stop()
            },
            onError = { error ->
                scope.launch {
                    android.widget.Toast.makeText(
                        context.applicationContext,
                        error,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // 检查更新
    LaunchedEffect(Unit) {
        mainViewModel.checkUpdate()
    }

    LaunchedEffect(themeColor) {
        withContext(Dispatchers.IO) {
            val server = LocalServer.startServer(
                themeColor.primaryDark,
                themeColor.secondaryLight,
                serversProvider = { discoveredServers },
                serverUserHintsProvider = { serverUserHints }
            ) { p, h, pt, user, pass ->
                scope.launch {
                    protocol = p
                    host = h
                    port = pt
                    username = user
                    password = pass
                    if (ConnectForm.shouldAutoLogin(h, user)) {
                        onLoginClick(p, h, pt, user, pass)
                    } else {
                        loginButtonFocusRequester.requestFocus()
                    }
                }
            }
            localServer = server

            if (server != null) {
                val ip = QrCodeUtils.getLocalIpAddress()
                if (ip != null) {
                    val address = "http://$ip:${server.listeningPort}"
                    localServerAddress = address
                    val bitmap = QrCodeUtils.generateQrCode(address, 400)
                    qrCodeBitmap = bitmap?.asImageBitmap()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            localServer?.stop()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(ConnectForm.PAGE))) {
        Box(
            Modifier
                .size(520.dp)
                .align(Alignment.TopStart)
                .offset(x = (-80).dp, y = (-60).dp)
                .background(Brush.radialGradient(listOf(Color(0x66306E86), Color.Transparent))),
        )
        Box(
            Modifier
                .size(560.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 80.dp, y = 80.dp)
                .background(Brush.radialGradient(listOf(Color(0x55A86A38), Color.Transparent))),
        )
        Box(
            Modifier
                .size(420.dp)
                .align(Alignment.CenterEnd)
                .offset(x = 40.dp)
                .background(Brush.radialGradient(listOf(Color(0x332E6A62), Color.Transparent))),
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 40.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xD01C2C34), Color(0xE8141612)),
                    ),
                    RoundedCornerShape(32.dp),
                )
                .padding(1.dp),
        ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(ConnectForm.LEFT_FRACTION).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (qrCodeBitmap != null) {
                    Box(
                        Modifier
                            .size(248.dp)
                            .background(Color.White, RoundedCornerShape(28.dp))
                            .padding(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            bitmap = qrCodeBitmap!!,
                            contentDescription = stringResource(R.string.scan_qr_hint),
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            Modifier
                                .size(42.dp)
                                .background(Color(0xFF52B54B), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(22.dp))
                    Text(
                        text = stringResource(R.string.scan_qr_hint),
                        color = Color.White.copy(alpha = 0.94f),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (discoveredServers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "已发现局域网服务器，选中后会填入右侧",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.8f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight(0.76f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.White.copy(0.18f), Color.Transparent),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .weight(1f - ConnectForm.LEFT_FRACTION)
                    .fillMaxHeight()
                    .padding(horizontal = 28.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    ConnectForm.TV_LOGIN_TITLE,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "服务器地址",
                    color = Color.White.copy(0.62f),
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProtocolButton(
                        protocol = protocol,
                        onClick = {
                            val next = if (protocol == "http") "https" else "http"
                            port = ConnectForm.portFor(next, port)
                            protocol = next
                        },
                        modifier = Modifier.width(96.dp).height(48.dp),
                    )
                    TvInputButton(
                        value = host,
                        label = stringResource(R.string.host),
                        onClick = { showHostDialog = true },
                        showMenu = { showMenu = true },
                        widthFraction = 1f,
                        fieldHeight = 48.dp,
                        modifier = Modifier.weight(1f).focusRequester(hostFocusRequester),
                    )
                    TvInputButton(
                        value = port,
                        label = stringResource(R.string.port),
                        onClick = { showPortDialog = true },
                        showMenu = { showMenu = true },
                        widthFraction = 1f,
                        fieldHeight = 48.dp,
                        modifier = Modifier.width(96.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Username Input
                TvInputButton(
                    value = username,
                    label = stringResource(R.string.username),
                    showMenu = { showMenu = true },
                    widthFraction = 1f,
                    fieldHeight = 48.dp,
                    onClick = { showUsernameDialog = true }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Password Input
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TvInputButton(
                        value = password,
                        label = stringResource(R.string.password),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        showMenu = { showMenu = true },
                        widthFraction = 1f,
                        fieldHeight = 48.dp,
                        modifier = Modifier.weight(1f),
                        onClick = { showPasswordDialog = true },
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = { passwordVisible = !passwordVisible },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0x760F131C),
                            contentColor = Color(0xFFF4F1EA),
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color(ConnectForm.INK),
                        ),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = null,
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                if (ConnectForm.loginButtonShown()) Button(
                    onClick = { onLoginClick() },
                    enabled = !loginViewModel.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        //兼容移动端点击  TODO：移除
                        //  .clickable(true, onClick = { onLoginClick() })
                        .focusRequester(loginButtonFocusRequester)
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.Menu -> {
                                        showMenu = true
                                        true
                                    }

                                    Key.Bookmark -> {
                                        showMenu = true
                                        true
                                    }

                                    else -> false
                                }
                            } else false
                        },
                    colors = ButtonDefaults.colors(
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color(ConnectForm.INK),
                        containerColor = Color(0xFFF4F1EA),
                        contentColor = Color(ConnectForm.INK),
                    ),
                    shape = ButtonDefaults.shape()
                ) {
                    if (loginViewModel.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.logging_in),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.login),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    onClick = { navController.navigate("proxy_settings") },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(100)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White.copy(alpha = 0.55f),
                        focusedContentColor = Color(0xFFF4F1EA),
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.proxy_settings),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    DoubleBackExitHandler(doubleBackToExit)
    }

    if (showMenu) {
        MenuDialog(
            needUpdate = mainViewModel.needUpdate,
            onDismiss = { showMenu = false },
            onLogout = {
                loginViewModel.logout()
                showMenu = false
            },
            onUpdate = {
                mainViewModel.checkUpdate()
                showMenu = false
                navController.navigate("update")
            },
            onThemeChange = { themeColor ->
                mainViewModel.saveThemeId(themeColor.id)
            },
            onProxySettings = {
                showMenu = false
                navController.navigate("proxy_settings")
            },
            onKeySettings = {
                showMenu = false
                navController.navigate("key_settings")
            },
            isShowLogout = false
        )
    }

    if (showServerPicker) {
        ServerPickerDialog(
            servers = discoveredServers,
            onDismiss = { showServerPicker = false },
            onSelect = { applyDiscoveredServer(it) }
        )
    }

    // Dialogs
    if (showHostDialog) {
        TvInputDialog(
            title = stringResource(R.string.host),
            initialValue = host,
            onConfirm = {
                host = it
                showHostDialog = false
            },
            onDismiss = { showHostDialog = false }
        )
    }

    if (showPortDialog) {
        TvInputDialog(
            title = stringResource(R.string.port),
            initialValue = port,
            onConfirm = {
                port = it.filter { c -> c.isDigit() }
                showPortDialog = false
            },
            onDismiss = { showPortDialog = false },
            isNumber = true
        )
    }

    if (showUsernameDialog) {
        TvInputDialog(
            title = stringResource(R.string.username),
            initialValue = username,
            onConfirm = {
                username = it
                showUsernameDialog = false
            },
            onDismiss = { showUsernameDialog = false }
        )
    }

    if (showPasswordDialog) {
        TvInputDialog(
            title = stringResource(R.string.password),
            initialValue = password,
            onConfirm = {
                password = it
                showPasswordDialog = false
            },
            onDismiss = { showPasswordDialog = false }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvInputButton(
    value: String,
    label: String,
    showMenu: () -> Unit,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    widthFraction: Float = 0.8f,
    fieldHeight: androidx.compose.ui.unit.Dp = 64.dp,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))),
            focusedBorder = Border(BorderStroke(1.5.dp, Color.White)),
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x760F131C),
            contentColor = Color(0xFFF4F1EA),
            focusedContainerColor = Color(0xC81A2130),
            focusedContentColor = Color(0xFFF4F1EA),
        ),
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(fieldHeight)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Menu -> {
                            showMenu()
                            true
                        }

                        else -> false
                    }
                } else false
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
            val transformedText = visualTransformation.filter(AnnotatedString(value)).text.text
            Text(
                text = if (value.isEmpty()) " " else transformedText,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProtocolChoice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White else Color.Transparent,
            contentColor = if (selected) Color(ConnectForm.INK) else Color.White.copy(0.7f),
            focusedContainerColor = if (selected) Color.White else Color.White.copy(0.16f),
            focusedContentColor = if (selected) Color(ConnectForm.INK) else Color.White,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ProtocolButton(
    protocol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(14.dp)),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))),
            focusedBorder = Border(BorderStroke(1.5.dp, Color.White)),
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xEBFFFFFF),
            contentColor = Color(ConnectForm.INK),
            focusedContainerColor = Color.White,
            focusedContentColor = Color(ConnectForm.INK),
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = protocol.uppercase(),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
/**
 * 局域网发现的服务器选择弹窗：
 * 显示扫描到的 Emby 服务器（名称 + 地址），用户点击后自动填入登录表单。
 * 扫到 1 台也弹窗，由用户决定是否使用
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ServerPickerDialog(
    servers: List<DiscoveredServer>,
    onDismiss: () -> Unit,
    onSelect: (DiscoveredServer) -> Unit
) {
    val firstFocusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(500.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))),
            colors = SurfaceDefaults.colors(
                containerColor = Color(0xFF161616).copy(alpha = 0.95f),
                contentColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp)
            ) {
                Text(
                    text = stringResource(R.string.discovered_servers_title),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.discovered_servers_hint),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.6f)
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                servers.forEachIndexed { index, server ->
                    Surface(
                        onClick = { onSelect(server) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .then(
                                if (index == 0) Modifier.focusRequester(firstFocusRequester)
                                else Modifier
                            ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))),
                            focusedBorder = Border(BorderStroke(2.dp, Color.White))
                        ),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.05f),
                            focusedContainerColor = Color.White.copy(alpha = 0.95f),
                            contentColor = Color.White,
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = server.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = server.address,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = LocalContentColor.current.copy(alpha = 0.7f)
                                ),
                                maxLines = 1
                            )
                        }
                    }
                    if (index < servers.size - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        try {
            firstFocusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }
}

/**
 * 查询目标服务器的公开用户（GET /Users/Public，无需认证），
 * 仅当服务器只有一个用户时返回该用户名，否则返回 null
 */
private suspend fun fetchSingleUserName(context: Context, serverAddress: String): String? {
    return try {
        val deviceId = com.xxxx.emby_tv.data.repository.EmbyRepository.getInstance(context).deviceId
        val users = EmbyApi.getPublicUsers(context, serverAddress, deviceId)
        val names = users.mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }
        names.singleOrNull()
    } catch (_: Exception) {
        null
    }
}
